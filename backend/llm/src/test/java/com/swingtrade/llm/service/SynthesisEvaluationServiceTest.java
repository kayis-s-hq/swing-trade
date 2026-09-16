package com.swingtrade.llm.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.llm.SynthesisEvaluation;
import com.swingtrade.llm.SynthesisOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SynthesisEvaluationServiceTest {

    @Test
    void recordsDecisionMetadataWithoutChangingTheSynthesisOutputContract() {
        var service = new SynthesisEvaluationService();
        var composite = composite();
        var output = new SynthesisOutput();
        output.setRecommendation("BUY");
        output.setConfidence(0.8);
        output.setConflictDetected(true);
        output.setEventRiskDetected(true);
        output.setEventRiskReason("results during holding window");

        var evaluation = service.record(composite, output);

        assertThat(evaluation.symbol()).isEqualTo("RELIANCE");
        assertThat(evaluation.analysisDate()).isEqualTo(composite.date());
        assertThat(evaluation.conflictDetected()).isTrue();
        assertThat(evaluation.eventRiskDetected()).isTrue();
        assertThat(service.find("RELIANCE", composite.date())).isEqualTo(evaluation);
    }

    @Test
    void attachesABoundedForwardOutcomeAndMeasuresRecommendationDirection() {
        var service = new SynthesisEvaluationService();
        var date = composite().date();
        var output = new SynthesisOutput();
        output.setRecommendation("BUY");
        service.record(composite(), output);

        var measured = service.recordOutcome("RELIANCE", date, 5, new BigDecimal("3.25"));

        assertThat(measured.outcome()).isNotNull();
        assertThat(measured.outcome().horizonDays()).isEqualTo(5);
        assertThat(measured.outcome().forwardReturnPct()).isEqualByComparingTo("3.25");
        assertThat(measured.outcome().recommendationCorrect()).isTrue();
    }

    @Test
    void rejectsOutcomeOutsideTheSupportedHoldingWindow() {
        var service = new SynthesisEvaluationService();
        var date = composite().date();
        var output = new SynthesisOutput();
        output.setRecommendation("HOLD");
        service.record(composite(), output);

        assertThatThrownBy(() -> service.recordOutcome("RELIANCE", date, 21, BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private CompositeAnalysis composite() {
        return new CompositeAnalysis("RELIANCE", LocalDate.of(2026, 8, 15), 72, "BUY",
            BigDecimal.valueOf(0.75), List.of(),
            new CompositeAnalysis.NewsScore(65, "summary", List.of(), List.of(), 1),
            new CompositeAnalysis.TechnicalScore(78, "BUY", 0.8, List.of()),
            new CompositeAnalysis.FundamentalScore(70, List.of()),
            new CompositeAnalysis.BacktestScore(12, 0.62, 1.8, 12.5, 28.3, 6.3, true),
            "reasoning", null);
    }
}
