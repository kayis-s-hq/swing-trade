package com.swingtrade.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Durable persistence projection for a synthesis decision and its later outcome. */
@Entity
@Table(name = "synthesis_evaluations", indexes = {
        @Index(name = "idx_synthesis_evaluation_date", columnList = "analysis_date"),
        @Index(name = "idx_synthesis_evaluation_outcome", columnList = "outcome_measured_at")
})
public class SynthesisEvaluationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(length = 10)
    private String recommendation;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "conflict_detected", nullable = false)
    private boolean conflictDetected;

    @Column(name = "event_risk_detected", nullable = false)
    private boolean eventRiskDetected;

    @Column(name = "event_risk_reason", length = 500)
    private String eventRiskReason;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "outcome_horizon_days")
    private Integer outcomeHorizonDays;

    @Column(name = "outcome_forward_return_pct", precision = 12, scale = 6)
    private BigDecimal outcomeForwardReturnPct;

    @Column(name = "outcome_correct")
    private Boolean outcomeCorrect;

    @Column(name = "outcome_measured_at")
    private Instant outcomeMeasuredAt;

    protected SynthesisEvaluationEntity() { }

    public SynthesisEvaluationEntity(SynthesisEvaluation evaluation) {
        this.symbol = evaluation.symbol();
        this.analysisDate = evaluation.analysisDate();
        this.recommendation = evaluation.recommendation();
        this.confidence = evaluation.confidence();
        this.conflictDetected = evaluation.conflictDetected();
        this.eventRiskDetected = evaluation.eventRiskDetected();
        this.eventRiskReason = evaluation.eventRiskReason();
        this.recordedAt = evaluation.recordedAt();
        applyOutcomeFields(evaluation.outcome());
    }

    public void applyOutcome(SynthesisEvaluation.OutcomeMeasurement outcome) {
        applyOutcomeFields(outcome);
    }

    private void applyOutcomeFields(SynthesisEvaluation.OutcomeMeasurement outcome) {
        if (outcome == null) {
            outcomeHorizonDays = null;
            outcomeForwardReturnPct = null;
            outcomeCorrect = null;
            outcomeMeasuredAt = null;
        } else {
            outcomeHorizonDays = outcome.horizonDays();
            outcomeForwardReturnPct = outcome.forwardReturnPct();
            outcomeCorrect = outcome.recommendationCorrect();
            outcomeMeasuredAt = outcome.measuredAt();
        }
    }

    public SynthesisEvaluation toDomain() {
        SynthesisEvaluation.OutcomeMeasurement outcome = outcomeHorizonDays == null
                ? null
                : new SynthesisEvaluation.OutcomeMeasurement(outcomeHorizonDays, outcomeForwardReturnPct,
                        Boolean.TRUE.equals(outcomeCorrect), outcomeMeasuredAt);
        return new SynthesisEvaluation(symbol, analysisDate, recommendation, confidence,
                conflictDetected, eventRiskDetected, eventRiskReason, recordedAt, outcome);
    }

    public String getSymbol() { return symbol; }
    public LocalDate getAnalysisDate() { return analysisDate; }
}
