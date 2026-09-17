package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.repository.PaperTradingOrderRepository;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
import com.swingtrade.broker.util.OptimisticLockRetryHelper;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.service.PaperPortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements the plan §7.2 per-variant paper portfolio bookkeeping described on
 * {@link PaperPortfolioService}. See that interface's javadoc for the scope boundary against
 * {@link PaperTradingStateService} (which still only drives one live, order-executing engine
 * for {@code portfolio_id = "default"}).
 */
@Service
public class PaperPortfolioServiceImpl implements PaperPortfolioService {

    private static final Logger logger = LoggerFactory.getLogger(PaperPortfolioServiceImpl.class);

    static final String DEFAULT_PORTFOLIO_ID = "default";
    private static final BigDecimal DEFAULT_DAILY_LOSS_THRESHOLD_PCT = new BigDecimal("0.04");
    // Mirrors PaperTradingEngine.calculatePositionSize's 1%-risk-per-trade sizing and its
    // 20%-of-capital capacity guard, applied against a SHADOW portfolio's own capital instead of
    // the shared engine's, so shadow sizing behaviour matches the champion's as closely as
    // possible without sharing any mutable state.
    private static final BigDecimal RISK_PER_TRADE_PCT =
        BigDecimal.ONE.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    private static final BigDecimal MAX_CAPITAL_PER_POSITION_PCT =
        BigDecimal.valueOf(20).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    private static final BigDecimal MIN_QUANTITY = BigDecimal.valueOf(100);
    private static final BigDecimal COMMISSION_RATE = BigDecimal.valueOf(0.05);

    private final PaperTradingPortfolioRepository portfolioRepo;
    private final PaperTradingSnapshotRepository snapshotRepo;
    private final PaperTradingStateService defaultPortfolioStateService;
    private final PaperTradingOrderRepository orderRepo;
    private final AtomicLong shadowOrderCounter = new AtomicLong(0);

    public PaperPortfolioServiceImpl(PaperTradingPortfolioRepository portfolioRepo,
                                      PaperTradingSnapshotRepository snapshotRepo,
                                      PaperTradingStateService defaultPortfolioStateService,
                                      PaperTradingOrderRepository orderRepo) {
        this.portfolioRepo = portfolioRepo;
        this.snapshotRepo = snapshotRepo;
        this.defaultPortfolioStateService = defaultPortfolioStateService;
        this.orderRepo = orderRepo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensurePortfolio(String portfolioId, BigDecimal paperCapital) {
        if (portfolioId == null || portfolioId.isBlank()) {
            throw new IllegalArgumentException("portfolioId must not be blank");
        }
        BigDecimal capital = paperCapital == null ? new BigDecimal("500000") : paperCapital;
        try {
            OptimisticLockRetryHelper.execute(() -> {
                if (portfolioRepo.findByPortfolioId(portfolioId).isPresent()) {
                    logger.debug("Portfolio {} already exists, skipping creation", portfolioId);
                    return;
                }
                PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
                entity.setId(portfolioRepo.findMaxId() + 1);
                entity.setPortfolioId(portfolioId);
                entity.setInitialCapital(capital);
                entity.setCurrentCapital(capital);
                entity.setTotalRealizedPnl(BigDecimal.ZERO);
                entity.setTotalUnrealizedPnL(BigDecimal.ZERO);
                entity.setOpenPositionCount(0);
                entity.setDailyLossThresholdPct(DEFAULT_DAILY_LOSS_THRESHOLD_PCT);
                portfolioRepo.save(entity);
                logger.info("Created paper trading portfolio {} with capital {}", portfolioId, capital);
            }, "PaperTradingPortfolioEntity:" + portfolioId);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to create portfolio " + portfolioId, e);
        }
    }

    @Override
    public boolean isDailyLossBreached(String portfolioId) {
        PaperTradingPortfolioEntity entity = portfolioRepo.findByPortfolioId(portfolioId).orElse(null);
        if (entity == null) {
            // No portfolio yet - nothing to protect; caller's routing logic decides whether a
            // signal for this portfolioId should even be considered.
            return false;
        }
        BigDecimal currentEquity = nz(entity.getCurrentCapital()).add(nz(entity.getTotalUnrealizedPnL()));
        BigDecimal baseline = todaysOpeningBaseline(portfolioId, entity);
        BigDecimal loss = baseline.subtract(currentEquity);
        if (loss.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal thresholdPct = entity.getDailyLossThresholdPct() != null
            ? entity.getDailyLossThresholdPct() : DEFAULT_DAILY_LOSS_THRESHOLD_PCT;
        BigDecimal thresholdAmount = nz(entity.getInitialCapital()).multiply(thresholdPct);
        boolean breached = loss.compareTo(thresholdAmount) > 0;
        if (breached) {
            logger.warn("Daily loss breaker tripped for portfolio {}: loss={} threshold={} (baseline={}, current={})",
                portfolioId, loss, thresholdAmount, baseline, currentEquity);
        }
        return breached;
    }

    private BigDecimal todaysOpeningBaseline(String portfolioId, PaperTradingPortfolioEntity entity) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return snapshotRepo.findFirstByPortfolioIdAndSnapshotTimeBeforeOrderBySnapshotTimeDesc(portfolioId, startOfToday)
            .map(PaperTradingSnapshotEntity::getTotalValue)
            .orElseGet(() -> nz(entity.getInitialCapital()));
    }

    @Override
    public void snapshotAllPortfolios() {
        List<PaperTradingPortfolioEntity> portfolios = portfolioRepo.findAllByOrderByPortfolioIdAsc();
        for (PaperTradingPortfolioEntity portfolio : portfolios) {
            try {
                if (DEFAULT_PORTFOLIO_ID.equals(portfolio.getPortfolioId())) {
                    // The only portfolio with a live, order-executing engine today - reuse its
                    // existing engine-driven snapshot logic (includes real cash/market value).
                    defaultPortfolioStateService.saveSnapshot();
                } else {
                    snapshotShadowPortfolio(portfolio);
                }
            } catch (Exception e) {
                logger.warn("Failed to snapshot portfolio {}: {}", portfolio.getPortfolioId(), e.getMessage());
            }
        }
    }

    // Shadow variants have no live execution engine yet (see PaperPortfolioService's javadoc),
    // so their "mark-to-market" today is just their persisted portfolio row's own state: no
    // open positions are simulated for them, so total value == current capital.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void snapshotShadowPortfolio(PaperTradingPortfolioEntity portfolio) {
        try {
            OptimisticLockRetryHelper.execute(() -> {
                BigDecimal totalValue = nz(portfolio.getCurrentCapital()).add(nz(portfolio.getTotalUnrealizedPnL()));
                PaperTradingSnapshotEntity snapshot = new PaperTradingSnapshotEntity();
                snapshot.setPortfolioId(portfolio.getPortfolioId());
                snapshot.setTotalValue(totalValue);
                snapshot.setCashBalance(nz(portfolio.getCurrentCapital()));
                snapshot.setMarketValue(nz(portfolio.getTotalUnrealizedPnL()));
                snapshot.setTotalPnL(nz(portfolio.getTotalRealizedPnl()).add(nz(portfolio.getTotalUnrealizedPnL())));
                BigDecimal initial = nz(portfolio.getInitialCapital());
                snapshot.setReturnPct(initial.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : totalValue.subtract(initial).divide(initial, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
                snapshot.setOpenPositions(portfolio.getOpenPositionCount());
                snapshotRepo.save(snapshot);
            }, "PaperTradingSnapshotEntity:" + portfolio.getPortfolioId());
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to snapshot portfolio " + portfolio.getPortfolioId(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean executeVariantBuy(String portfolioId, Signal signal, BigDecimal referencePrice) {
        if (portfolioId == null || signal == null || referencePrice == null
                || referencePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        try {
            return OptimisticLockRetryHelper.execute(() -> {
                PaperTradingPortfolioEntity entity = portfolioRepo.findByPortfolioId(portfolioId).orElse(null);
                if (entity == null) {
                    logger.warn("Cannot execute variant BUY for {}: no portfolio row for {} - "
                        + "ensurePortfolio() must run first", signal.symbol(), portfolioId);
                    return false;
                }

                // Dedupe: a signal already tagged onto a persisted order for this portfolio must
                // not be executed twice (e.g. a retry after a partial failure elsewhere).
                if (signal.id() != null) {
                    String signalId = signal.id().toString();
                    boolean alreadyExecuted = orderRepo.findByPortfolioIdOrderByCreatedAtDesc(portfolioId).stream()
                        .anyMatch(o -> signalId.equals(o.getSignalId()));
                    if (alreadyExecuted) {
                        logger.debug("Signal {} already executed for portfolio {}, skipping", signalId, portfolioId);
                        return true;
                    }
                }

                BigDecimal currentCapital = nz(entity.getCurrentCapital());
                if (currentCapital.compareTo(BigDecimal.ZERO) <= 0) {
                    logger.warn("Cannot open position for portfolio {}: capital depleted ({})",
                        portfolioId, currentCapital);
                    return false;
                }

                BigDecimal quantity = calculateQuantity(currentCapital, referencePrice, signal.stopLoss());
                if (quantity == null || quantity.signum() <= 0) {
                    return false;
                }
                BigDecimal positionValue = referencePrice.multiply(quantity);
                BigDecimal capitalRatio = positionValue.divide(currentCapital, 4, RoundingMode.HALF_UP);
                if (capitalRatio.compareTo(MAX_CAPITAL_PER_POSITION_PCT) > 0) {
                    logger.warn("Cannot open position for portfolio {} on {}: position value {} exceeds "
                        + "20% of capital {}", portfolioId, signal.symbol(), positionValue, currentCapital);
                    return false;
                }

                BigDecimal commission = quantity.multiply(COMMISSION_RATE);
                BigDecimal totalCost = positionValue.add(commission);
                if (totalCost.compareTo(currentCapital) > 0) {
                    logger.warn("Cannot open position for portfolio {} on {}: total cost {} exceeds "
                        + "available capital {}", portfolioId, signal.symbol(), totalCost, currentCapital);
                    return false;
                }

                entity.setCurrentCapital(currentCapital.subtract(totalCost));
                entity.setOpenPositionCount(entity.getOpenPositionCount() + 1);
                portfolioRepo.save(entity);

                Order order = new Order(generateShadowOrderId(portfolioId), signal.symbol(), OrderType.MARKET,
                    TradeDirection.LONG, quantity, referencePrice, null, null);
                order.setStatus(OrderStatus.FILLED);
                order.setExecutionTime(LocalDateTime.now());
                order.setCommission(commission);
                order.setAdditionalProperties(signal.id() != null
                    ? java.util.Map.of("signalId", signal.id().toString()) : java.util.Map.of());
                PaperTradingOrderEntity orderEntity = new PaperTradingOrderEntity(order, portfolioId);
                orderRepo.save(orderEntity);

                logger.info("Executed variant BUY for portfolio {}: {} shares of {} at {} (commission {})",
                    portfolioId, quantity, signal.symbol(), referencePrice, commission);
                return true;
            }, "PaperTradingPortfolioEntity:executeVariantBuy:" + portfolioId);
        } catch (RuntimeException e) {
            logger.warn("Failed to execute variant BUY for portfolio {} on {}: {}",
                portfolioId, signal.symbol(), e.getMessage(), e);
            return false;
        }
    }

    private BigDecimal calculateQuantity(BigDecimal currentCapital, BigDecimal entryPrice, BigDecimal stopLoss) {
        if (stopLoss == null) {
            return MIN_QUANTITY;
        }
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss);
        if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return MIN_QUANTITY;
        }
        BigDecimal maxRiskAmount = currentCapital.multiply(RISK_PER_TRADE_PCT);
        BigDecimal quantity = maxRiskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);
        return quantity.max(MIN_QUANTITY);
    }

    private String generateShadowOrderId(String portfolioId) {
        return "SHADOW_" + portfolioId + "_" + shadowOrderCounter.incrementAndGet()
            + "_" + System.nanoTime();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
