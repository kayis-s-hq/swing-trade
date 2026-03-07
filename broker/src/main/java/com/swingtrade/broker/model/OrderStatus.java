package com.swingtrade.broker.model;

/**
 * Enum representing the status of an order in the paper trading system.
 * Orders progress through these states during their lifecycle.
 */
public enum OrderStatus {
    /**
     * Order has been submitted but not yet executed.
     */
    PENDING,
    
    /**
     * Order has been accepted by the system and is waiting for execution.
     */
    ACCEPTED,
    
    /**
     * Order is currently being executed.
     */
    EXECUTING,
    
    /**
     * Order has been completely filled.
     */
    FILLED,
    
    /**
     * Order has been partially filled.
     */
    PARTIALLY_FILLED,
    
    /**
     * Order has been cancelled by the user or system.
     */
    CANCELLED,
    
    /**
     * Order has expired due to time constraints.
     */
    EXPIRED
}
