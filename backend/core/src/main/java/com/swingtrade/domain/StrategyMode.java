package com.swingtrade.domain;

/**
 * The live-execution mode of a {@link StrategyConfig} variant.
 *
 * <p>{@code SHADOW} and {@code CHAMPION} count toward the plan's "max 12 active variants"
 * shadow cap (docs/plans/2026-09-16-configurable-multi-strategy.md §0). At most one variant may
 * be {@code CHAMPION} at a time.
 */
public enum StrategyMode {
    /** Not evaluated at all (config kept for history/reference only). */
    OFF,
    /** Evaluated only in offline backtests, never in the live job or paper trading. */
    BACKTEST_ONLY,
    /** Evaluated live with its own virtual paper portfolio; no real orders. */
    SHADOW,
    /** The single variant eligible for real orders (not wired up until a later phase). */
    CHAMPION
}
