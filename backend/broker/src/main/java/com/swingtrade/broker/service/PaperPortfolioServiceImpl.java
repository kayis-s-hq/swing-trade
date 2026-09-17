package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
import com.swingtrade.broker.util.OptimisticLockRetryHelper;
import com.swingtrade.domain.service.PaperPortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    private final PaperTradingPortfolioRepository portfolioRepo;
    private final PaperTradingSnapshotRepository snapshotRepo;
    private final PaperTradingStateService defaultPortfolioStateService;

    public PaperPortfolioServiceImpl(PaperTradingPortfolioRepository portfolioRepo,
                                      PaperTradingSnapshotRepository snapshotRepo,
                                      PaperTradingStateService defaultPortfolioStateService) {
        this.portfolioRepo = portfolioRepo;
        this.snapshotRepo = snapshotRepo;
        this.defaultPortfolioStateService = defaultPortfolioStateService;
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

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
