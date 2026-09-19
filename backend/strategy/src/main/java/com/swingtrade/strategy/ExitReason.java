package com.swingtrade.strategy;

/**
 * Reason a position was closed. In the backtest engine, checked in this priority order
 * each bar: {@link #STOP_LOSS} then {@link #TARGET_HIT} then {@link #SIGNAL_EXIT} (if
 * {@link BacktestConfig#signalExitEnabled()}) then {@link #TREND_BREAK} then {@link #TIME_STOP}.
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
    BREAKEVEN_STOP,
    TRAILING_STOP,
    MANUAL
}
