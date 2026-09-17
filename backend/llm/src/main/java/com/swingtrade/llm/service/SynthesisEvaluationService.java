package com.swingtrade.llm.service;

import com.swingtrade.llm.SynthesisEvaluation;
import com.swingtrade.llm.SynthesisOutput;
import com.swingtrade.llm.SynthesisEvaluationEntity;
import com.swingtrade.llm.SynthesisEvaluationRepository;
import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * Measures persisted decisions once their complete forward window exists.
     * Missing or unusable candles are skipped and remain eligible for a later run.
     * The return is expressed in percentage points, matching {@code forwardReturnPct}.
     */
    public int evaluatePending(CandleStore candleStore, LocalDate asOfDate, int horizonDays) {
        if (repository == null || candleStore == null || asOfDate == null
                || horizonDays < 1 || horizonDays > 20) {
            return 0;
        }
        int processed = 0;
        for (SynthesisEvaluationEntity entity : repository
                .findByOutcomeMeasuredAtIsNullAndAnalysisDateBeforeOrderByAnalysisDateAsc(asOfDate)) {
            OhlcvCandle entry = candleStore
                    .findFirstBySymbolAndDateAfterOrderByDateAsc(entity.getSymbol(), entity.getAnalysisDate())
                    .orElse(null);
            if (entry == null || entry.close() == null || entry.close().signum() <= 0) continue;
            OhlcvCandle exit = candleStore
                    .findNthBySymbolAndDateAfterOrderByDateAsc(entity.getSymbol(), entry.date(), horizonDays)
                    .orElse(null);
            if (exit == null || exit.date().isAfter(asOfDate)
                    || exit.close() == null || exit.close().signum() <= 0) continue;

            BigDecimal forwardReturnPct = exit.close().subtract(entry.close())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(entry.close(), 6, RoundingMode.HALF_UP);
            if (recordOutcome(entity.getSymbol(), entity.getAnalysisDate(), horizonDays,
                    forwardReturnPct) != null) {
                processed++;
            }
        }
        return processed;
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
