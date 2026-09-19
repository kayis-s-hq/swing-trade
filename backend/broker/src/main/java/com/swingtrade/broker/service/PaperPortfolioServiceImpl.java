package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.entity.ShadowPositionEntity;
import com.swingtrade.broker.repository.PaperTradingOrderRepository;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
import com.swingtrade.broker.repository.ShadowPositionRepository;
import com.swingtrade.broker.util.OptimisticLockRetryHelper;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.ShadowPositionSnapshot;
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
import java.util.Optional;
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
    private final ShadowPositionRepository shadowPositionRepo;
    private final AtomicLong shadowOrderCounter = new AtomicLong(0);

    public PaperPortfolioServiceImpl(PaperTradingPortfolioRepository portfolioRepo,
                                      PaperTradingSnapshotRepository snapshotRepo,
                                      PaperTradingStateService defaultPortfolioStateService,
                                      PaperTradingOrderRepository orderRepo,
                                      ShadowPositionRepository shadowPositionRepo) {
        this.portfolioRepo = portfolioRepo;
        this.snapshotRepo = snapshotRepo;
        this.defaultPortfolioStateService = defaultPortfolioStateService;
        this.orderRepo = orderRepo;
        this.shadowPositionRepo = shadowPositionRepo;
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

                // Persist open-position state (plan §7.4 gap-fill) so a later run can evaluate an
                // exit for it: entry price/stop/target/high-water-mark, keyed by
                // (portfolioId, symbol) - at most one OPEN row is expected per pair.
                ShadowPositionEntity position = new ShadowPositionEntity();
                position.setPortfolioId(portfolioId);
                position.setSymbol(signal.symbol());
                position.setEntryDate(signal.date() != null ? signal.date() : LocalDate.now());
                position.setEntryPrice(referencePrice);
                position.setStopLoss(signal.stopLoss());
                position.setTarget(signal.target());
                position.setQuantity(quantity.intValue());
                position.setHighWaterMark(referencePrice);
                position.setStatus(ShadowPositionEntity.STATUS_OPEN);
                position.setCreatedAt(LocalDateTime.now());
                position.setUpdatedAt(LocalDateTime.now());
                shadowPositionRepo.save(position);

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

    @Override
    public Optional<ShadowPositionSnapshot> findOpenShadowPosition(String portfolioId, String symbol) {
        if (portfolioId == null || symbol == null) {
            return Optional.empty();
        }
        return shadowPositionRepo.findByPortfolioIdAndSymbolAndStatus(
                portfolioId, symbol, ShadowPositionEntity.STATUS_OPEN)
            .map(e -> new ShadowPositionSnapshot(e.getPortfolioId(), e.getSymbol(), e.getEntryDate(),
                e.getEntryPrice(), e.getStopLoss(), e.getTarget(), e.getQuantity(), e.getHighWaterMark()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advanceShadowPositionHighWaterMark(String portfolioId, String symbol, BigDecimal candidateClose) {
        if (portfolioId == null || symbol == null || candidateClose == null) {
            return;
        }
        try {
            OptimisticLockRetryHelper.execute(() -> {
                ShadowPositionEntity position = shadowPositionRepo
                    .findByPortfolioIdAndSymbolAndStatus(portfolioId, symbol, ShadowPositionEntity.STATUS_OPEN)
                    .orElse(null);
                if (position == null) {
                    return;
                }
                BigDecimal current = nz(position.getHighWaterMark());
                if (candidateClose.compareTo(current) > 0) {
                    position.setHighWaterMark(candidateClose);
                    position.setUpdatedAt(LocalDateTime.now());
                    shadowPositionRepo.save(position);
                }
            }, "ShadowPositionEntity:hwm:" + portfolioId + ":" + symbol);
        } catch (RuntimeException e) {
            logger.warn("Failed to advance high-water-mark for portfolio {} on {}: {}",
                portfolioId, symbol, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean executeVariantExit(String portfolioId, String symbol, BigDecimal exitPrice, String exitReason) {
        if (portfolioId == null || symbol == null || exitPrice == null
                || exitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        try {
            return OptimisticLockRetryHelper.execute(() -> {
                // Idempotent by construction: once closed, this lookup for STATUS_OPEN finds
                // nothing on a retried/duplicate run within the same day, so no double-exit.
                ShadowPositionEntity position = shadowPositionRepo
                    .findByPortfolioIdAndSymbolAndStatus(portfolioId, symbol, ShadowPositionEntity.STATUS_OPEN)
                    .orElse(null);
                if (position == null) {
                    logger.debug("No open shadow position for portfolio {} on {}, nothing to exit",
                        portfolioId, symbol);
                    return false;
                }

                PaperTradingPortfolioEntity portfolio = portfolioRepo.findByPortfolioId(portfolioId).orElse(null);
                if (portfolio == null) {
                    logger.warn("Cannot execute variant exit for {}: no portfolio row for {}",
                        symbol, portfolioId);
                    return false;
                }

                BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
                BigDecimal proceeds = exitPrice.multiply(quantity);
                BigDecimal commission = quantity.multiply(COMMISSION_RATE);
                BigDecimal netProceeds = proceeds.subtract(commission);
                BigDecimal entryValue = nz(position.getEntryPrice()).multiply(quantity);
                BigDecimal pnl = netProceeds.subtract(entryValue);

                portfolio.setCurrentCapital(nz(portfolio.getCurrentCapital()).add(netProceeds));
                portfolio.setTotalRealizedPnl(nz(portfolio.getTotalRealizedPnl()).add(pnl));
                portfolio.setOpenPositionCount(Math.max(0, portfolio.getOpenPositionCount() - 1));
                portfolioRepo.save(portfolio);

                position.setStatus(ShadowPositionEntity.STATUS_CLOSED);
                position.setExitDate(LocalDate.now());
                position.setExitPrice(exitPrice);
                position.setExitReason(exitReason);
                position.setPnl(pnl);
                position.setUpdatedAt(LocalDateTime.now());
                shadowPositionRepo.save(position);

                PaperTradingOrderEntity orderEntity = new PaperTradingOrderEntity();
                orderEntity.setOrderId(generateShadowOrderId(portfolioId));
                orderEntity.setSymbol(symbol);
                orderEntity.setType(OrderType.MARKET.name());
                orderEntity.setDirection("SELL");
                orderEntity.setQuantity(position.getQuantity());
                orderEntity.setPrice(exitPrice);
                orderEntity.setStatus(OrderStatus.FILLED.name());
                orderEntity.setCommission(commission);
                orderEntity.setExecutedAt(LocalDateTime.now());
                orderEntity.setCreatedAt(LocalDateTime.now());
                orderEntity.setUpdatedAt(LocalDateTime.now());
                orderEntity.setSignalId(exitReason);
                orderEntity.setPortfolioId(portfolioId);
                orderRepo.save(orderEntity);

                logger.info("Executed variant exit for portfolio {}: {} shares of {} at {} reason={} pnl={}",
                    portfolioId, position.getQuantity(), symbol, exitPrice, exitReason, pnl);
                return true;
            }, "ShadowPositionEntity:exit:" + portfolioId + ":" + symbol);
        } catch (RuntimeException e) {
            logger.warn("Failed to execute variant exit for portfolio {} on {}: {}",
                portfolioId, symbol, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<ShadowClosedTrade> findClosedTrades(String portfolioId) {
        if (portfolioId == null) {
            return List.of();
        }
        return shadowPositionRepo
            .findByPortfolioIdAndStatusOrderByExitDateDesc(portfolioId, ShadowPositionEntity.STATUS_CLOSED)
            .stream()
            .map(e -> new ShadowClosedTrade(e.getPortfolioId(), e.getSymbol(), e.getEntryDate(), e.getExitDate(),
                e.getEntryPrice(), e.getExitPrice(), e.getStopLoss(), e.getTarget(), e.getQuantity(),
                e.getExitReason(), e.getPnl()))
            .toList();
    }

    @Override
    public List<com.swingtrade.domain.PaperPortfolioSummary> listPortfolios() {
        return portfolioRepo.findAllByOrderByPortfolioIdAsc().stream()
            .map(p -> new com.swingtrade.domain.PaperPortfolioSummary(p.getPortfolioId(), p.getInitialCapital(),
                p.getCurrentCapital(), p.getTotalRealizedPnl(), p.getOpenPositionCount()))
            .toList();
    }

    @Override
    public List<com.swingtrade.domain.ShadowPositionView> listShadowPositions(String portfolioId) {
        if (portfolioId == null) {
            return List.of();
        }
        return shadowPositionRepo.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId).stream()
            .map(PaperPortfolioServiceImpl::toView).toList();
    }

    @Override
    public List<com.swingtrade.domain.ShadowPositionView> listShadowPositionsForSymbol(String symbol) {
        if (symbol == null) {
            return List.of();
        }
        return shadowPositionRepo.findBySymbolOrderByEntryDateDescIdDesc(symbol).stream()
            .map(PaperPortfolioServiceImpl::toView).toList();
    }

    private static com.swingtrade.domain.ShadowPositionView toView(ShadowPositionEntity e) {
        return new com.swingtrade.domain.ShadowPositionView(e.getPortfolioId(), e.getSymbol(), e.getEntryDate(),
            e.getEntryPrice(), e.getStopLoss(), e.getTarget(), e.getQuantity(), e.getStatus(), e.getExitDate(),
            e.getExitPrice(), e.getExitReason(), e.getPnl());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
