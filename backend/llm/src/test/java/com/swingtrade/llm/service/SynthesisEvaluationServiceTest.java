package com.swingtrade.llm.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.llm.SynthesisEvaluation;
import com.swingtrade.llm.SynthesisEvaluationEntity;
import com.swingtrade.llm.SynthesisEvaluationRepository;
import com.swingtrade.llm.SynthesisOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    void evaluatesOnlyCompletePersistedForwardWindows() {
        SynthesisEvaluationRepository repository = mock(SynthesisEvaluationRepository.class);
        CandleStore candleStore = mock(CandleStore.class);
        SynthesisEvaluationService service = new SynthesisEvaluationService(repository);
        var evaluation = service.record(composite(), output("BUY"));
        SynthesisEvaluationEntity entity = new SynthesisEvaluationEntity(evaluation);
        when(repository.findByOutcomeMeasuredAtIsNullAndAnalysisDateBeforeOrderByAnalysisDateAsc(
                LocalDate.of(2026, 8, 25))).thenReturn(List.of(entity));
        OhlcvCandle entry = candle("RELIANCE", LocalDate.of(2026, 8, 17), "100");
        OhlcvCandle exit = candle("RELIANCE", LocalDate.of(2026, 8, 24), "105");
        when(candleStore.findFirstBySymbolAndDateAfterOrderByDateAsc("RELIANCE", evaluation.analysisDate()))
                .thenReturn(java.util.Optional.of(entry));
        when(candleStore.findNthBySymbolAndDateAfterOrderByDateAsc("RELIANCE", entry.date(), 5))
                .thenReturn(java.util.Optional.of(exit));
        when(repository.findBySymbolAndAnalysisDate("RELIANCE", evaluation.analysisDate()))
                .thenReturn(java.util.Optional.of(entity));

        assertThat(service.evaluatePending(candleStore, LocalDate.of(2026, 8, 25), 5)).isEqualTo(1);
        assertThat(entity.toDomain().outcome().forwardReturnPct()).isEqualByComparingTo("5.000000");
        verify(repository).save(entity);
    }

    @Test
    void summarizesMeasuredOutcomesByRecommendation() {
        SynthesisEvaluationService service = new SynthesisEvaluationService();
        service.record(composite(), output("BUY"));
        service.recordOutcome("RELIANCE", composite().date(), 5, new BigDecimal("3.25"));

        var summary = service.summary();

        assertThat(summary.totalEvaluations()).isEqualTo(1);
        assertThat(summary.measuredEvaluations()).isEqualTo(1);
        assertThat(summary.correctEvaluations()).isEqualTo(1);
        assertThat(summary.accuracyPct()).isEqualTo(100.0);
        assertThat(summary.measuredByRecommendation()).containsEntry("BUY", 1L);
    }

    private SynthesisOutput output(String recommendation) {
        var output = new SynthesisOutput();
        output.setRecommendation(recommendation);
        output.setConfidence(0.8);
        return output;
    }

    private OhlcvCandle candle(String symbol, LocalDate date, String close) {
        return OhlcvCandle.of(symbol, date, new BigDecimal(close), new BigDecimal(close),
                new BigDecimal(close), new BigDecimal(close), 100L);
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
