package com.swingtrade.strategy;

/**
 * Reason a backtest position was closed. Checked in this priority order each bar:
 * {@link #STOP_LOSS} then {@link #TARGET_HIT} then {@link #TREND_BREAK} then {@link #TIME_STOP}.
 */
public enum ExitReason {
    STOP_LOSS,
    TARGET_HIT,
    TIME_STOP,
    TREND_BREAK
}
