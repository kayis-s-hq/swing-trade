package com.swingtrade.broker.model;

/**
 * Enum representing the status of a position in the paper trading system.
 * Positions can be open, closed, or in a transition state.
 */
public enum PositionStatus {
    /**
     * Position is currently open and actively held.
     */
    OPEN,

    /**
     * Position is closed and no longer held.
     */
    CLOSED,

    /**
     * Position is pending closure (e.g., exit order placed).
     */
    PENDING_CLOSE,

    /**
     * Position was closed because stop loss was triggered.
     */
    STOPPED,

    /**
     * Position was closed because target price was hit.
     */
    TARGET_HIT
}