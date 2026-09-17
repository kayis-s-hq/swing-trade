package com.swingtrade.api.service;

import com.swingtrade.api.dto.strategy.PromotionEligibilityResponse;
import com.swingtrade.data.entity.StrategyExperimentLogEntity;
import com.swingtrade.data.repository.StrategyExperimentLogRepository;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.ExitReason;
import com.swingtrade.strategy.PortfolioTrade;
import com.swingtrade.strategy.PromotionEligibilityChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates plan §7.4's champion/challenger promotion-eligibility checklist for
 * {@code GET /api/strategies/{variantId}/promotion-eligibility}: resolves the current champion
 * and the requested challenger via {@link StrategyConfigStore}, pulls each side's real closed
 * paper trades via {@link PaperPortfolioService#findClosedTrades}, looks up the most recent
 * walk-forward OOS Sharpe/DSR p-value logged for each in {@code strategy_experiment_log}, and
 * hands all of that to the pure {@link PromotionEligibilityChecker}.
 *
 * <p><b>MaxDD source:</b> {@link com.swingtrade.strategy.MetricsCalculator}'s drawdown helper
 * only operates on a daily mark-to-market equity curve (plan §6.1/§6.2), which the live paper
 * portfolios don't persist. This class instead derives a trade-level MaxDD by replaying each
 * side's own closed trades in exit-date order against a running equity value seeded at that
 * variant's {@code paperCapital} - a coarser (trade-close-to-trade-close, not daily) drawdown
 * than the backtest engine's, but the only one available from live paper-trading data; this is
 * a documented judgment call, not a value taken from the plan text.
 */
@Service
public class PromotionEligibilityService {

    private final StrategyConfigStore configStore;
    private final PaperPortfolioService paperPortfolioService;
    private final StrategyExperimentLogRepository experimentLogRepository;

    public PromotionEligibilityService(
        StrategyConfigStore configStore,
        PaperPortfolioService paperPortfolioService,
        StrategyExperimentLogRepository experimentLogRepository
    ) {
        this.configStore = configStore;
        this.paperPortfolioService = paperPortfolioService;
        this.experimentLogRepository = experimentLogRepository;
    }

    public PromotionEligibilityResponse evaluate(String challengerVariantId) {
        StrategyConfig champion = configStore.findCurrentChampion()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No champion is currently set"));
        StrategyConfig challenger = configStore.findCurrent(challengerVariantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Unknown variant: " + challengerVariantId));

        long tenureCalendarDays = tenureDays(challenger);

        List<PortfolioTrade> challengerTrades = toPortfolioTrades(
            paperPortfolioService.findClosedTrades(challenger.variantId()));
        List<PortfolioTrade> championTrades = toPortfolioTrades(
            paperPortfolioService.findClosedTrades(champion.variantId()));

        double challengerMaxDd = maxDrawdownPct(challenger.paperCapital(), challengerTrades);
        double championMaxDd = maxDrawdownPct(champion.paperCapital(), championTrades);

        WalkForwardStats challengerStats = latestWalkForwardStats(challenger);
        WalkForwardStats championStats = latestWalkForwardStats(champion);

        PromotionEligibilityChecker.PromotionEligibilityResult result = PromotionEligibilityChecker.evaluate(
            tenureCalendarDays,
            challengerTrades,
            championTrades,
            challengerMaxDd,
            championMaxDd,
            challengerStats.oosSharpe(),
            championStats.oosSharpe(),
            challengerStats.dsrPValue()
        );

        return PromotionEligibilityResponse.from(challenger.variantId(), champion.variantId(), tenureCalendarDays, result);
    }

    /**
     * Days since the challenger's <em>current version</em> was created. Per plan §4.5's
     * immutable-versions design, a new version is a brand-new row with its own
     * {@code createdAt}, so tenure naturally resets whenever the challenger's params change -
     * it is never carried over from a prior version.
     */
    private long tenureDays(StrategyConfig config) {
        LocalDateTime createdAt = config.createdAt();
        if (createdAt == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDateTime.now().toLocalDate());
    }

    private WalkForwardStats latestWalkForwardStats(StrategyConfig config) {
        Optional<StrategyExperimentLogEntity> entry = experimentLogRepository
            .findTopByVariantIdAndVersionOrderByCreatedAtDesc(config.variantId(), config.version());
        if (entry.isEmpty()) {
            return new WalkForwardStats(null, null);
        }
        var metrics = entry.get().getMetrics();
        Double oosSharpe = asDouble(metrics.get("oosSharpe"));
        Double dsrPValue = asDouble(metrics.get("dsrPValue"));
        return new WalkForwardStats(oosSharpe, dsrPValue);
    }

    private Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private List<PortfolioTrade> toPortfolioTrades(List<ShadowClosedTrade> trades) {
        return trades.stream().map(this::toPortfolioTrade).toList();
    }

    /**
     * Maps the core module's live-trading {@link ShadowClosedTrade} onto the strategy module's
     * backtest-domain {@link PortfolioTrade} so the (pure, backtest-oriented)
     * {@link PromotionEligibilityChecker} can consume live paper-trading history without knowing
     * about the core module's persistence-facing shape. {@code riskPerShare} is derived
     * ({@code entryPrice - stopLoss}, matching {@code ShadowClosedTrade.rMultiple()}'s own
     * formula); MAE/MFE and strategy-score-at-entry have no live-trading equivalent (they're
     * per-bar backtest diagnostics) and are left at zero since the checker never reads them.
     */
    private PortfolioTrade toPortfolioTrade(ShadowClosedTrade trade) {
        BigDecimal riskPerShare = trade.entryPrice() != null && trade.stopLoss() != null
            ? trade.entryPrice().subtract(trade.stopLoss())
            : BigDecimal.ZERO;
        double pnlPct = trade.entryPrice() != null && trade.entryPrice().signum() != 0 && trade.quantity() != 0
            ? trade.pnl().doubleValue() / (trade.entryPrice().doubleValue() * trade.quantity()) * 100.0
            : 0.0;
        ExitReason exitReason = parseExitReason(trade.exitReason());

        return new PortfolioTrade(
            trade.symbol(),
            trade.entryDate(),
            trade.exitDate(),
            trade.entryPrice(),
            trade.exitPrice(),
            trade.stopLoss(),
            trade.target(),
            trade.quantity(),
            exitReason,
            riskPerShare,
            trade.pnl(),
            pnlPct,
            (int) trade.holdingDays(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    private ExitReason parseExitReason(String raw) {
        if (raw == null) {
            return ExitReason.MANUAL;
        }
        try {
            return ExitReason.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ExitReason.MANUAL;
        }
    }

    /**
     * Trade-close-to-trade-close MaxDD (%): replays {@code trades} in exit-date order against a
     * running equity value seeded at {@code startingCapital}, tracking peak-to-trough drawdown -
     * see this class's javadoc for why this differs from the backtest engine's daily version.
     */
    private double maxDrawdownPct(BigDecimal startingCapital, List<PortfolioTrade> trades) {
        double capital = startingCapital == null ? 0.0 : startingCapital.doubleValue();
        if (capital <= 0.0 || trades.isEmpty()) {
            return 0.0;
        }
        List<PortfolioTrade> ordered = trades.stream()
            .sorted((a, b) -> {
                if (a.exitDate() == null || b.exitDate() == null) {
                    return 0;
                }
                return a.exitDate().compareTo(b.exitDate());
            })
            .toList();

        double equity = capital;
        double peak = capital;
        double maxDrawdown = 0.0;
        for (PortfolioTrade trade : ordered) {
            equity += trade.pnl().doubleValue();
            if (equity > peak) {
                peak = equity;
            } else if (peak > 0) {
                maxDrawdown = Math.max(maxDrawdown, (peak - equity) / peak * 100.0);
            }
        }
        return maxDrawdown;
    }

    private record WalkForwardStats(Double oosSharpe, Double dsrPValue) {
    }
}
