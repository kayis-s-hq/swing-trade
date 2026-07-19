package com.swingtrade.api.dto;

/**
 * Request DTO for closing an existing position.
 * Symbol is provided via the URL path.
 */
public class ClosePositionRequest {

    private String exitReason;

    public ClosePositionRequest() {
    }

    // Getters and Setters
    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }
}