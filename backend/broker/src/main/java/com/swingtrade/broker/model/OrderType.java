package com.swingtrade.broker.model;

/**
 * Enum representing different order types used in the paper trading system.
 * These order types simulate real-world trading orders.
 */
public enum OrderType {
    /**
     * Market order that executes immediately at the best available price.
     */
    MARKET,

    /**
     * Limit order that executes only at the specified price or better.
     */
    LIMIT,

    /**
     * Stop order that becomes a market order when the stop price is reached.
     * Used for stop loss orders to limit downside risk.
     */
    STOP_LOSS,

    /**
     * Stop order that becomes a market order when the stop price is reached.
     * Used for take profit orders to capture gains at target price.
     */
    TAKE_PROFIT,

    /**
     * Stop order that becomes a market order when the stop price is reached.
     * Generic stop order for any trigger-based execution.
     */
    STOP,

    /**
     * Stop-limit order that becomes a limit order when the stop price is reached.
     * Provides more control over execution price after stop trigger.
     */
    STOP_LIMIT
}
