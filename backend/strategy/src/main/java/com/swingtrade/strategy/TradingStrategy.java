package com.swingtrade.strategy;

import com.swingtrade.domain.MarketRegime;
import com.swingtrade.domain.MarketRegimeAssessment;

import java.util.Set;


/**
 * A named set of entry/exit rules, evaluated against a single bar's {@link Indicators}.
 *
 * <p>Both the live signal engine ({@link PriceActionSignalEngine}) and the backtest
 * simulator ({@link BacktestEngine}) evaluate rules through this interface so a
 * strategy's thresholds can never drift between live trading and backtesting - and so
 * additional strategies can be added and selected for backtest comparison (see
 * {@link StrategyRegistry}) without changing either engine.
 *
 * <p>Live signal generation and the daily job orchestration are deliberately pinned to a
 * single production strategy and do not expose selection; only backtest/testing paths do.
 */
public interface TradingStrategy {

    /** Configured strategies opt into the market-regime overlay explicitly. */
    default boolean regimeFilterEnabled() {
        return false;
    }

    /** Regimes in which an opted-in strategy may open a position. */
    default Set<MarketRegime> allowedMarketRegimes() {
        return Set.of(MarketRegime.BULLISH);
    }

    /** Final entry eligibility; missing regime facts fail closed when enabled. */
    default boolean isEntryEligible(Indicators indicators, MarketRegimeAssessment regime) {
        if (!isEntrySignal(indicators)) {
            return false;
        }
        return !regimeFilterEnabled()
            || regime != null
            && regime.eligible()
            && allowedMarketRegimes().contains(regime.regime());
    }

    /** Number of entry rules that must pass for this strategy to enter. */
    default int requiredEntryRules() {
        return 4;
    }

    /** Human-readable description of this strategy's entry confluence. */
    default String entryConfluenceDescription() {
        return "All entry rules passed";
    }

    /** Human-readable description of this strategy's RSI entry range. */
    default String entryRsiDescription() {
        return "RSI between 50-65";
    }

    /**
     * Unique, stable name used to select this strategy (e.g. via {@link StrategyRegistry}
     * or a backtest API parameter). Not intended for display formatting.
     */
    String name();

    /**
     * All entry conditions that must hold for a BUY signal. Default combines the four
     * individual rule checks below with AND — override only if a strategy's entry logic
     * isn't a simple all-of confluence.
     */
    default boolean isEntrySignal(Indicators indicators) {
        return trendAligned(indicators)
            && rsiInEntryRange(indicators)
            && volumeSurge(indicators)
            && nearWeeklyHigh(indicators);
    }

    /**
     * Any exit condition that fires a SELL/signal-exit. Default combines the three
     * individual rule checks below with OR (any one is sufficient) - deliberately looser
     * than entry ("enter carefully, exit quickly").
     */
    default boolean isSignalExit(Indicators indicators) {
        return closeBelowEma20(indicators) || ema20BelowEma50(indicators) || rsiBelowLowerBound(indicators);
    }

    boolean trendAligned(Indicators indicators);

    boolean rsiInEntryRange(Indicators indicators);

    boolean volumeSurge(Indicators indicators);

    boolean nearWeeklyHigh(Indicators indicators);

    boolean closeBelowEma20(Indicators indicators);

    boolean ema20BelowEma50(Indicators indicators);

    boolean rsiBelowLowerBound(Indicators indicators);
}
