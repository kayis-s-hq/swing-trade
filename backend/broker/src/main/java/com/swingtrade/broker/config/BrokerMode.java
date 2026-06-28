package com.swingtrade.broker.config;

/**
 * Enum representing broker trading modes.
 * Controls how orders are processed and executed.
 */
public enum BrokerMode {
    /**
     * Paper trading mode - simulates orders without real execution.
     * Uses the existing PaperTradeEngine for simulation.
     */
    PAPER,

    /**
     * Live trading mode - sends orders to Zerodha Kite Connect.
     * Requires valid API credentials and authorization.
     */
    LIVE,

    /**
     * Dry-run mode - logs orders without sending to broker.
     * Useful for testing and validation before live trading.
     */
    DRY_RUN;

    /**
     * Check if this mode allows actual order execution.
     */
    public boolean allowsExecution() {
        return this == LIVE;
    }

    /**
     * Check if this mode is safe for testing (no real money).
     */
    public boolean isSafeMode() {
        return this == PAPER || this == DRY_RUN;
    }

    /**
     * Get human-readable description.
     */
    public String getDescription() {
        switch (this) {
            case PAPER:
                return "Paper Trading (Simulation)";
            case LIVE:
                return "Live Trading (Real Money)";
            case DRY_RUN:
                return "Dry-Run (Logging Only)";
            default:
                return "Unknown";
        }
    }

    /**
     * Parse broker mode from string.
     *
     * @param modeString mode string (paper, live, dry_run)
     * @return BrokerMode enum
     * @throws IllegalArgumentException if mode is not recognized
     */
    public static BrokerMode fromString(String modeString) {
        if (modeString == null) {
            return BrokerMode.PAPER;
        }

        switch (modeString.toLowerCase().trim()) {
            case "paper":
            case "paper_trading":
                return BrokerMode.PAPER;
            case "live":
            case "production":
            case "real":
                return BrokerMode.LIVE;
            case "dry_run":
            case "dry-run":
            case "dry":
                return BrokerMode.DRY_RUN;
            default:
                throw new IllegalArgumentException("Unknown broker mode: " + modeString +
                        ". Valid modes: paper, live, dry_run");
        }
    }
}
