package com.swingtrade.domain;

import java.time.LocalDate;

/** Result of comparing a stock's bounded return with an index's bounded return. */
public record RelativeStrengthAssessment(
        boolean eligible,
        double stockReturnPct,
        double indexReturnPct,
        double excessReturnPct,
        LocalDate asOf,
        String reason
) {
    public RelativeStrengthAssessment {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Assessment reason cannot be blank");
        }
    }

    public static RelativeStrengthAssessment unavailable(String reason) {
        return new RelativeStrengthAssessment(false, 0.0, 0.0, 0.0, null, reason);
    }
}
