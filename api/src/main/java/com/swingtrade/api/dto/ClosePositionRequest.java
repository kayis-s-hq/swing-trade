package com.swingtrade.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for closing an existing position.
 */
public class ClosePositionRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    private String exitReason;

    public ClosePositionRequest() {
    }

    public ClosePositionRequest(String symbol, String exitReason) {
        this.symbol = symbol.toUpperCase();
        this.exitReason = exitReason;
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol.toUpperCase();
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }

    /**
     * Validates the close position request.
     * @return true if valid
     */
    public boolean isValid() {
        return symbol != null && !symbol.isEmpty();
    }
}
