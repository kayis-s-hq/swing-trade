package com.swingtrade.llm.service;

import com.swingtrade.llm.SynthesisEvaluation;
import com.swingtrade.llm.SynthesisOutput;
import com.swingtrade.domain.CompositeAnalysis;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records only bounded synthesis metadata. It deliberately does not affect
 * the synthesis verdict or any existing API result.
 */
@Service
public class SynthesisEvaluationService {
    static final int MAX_RECORDS = 10_000;
    private final Map<Key, SynthesisEvaluation> evaluations = new ConcurrentHashMap<>();

    public SynthesisEvaluation record(CompositeAnalysis composite, SynthesisOutput output) {
        var evaluation = new SynthesisEvaluation(
            composite.symbol(), composite.date(), output.getRecommendation(),
            output.getConfidence() == null ? 0.0 : output.getConfidence(),
            output.isConflictDetected(), output.isEventRiskDetected(), output.getEventRiskReason(),
            Instant.now(), null);
        if (evaluations.size() >= MAX_RECORDS) {
            evaluations.keySet().stream().findAny().ifPresent(evaluations::remove);
        }
        evaluations.put(new Key(evaluation.symbol(), evaluation.analysisDate()), evaluation);
        return evaluation;
    }

    public SynthesisEvaluation recordOutcome(String symbol, java.time.LocalDate analysisDate,
                                             int horizonDays, BigDecimal forwardReturnPct) {
        var key = new Key(symbol, analysisDate);
        var existing = evaluations.get(key);
        if (existing == null) return null;
        boolean correct = recommendationCorrect(existing.recommendation(), forwardReturnPct);
        var measured = new SynthesisEvaluation.OutcomeMeasurement(
            horizonDays, forwardReturnPct, correct, Instant.now());
        var updated = existing.withOutcome(measured);
        evaluations.put(key, updated);
        return updated;
    }

    public SynthesisEvaluation find(String symbol, java.time.LocalDate analysisDate) {
        return evaluations.get(new Key(symbol, analysisDate));
    }

    private boolean recommendationCorrect(String recommendation, BigDecimal forwardReturnPct) {
        if (recommendation == null || forwardReturnPct == null) return false;
        return switch (recommendation.trim().toUpperCase()) {
            case "BUY" -> forwardReturnPct.signum() > 0;
            case "SELL" -> forwardReturnPct.signum() < 0;
            case "HOLD" -> forwardReturnPct.signum() == 0;
            default -> false;
        };
    }

    private record Key(String symbol, java.time.LocalDate analysisDate) {}
}
