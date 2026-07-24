package com.swingtrade.broker.model;

/**
 * Enum representing the status of an order.
 */
public enum OrderStatus {
    PENDING,
    ACCEPTED,
    EXECUTING,
    FILLED,
    PARTIALLY_FILLED,
    CANCELLED,
    EXPIRED,
    REJECTED,
    MODIFIED
}