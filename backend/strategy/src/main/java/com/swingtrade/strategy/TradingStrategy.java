package com.swingtrade.strategy;

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
