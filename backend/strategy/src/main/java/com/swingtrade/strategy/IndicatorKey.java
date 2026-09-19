package com.swingtrade.strategy;

/**
 * Identifies a family of indicator computable on a {@link MarketContext}, used together with
 * a period/params tuple as a cache key so multiple {@link SignalStrategy} variants asking for
 * the same indicator (e.g. {@code ema(20)}) only trigger one computation (see plan
 * {@code docs/plans/2026-09-16-configurable-multi-strategy.md} §3.1).
 *
 * <p>{@code INDEX_*} keys are reserved for the relative-strength/regime strategy types added in
 * a later phase and are not produced by any Phase 1 strategy.
 */
public enum IndicatorKey {
    EMA,
    RSI,
    ATR,
    VOLUME,
    VOLUME_MA,
    HIGHEST_HIGH,
    LOWEST_LOW,
    /** Bollinger Bands middle band (SMA of close), added for the SQUEEZE type (plan §5.3). */
    BB_MIDDLE,
    /** Bollinger Bands upper band, keyed together with its {@code k} multiplier (plan §5.3). */
    BB_UPPER,
    /** Bollinger Bands lower band, keyed together with its {@code k} multiplier (plan §5.3). */
    BB_LOWER,
    /** Keltner Channel middle line (EMA of typical price), added for the SQUEEZE type (plan §5.3). */
    KELTNER_MIDDLE,
    /** Keltner Channel upper band, keyed together with its ATR multiplier (plan §5.3). */
    KELTNER_UPPER,
    INDEX_CLOSE,
    INDEX_EMA
}
