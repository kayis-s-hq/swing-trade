package com.swingtrade.broker.model;

/**
 * Enum representing the direction of a trade in the paper trading system.
 * Used to indicate whether a position is long or short.
 */
public enum TradeDirection {
    /**
     * Long position - buying assets with expectation of price increase.
     */
    LONG,
    
    /**
     * Short position - selling assets with expectation of price decrease.
     */
    SHORT
}
