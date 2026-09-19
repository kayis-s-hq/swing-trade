package com.swingtrade.llm;

import java.util.Map;

/** Aggregate accuracy view over recorded synthesis decisions with measured outcomes. */
public record SynthesisEvaluationSummary(
        long totalEvaluations,
        long measuredEvaluations,
        long correctEvaluations,
        double accuracyPct,
        Map<String, Long> measuredByRecommendation
) {
    public SynthesisEvaluationSummary {
        measuredByRecommendation = measuredByRecommendation == null ? Map.of() : Map.copyOf(measuredByRecommendation);
    }
}
