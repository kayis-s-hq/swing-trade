package com.swingtrade.strategy;

/**
 * Reason a position was closed. In the (legacy, single-symbol) backtest engine, checked in this
 * priority order each bar: {@link #STOP_LOSS} then {@link #TARGET_HIT} then
 * {@link #SIGNAL_EXIT} (if {@link BacktestConfig#signalExitEnabled()}) then
 * {@link #TREND_BREAK} then {@link #TIME_STOP}.
 *
 * <p>{@link #TRAILING} is produced by the new {@link SignalStrategy} SPI's
 * {@link UniformExitEvaluator}, whose precedence is {@link #STOP_LOSS} then {@link #TARGET_HIT}
 * then {@link #SIGNAL_EXIT} then {@link #TRAILING} then {@link #TIME_STOP} (plan §3.3); it does
 * not use {@link #TREND_BREAK}, which remains specific to the legacy engine's streak-based exit.
 *
 * <p>{@link #MANUAL} is used by the live paper-trading path when a position is closed
 * without an explicit reason (e.g. a manual close request).
 */
public enum ExitReason {
    STOP_LOSS,
    TARGET_HIT,
    SIGNAL_EXIT,
    TIME_STOP,
    TREND_BREAK,
    TRAILING,
    MANUAL
}
