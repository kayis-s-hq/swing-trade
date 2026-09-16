package com.swingtrade.strategy;

import java.util.Set;

/**
 * A configurable family of entry/exit rules evaluated against a {@link MarketContext}, replacing
 * the fixed {@link TradingStrategy} 4-rule shape so future strategy types (PULLBACK, SQUEEZE,
 * RS_NIFTY - added in later phases) don't need to fake unrelated rule methods (plan finding F1).
 *
 * <p>Both live signal generation (last bar) and backtesting (every bar) call
 * {@link #evaluateEntry}/{@link #evaluateExit} through this same interface with an explicit
 * {@code barIndex}, so the two paths can never drift. Implementations must read bar data only
 * through {@link MarketContext#view(int)} bounded at {@code barIndex} - that view throws on any
 * attempt to read a later bar, which is what makes this method safe to call at every bar of a
 * backtest without look-ahead bias (plan §3.1).
 */
public interface SignalStrategy {

    /** Unique, stable type identifier, e.g. {@code "BREAKOUT"}. Not for display formatting. */
    String type();

    /** Describes this type's configurable parameters (names, types, ranges, defaults). */
    ParamSchema paramSchema();

    /** The indicators this strategy needs computed, given a resolved params view. */
    Set<IndicatorKey> requiredIndicators(StrategyParamsView params);

    /** Minimum number of leading bars required before {@link #evaluateEntry} can be trusted. */
    int warmupBars(StrategyParamsView params);

    /**
     * Evaluates entry rules at {@code barIndex}. Must not read any bar after {@code barIndex}
     * (enforce via {@code ctx.view(barIndex)}).
     */
    StrategyDecision evaluateEntry(MarketContext ctx, int barIndex, StrategyParamsView params);

    /**
     * Evaluates exit rules at {@code barIndex} for an open {@code position}. Must not read any
     * bar after {@code barIndex} (enforce via {@code ctx.view(barIndex)}).
     */
    ExitDecision evaluateExit(MarketContext ctx, int barIndex, OpenPosition position, StrategyParamsView params);
}
