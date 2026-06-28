package com.swingtrade.api.dto;

/**
 * Request DTO for kill switch API endpoint.
 * Used to enable/disable the trading kill switch.
 */
public record KillSwitchRequest(
        boolean enabled,
        String reason
) {
    /**
     * Validate the request.
     *
     * @return true if valid
     */
    public boolean isValid() {
        return enabled; // enabled must be true to activate
    }

    /**
     * Get a human-readable reason.
     *
     * @return reason string or default message
     */
    public String getReason() {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return enabled ? "Kill switch activated by user" : "Kill switch deactivated by user";
    }
}
