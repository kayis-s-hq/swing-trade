package com.swingtrade.domain;

import java.time.LocalDate;

/** Result of evaluating an index candle series for a market-regime policy. */
public record MarketRegimeAssessment(
        MarketRegime regime,
        boolean eligible,
        LocalDate asOf,
        String reason
) {
    public MarketRegimeAssessment {
        if (regime == null) {
            throw new IllegalArgumentException("Market regime cannot be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Assessment reason cannot be blank");
        }
    }

    public static MarketRegimeAssessment unavailable(String reason) {
        return new MarketRegimeAssessment(MarketRegime.UNKNOWN, false, null, reason);
    }
}
