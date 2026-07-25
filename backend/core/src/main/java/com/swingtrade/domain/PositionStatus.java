package com.swingtrade.domain;

/**
 * Enum representing the status of a trading position.
 */
public enum PositionStatus {
    OPEN("Open"),
    CLOSED("Closed"),
    PENDING_CLOSE("Pending Close"),
    STOPPED("Stopped"),
    TARGET_HIT("Target Hit");

    private final String displayName;

    PositionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}