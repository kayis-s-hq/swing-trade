package com.swingtrade.domain;

/** Deterministic result of checking a candidate portfolio entry. */
public record PortfolioExposureDecision(boolean accepted, String reason) {
    public PortfolioExposureDecision {
        reason = reason == null ? "" : reason;
    }

    public static PortfolioExposureDecision accept() {
        return new PortfolioExposureDecision(true, "");
    }

    public static PortfolioExposureDecision reject(String reason) {
        return new PortfolioExposureDecision(false, reason);
    }
}
