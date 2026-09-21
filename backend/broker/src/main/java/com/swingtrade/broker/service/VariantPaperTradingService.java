package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.util.OptimisticLockRetryHelper;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.service.VariantTradingService;
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
 * Gives every active (SHADOW or CHAMPION) strategy variant its own simulated
 * paper-trading portfolio, keyed by {@code portfolio_id = variantId} on both
 * {@code paper_trading_portfolio} and {@code positions} (see migration V63).
 *
 * <p>Deliberately independent of {@code PaperTradingEngine}/{@code PositionManager}'s
 * single in-memory "default" portfolio: those continue to back the champion's real
 * order-queueing path unchanged. This service only does simulated bookkeeping —
 * capital debit/credit and open/closed {@code PositionEntity} rows — for every
 * variant (including the champion, which gets both its existing real path AND its
 * own variant-attributed bookkeeping here for apples-to-apples comparison against
 * shadows).
 *
 * <p>Risk-based position sizing mirrors {@code OrderManager.calculatePositionSize}
 * (2% of the variant's own available capital, sized off signal stop distance) so a
 * shadow variant's simulated fills are directly comparable to how the champion would
 * have sized the same signal.
 */
@Service
public class VariantPaperTradingService implements VariantTradingService {

    private static final Logger logger = LoggerFactory.getLogger(VariantPaperTradingService.class);
    private static final String BROKER_TYPE = "PAPER";
    private static final BigDecimal RISK_PER_TRADE_PCT = BigDecimal.valueOf(0.02);
    private static final BigDecimal DEFAULT_STOP_PCT = BigDecimal.valueOf(0.03);
    private static final BigDecimal TARGET_REWARD_RATIO = BigDecimal.valueOf(2.5);

    private final PaperTradingPortfolioRepository portfolioRepo;
    private final PositionRepository positionRepo;
    private final AtomicLong positionCounter = new AtomicLong(0);

    public VariantPaperTradingService(PaperTradingPortfolioRepository portfolioRepo,
                                       PositionRepository positionRepo) {
        this.portfolioRepo = portfolioRepo;
        this.positionRepo = positionRepo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensurePortfolio(String variantId, BigDecimal paperCapital) {
        if (variantId == null || variantId.isBlank()) return;
        BigDecimal capital = paperCapital != null && paperCapital.signum() > 0
            ? paperCapital : BigDecimal.valueOf(100000);
        if (portfolioRepo.findByPortfolioId(variantId).isPresent()) {
            return;
        }
        try {
            OptimisticLockRetryHelper.execute(() -> {
                if (portfolioRepo.findByPortfolioId(variantId).isPresent()) return null;
                PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
                entity.setId(nextPortfolioId());
                entity.setPortfolioId(variantId);
                entity.setInitialCapital(capital);
                entity.setCurrentCapital(capital);
                entity.setTotalRealizedPnl(BigDecimal.ZERO);
                entity.setTotalUnrealizedPnL(BigDecimal.ZERO);
                entity.setOpenPositionCount(0);
                portfolioRepo.save(entity);
                logger.info("Created paper-trading portfolio for variant {} with capital {}", variantId, capital);
                return null;
            }, "PaperTradingPortfolioEntity(" + variantId + ")");
        } catch (RuntimeException e) {
            // A duplicate-key race on portfolio_id (two concurrent mode-change calls for the
            // same variant) is benign — whichever writer lost just means the portfolio now
            // exists, which is exactly the postcondition this method promises.
            if (portfolioRepo.findByPortfolioId(variantId).isEmpty()) {
                throw e;
            }
        }
    }

    private Long nextPortfolioId() {
        long max = portfolioRepo.findAll().stream()
            .map(PaperTradingPortfolioEntity::getId)
            .filter(java.util.Objects::nonNull)
            .max(Long::compareTo)
            .orElse(1L);
        return Math.max(max + 1, 2L);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean openPosition(String variantId, Signal buySignal, BigDecimal referencePrice) {
        if (variantId == null || buySignal == null || referencePrice == null || referencePrice.signum() <= 0) {
            return false;
        }
        String symbol = buySignal.symbol();
        if (!positionRepo.findOpenByPortfolioIdAndSymbol(variantId, symbol).isEmpty()) {
            logger.debug("Variant {} already holds an open position in {}, skipping BUY", variantId, symbol);
            return false;
        }
        PaperTradingPortfolioEntity portfolio = portfolioRepo.findByPortfolioId(variantId).orElse(null);
        if (portfolio == null) {
            logger.warn("No paper-trading portfolio for variant {}, cannot open {} — ensurePortfolio() "
                + "was not called for this variant's mode transition", variantId, symbol);
            return false;
        }

        BigDecimal stopLoss = buySignal.stopLoss() != null && buySignal.stopLoss().signum() > 0
            ? buySignal.stopLoss()
            : referencePrice.multiply(BigDecimal.ONE.subtract(DEFAULT_STOP_PCT));
        BigDecimal riskPerShare = referencePrice.subtract(stopLoss);
        if (riskPerShare.signum() <= 0) {
            riskPerShare = referencePrice.multiply(DEFAULT_STOP_PCT);
        }
        BigDecimal target = buySignal.target() != null && buySignal.target().signum() > 0
            ? buySignal.target()
            : referencePrice.add(riskPerShare.multiply(TARGET_REWARD_RATIO));

        BigDecimal currentCapital = portfolio.getCurrentCapital() != null
            ? portfolio.getCurrentCapital() : BigDecimal.ZERO;
        BigDecimal riskBudget = currentCapital.multiply(RISK_PER_TRADE_PCT);
        int qtyByRisk = riskBudget.divide(riskPerShare, 0, RoundingMode.DOWN).intValue();
        int qtyByCapital = currentCapital.divide(referencePrice, 0, RoundingMode.DOWN).intValue();
        int quantity = Math.min(qtyByRisk, qtyByCapital);
        if (quantity <= 0) {
            logger.debug("Variant {} has insufficient capital ({}) to open {} at {}",
                variantId, currentCapital, symbol, referencePrice);
            return false;
        }

        BigDecimal cost = referencePrice.multiply(BigDecimal.valueOf(quantity));
        try {
            PositionEntity saved = OptimisticLockRetryHelper.execute(() -> {
                PaperTradingPortfolioEntity fresh = portfolioRepo.findByPortfolioId(variantId).orElseThrow();
                if (fresh.getCurrentCapital().compareTo(cost) < 0) {
                    return null;
                }
                fresh.setCurrentCapital(fresh.getCurrentCapital().subtract(cost));
                fresh.setOpenPositionCount(fresh.getOpenPositionCount() + 1);
                portfolioRepo.save(fresh);

                PositionEntity entity = new PositionEntity();
                entity.setPortfolioId(variantId);
                entity.setBrokerType(BROKER_TYPE);
                entity.setSymbol(symbol);
                entity.setEntryPrice(referencePrice);
                entity.setEntryDate(buySignal.date() != null ? buySignal.date() : LocalDate.now());
                entity.setEntryTime(LocalDateTime.now());
                entity.setQuantity(quantity);
                entity.setStopLoss(stopLoss);
                entity.setTarget(target);
                entity.setStatus("OPEN");
                entity.setEntryReason(buySignal.reasoning());
                entity.setCurrentPrice(referencePrice);
                entity.setPositionId("VPOS_" + variantId + "_" + positionCounter.incrementAndGet());
                entity.setDirection(TradeDirection.LONG.name());
                entity.setAveragePrice(referencePrice);
                entity.setUnrealizedPnL(BigDecimal.ZERO);
                entity.setRealizedPnL(BigDecimal.ZERO);
                entity.setMarginUtilized(BigDecimal.ZERO);
                entity.setPartialExitTaken(false);
                entity.setSignalId(buySignal.id());
                positionRepo.save(entity);
                logger.info("Variant {} opened {} qty={} at {} (SL={}, target={})",
                    variantId, symbol, quantity, referencePrice, stopLoss, target);
                return entity;
            }, "PositionEntity(variant=" + variantId + ")");
            return saved != null;
        } catch (RuntimeException e) {
            logger.warn("Failed to open position for variant {} in {}: {}", variantId, symbol, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean closePosition(String variantId, String symbol, BigDecimal exitPrice, String reason) {
        return closePositionWithStatus(variantId, symbol, exitPrice, reason, "CLOSED");
    }

    private boolean closePositionWithStatus(String variantId, String symbol, BigDecimal exitPrice,
                                             String reason, String status) {
        if (variantId == null || symbol == null || exitPrice == null) return false;
        List<PositionEntity> open = positionRepo.findOpenByPortfolioIdAndSymbol(variantId, symbol);
        if (open.isEmpty()) return false;

        boolean[] closedAny = {false};
        for (PositionEntity position : open) {
            try {
                OptimisticLockRetryHelper.execute(() -> {
                    PositionEntity fresh = positionRepo.findById(position.getId()).orElse(null);
                    if (fresh == null || !"OPEN".equals(fresh.getStatus())) return null;
                    BigDecimal realizedPnL = exitPrice.subtract(fresh.getEntryPrice())
                        .multiply(BigDecimal.valueOf(fresh.getQuantity()));
                    fresh.setStatus(status);
                    fresh.setCurrentPrice(exitPrice);
                    fresh.setUnrealizedPnL(BigDecimal.ZERO);
                    fresh.setRealizedPnL(realizedPnL);
                    fresh.setExitTime(LocalDateTime.now());
                    fresh.setExitReason(reason);
                    positionRepo.save(fresh);

                    PaperTradingPortfolioEntity portfolio = portfolioRepo.findByPortfolioId(variantId).orElse(null);
                    if (portfolio != null) {
                        BigDecimal proceeds = exitPrice.multiply(BigDecimal.valueOf(fresh.getQuantity()));
                        portfolio.setCurrentCapital(portfolio.getCurrentCapital().add(proceeds));
                        portfolio.setTotalRealizedPnl((portfolio.getTotalRealizedPnl() != null
                            ? portfolio.getTotalRealizedPnl() : BigDecimal.ZERO).add(realizedPnL));
                        portfolio.setOpenPositionCount(Math.max(0, portfolio.getOpenPositionCount() - 1));
                        portfolioRepo.save(portfolio);
                    }
                    closedAny[0] = true;
                    logger.info("Variant {} closed {} qty={} at {} ({}) — realized P&L {}",
                        variantId, symbol, fresh.getQuantity(), exitPrice, reason, realizedPnL);
                    return null;
                }, "PositionEntity(variant=" + variantId + ",close)");
            } catch (RuntimeException e) {
                logger.warn("Failed to close position {} for variant {}: {}",
                    position.getPositionId(), variantId, e.getMessage());
            }
        }
        return closedAny[0];
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int evaluateOpenPositions(String variantId, String symbol, OhlcvCandle candle) {
        if (variantId == null || symbol == null || candle == null) return 0;
        List<PositionEntity> open = positionRepo.findOpenByPortfolioIdAndSymbol(variantId, symbol);
        int closed = 0;
        for (PositionEntity position : open) {
            // Same stop/target trigger logic PositionManager.checkPositionTriggers() uses for
            // the champion's own positions (reused, not reinvented — see task constraint):
            // a LONG position with the day's low breaching the stop, or the day's high
            // reaching the target, fills at the worse of the trigger price or the day's open.
            BigDecimal low = candle.low();
            BigDecimal high = candle.high();
            BigDecimal stopLoss = position.getStopLoss();
            BigDecimal target = position.getTarget();
            if (stopLoss != null && low.compareTo(stopLoss) <= 0) {
                BigDecimal fill = candle.open().compareTo(stopLoss) <= 0 ? candle.open() : stopLoss;
                if (closePositionWithStatus(variantId, symbol, fill,
                        "Stop Loss Hit - Price dropped to " + low, "STOPPED")) {
                    closed++;
                }
            } else if (target != null && high.compareTo(target) >= 0) {
                BigDecimal fill = candle.open().compareTo(target) >= 0 ? candle.open() : target;
                if (closePositionWithStatus(variantId, symbol, fill,
                        "Target Hit - Price rose to " + high, "TARGET_HIT")) {
                    closed++;
                }
            }
        }
        return closed;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Position> findOpenPositions(String variantId, String symbol) {
        if (variantId == null || symbol == null) return List.of();
        return positionRepo.findOpenByPortfolioIdAndSymbol(variantId, symbol).stream()
            .map(PositionEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Position> findClosedTrades(String variantId) {
        if (variantId == null) return List.of();
        return positionRepo.findClosedByPortfolioId(variantId).stream()
            .map(PositionEntity::toDomain)
            .toList();
    }
}
