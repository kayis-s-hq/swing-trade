package com.swingtrade.broker.model;

/**
 * Enum representing the status of a position.
 */
public enum PositionStatus {
    OPEN,
    CLOSED,
    PENDING_CLOSE,
    STOPPED,
    TARGET_HIT
}