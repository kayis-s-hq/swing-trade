package com.swingtrade.llm;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Bounded, API-independent record of the parts of synthesis that can be
 * evaluated after the decision date.
 */
public record SynthesisEvaluation(
    String symbol,
    LocalDate analysisDate,
    String recommendation,
    double confidence,
    boolean conflictDetected,
    boolean eventRiskDetected,
    String eventRiskReason,
    Instant recordedAt,
    OutcomeMeasurement outcome
) {
    public static final int MAX_REASON_LENGTH = 500;

    public SynthesisEvaluation {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (analysisDate == null) {
            throw new IllegalArgumentException("analysisDate is required");
        }
        eventRiskReason = boundedReason(eventRiskReason);
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }

    public SynthesisEvaluation withOutcome(OutcomeMeasurement measurement) {
        return new SynthesisEvaluation(symbol, analysisDate, recommendation, confidence,
            conflictDetected, eventRiskDetected, eventRiskReason, recordedAt, measurement);
    }

    private static String boundedReason(String reason) {
        if (reason == null || reason.length() <= MAX_REASON_LENGTH) return reason;
        return reason.substring(0, MAX_REASON_LENGTH);
    }

    public record OutcomeMeasurement(int horizonDays, BigDecimal forwardReturnPct,
                                     boolean recommendationCorrect, Instant measuredAt) {
        public OutcomeMeasurement {
            if (horizonDays < 1 || horizonDays > 20) {
                throw new IllegalArgumentException("horizonDays must be between 1 and 20");
            }
            if (forwardReturnPct == null) {
                throw new IllegalArgumentException("forwardReturnPct is required");
            }
            measuredAt = measuredAt == null ? Instant.now() : measuredAt;
        }
    }
}
