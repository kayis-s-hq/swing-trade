package com.swingtrade.llm.service;

import com.swingtrade.llm.SynthesisEvaluation;
import com.swingtrade.llm.SynthesisOutput;
import com.swingtrade.llm.SynthesisEvaluationEntity;
import com.swingtrade.llm.SynthesisEvaluationRepository;
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
    private final SynthesisEvaluationRepository repository;

    public SynthesisEvaluationService() {
        this.repository = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SynthesisEvaluationService(SynthesisEvaluationRepository repository) {
        this.repository = repository;
    }

    public SynthesisEvaluation record(CompositeAnalysis composite, SynthesisOutput output) {
        var evaluation = new SynthesisEvaluation(
            composite.symbol(), composite.date(), output.getRecommendation(),
            output.getConfidence() == null ? 0.0 : output.getConfidence(),
            output.isConflictDetected(), output.isEventRiskDetected(), output.getEventRiskReason(),
            Instant.now(), null);
        if (evaluations.size() >= MAX_RECORDS) {
            evaluations.keySet().stream().findAny().ifPresent(evaluations::remove);
        }
        Key key = new Key(evaluation.symbol(), evaluation.analysisDate());
        evaluations.put(key, evaluation);
        persist(evaluation);
        return evaluation;
    }

    public SynthesisEvaluation recordOutcome(String symbol, java.time.LocalDate analysisDate,
                                             int horizonDays, BigDecimal forwardReturnPct) {
        var key = new Key(symbol, analysisDate);
        var existing = find(symbol, analysisDate);
        if (existing == null) return null;
        boolean correct = recommendationCorrect(existing.recommendation(), forwardReturnPct);
        var measured = new SynthesisEvaluation.OutcomeMeasurement(
            horizonDays, forwardReturnPct, correct, Instant.now());
        var updated = existing.withOutcome(measured);
        evaluations.put(key, updated);
        persist(updated);
        return updated;
    }

    public SynthesisEvaluation find(String symbol, java.time.LocalDate analysisDate) {
        Key key = new Key(symbol, analysisDate);
        SynthesisEvaluation cached = evaluations.get(key);
        if (cached != null || repository == null) return cached;
        return repository.findBySymbolAndAnalysisDate(symbol, analysisDate)
                .map(SynthesisEvaluationEntity::toDomain)
                .map(value -> { evaluations.put(key, value); return value; })
                .orElse(null);
    }

    private void persist(SynthesisEvaluation evaluation) {
        if (repository == null) return;
        repository.findBySymbolAndAnalysisDate(evaluation.symbol(), evaluation.analysisDate())
                .ifPresentOrElse(entity -> {
                    entity.applyOutcome(evaluation.outcome());
                    repository.save(entity);
                }, () -> repository.save(new SynthesisEvaluationEntity(evaluation)));
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
