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
     */
    STOP,
    
    /**
     * Stop-limit order that becomes a limit order when the stop price is reached.
     */
    STOP_LIMIT
}
