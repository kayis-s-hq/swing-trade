package com.swingtrade.data.service;

import com.swingtrade.data.entity.SentimentAccuracyEntity;
import com.swingtrade.data.repository.SentimentAccuracyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SentimentAccuracyServiceTest {

    @Test
    void spearmanIcUsesAverageRanksForTiedSentimentScores() {
        double ic = SentimentAccuracyService.computeSpearmanIC(
            List.of(1.0, 1.0, 0.0, 0.0),
            List.of(4.0, 3.0, 2.0, 1.0));

        assertThat(ic).isCloseTo(0.89442719, within(0.000001));
    }

    @Test
    void accuracyWindowUsesTheReturnForThatWindow() {
        SentimentAccuracyEntity first = accuracy("POSITIVE", "0.010", "0.100", "0.300");
        SentimentAccuracyEntity second = accuracy("NEGATIVE", "-0.020", "-0.200", "-0.400");
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.findAll()).thenReturn(List.of(first, second));

        List<SentimentAccuracyService.AccuracyByWindow> result =
            new SentimentAccuracyService(repository).getAccuracyByWindow();

        assertThat(result).extracting(SentimentAccuracyService.AccuracyByWindow::window)
            .containsExactly("1-day", "5-day", "21-day");
        assertThat(result.get(0).avgReturn()).isCloseTo(-0.005, within(0.000001));
        assertThat(result.get(1).avgReturn()).isCloseTo(-0.05, within(0.000001));
        assertThat(result.get(2).avgReturn()).isCloseTo(-0.05, within(0.000001));
    }

    @Test
    void fallbackSentimentIsExcludedFromPredictiveWindows() {
        SentimentAccuracyEntity llm = accuracy("POSITIVE", "0.010", "0.100", "0.300");
        llm.setSentimentSource("LLM");
        SentimentAccuracyEntity keyword = accuracy("NEGATIVE", "-0.020", "-0.200", "-0.400");
        keyword.setSentimentSource("KEYWORD");
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.findAll()).thenReturn(List.of(llm, keyword));

        List<SentimentAccuracyService.AccuracyByWindow> result =
            new SentimentAccuracyService(repository).getAccuracyByWindow();

        assertThat(result).allSatisfy(window -> assertThat(window.total()).isEqualTo(1));
        assertThat(result.get(0).avgReturn()).isCloseTo(0.010, within(0.000001));
    }

    @Test
    void accuracyStatsCountsScorableRowsBySentimentAndSymbol() {
        SentimentAccuracyEntity correct = accuracy("POSITIVE", "0.010", null, null);
        correct.setSymbol("TCS");
        correct.setWasCorrect(true);
        SentimentAccuracyEntity incorrect = accuracy("NEGATIVE", "-0.010", null, null);
        incorrect.setSymbol("INFY");
        incorrect.setWasCorrect(false);
        SentimentAccuracyEntity fallback = accuracy("NEUTRAL", "0.001", null, null);
        fallback.setSymbol("TCS");
        fallback.setSentimentSource("KEYWORD");

        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.findAll()).thenReturn(List.of(correct, incorrect, fallback));

        SentimentAccuracyService.AccuracyStats stats = new SentimentAccuracyService(repository).getAccuracyStats();

        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.correct()).isEqualTo(1);
        assertThat(stats.accuracyPct()).isEqualTo(.5);
        assertThat(stats.bySentiment()).containsEntry("POSITIVE", 1).containsEntry("NEGATIVE", 1);
        assertThat(stats.bySymbol()).containsEntry("TCS", 1).containsEntry("INFY", 1);
    }

    @Test
    void aggregateQueriesMapRowsAndRoundMetrics() {
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.accuracyByRegime()).thenReturn(List.<Object[]>of(
                new Object[] {"BULL", 10L, .7567, .8123}));
        when(repository.accuracyBySymbol()).thenReturn(List.<Object[]>of(
                new Object[] {"TCS", 8L, .6255, .7444}));
        when(repository.calibrationData()).thenReturn(List.<Object[]>of(
                new Object[] {0.8, .75, .82, 10L}));

        SentimentAccuracyService service = new SentimentAccuracyService(repository);

        assertThat(service.getAccuracyByRegime()).containsExactly(
                new SentimentAccuracyService.AccuracyByRegime("BULL", 10, 75.67, 81.23));
        assertThat(service.getAccuracyBySymbol()).containsExactly(
                new SentimentAccuracyService.AccuracyBySymbol("TCS", 8, 62.55, 74.44));
        assertThat(service.getCalibrationData()).containsExactly(
                new SentimentAccuracyService.CalibrationData(.8, .82, .75, 7.0, 10));
        assertThat(service.getECEStats()).isEqualTo(new SentimentAccuracyService.ECEStats(.07, 1));
    }

    @Test
    void rollingIcRequiresThreePairsAndReturnsTheCurrentDate() {
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.scoreReturnPairsSince(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[] {new BigDecimal("1"), new BigDecimal("3")},
                        new Object[] {new BigDecimal("2"), new BigDecimal("2")},
                        new Object[] {new BigDecimal("3"), new BigDecimal("1")}));

        List<SentimentAccuracyService.RollingICResult> result =
                new SentimentAccuracyService(repository).getRollingIC(30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(result.get(0).spearmanIC()).isEqualTo(-1.0);
    }

    @Test
    void volumeAndDirectionalStatsDelegateToRepository() {
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.countSince(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(3L, 14L, 50L);
        when(repository.countDirectionalSince(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(9L);
        when(repository.countCorrectDirectionalSince(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(7L);

        SentimentAccuracyService service = new SentimentAccuracyService(repository);

        assertThat(service.getSignalVolumeStats()).isEqualTo(
                new SentimentAccuracyService.SignalVolumeStats(3, 14, 2.0, 1.67));
        assertThat(service.getDirectionalCount()).isEqualTo(9L);
        assertThat(service.getDirectionalCorrectCount()).isEqualTo(7L);
    }

    @Test
    void averageConfidenceExcludesNullValuesButUsesScorablePopulation() {
        SentimentAccuracyEntity first = accuracy("POSITIVE", null, null, null);
        first.setLlmConfidence(.8f);
        SentimentAccuracyEntity second = accuracy("NEGATIVE", null, null, null);
        second.setLlmConfidence(.6f);
        SentimentAccuracyEntity noConfidence = accuracy("NEUTRAL", null, null, null);
        noConfidence.setLlmConfidence(null);
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.findAll()).thenReturn(List.of(first, second, noConfidence));

        assertThat(new SentimentAccuracyService(repository).getAvgConfidence()).isCloseTo(.4667, within(.0001));
    }

    @Test
    void rollingIcReturnsEmptyForInsufficientDataAndStatsHandleEmptyData() {
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.scoreReturnPairsSince(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(repository.accuracyByRegime()).thenReturn(List.of());
        when(repository.accuracyBySymbol()).thenReturn(List.of());
        when(repository.calibrationData()).thenReturn(List.of());
        when(repository.countSince(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(0L);

        SentimentAccuracyService service = new SentimentAccuracyService(repository);

        assertThat(service.getRollingIC(30)).isEmpty();
        assertThat(service.getAccuracyStats().total()).isZero();
        assertThat(service.getAccuracyByWindow()).isEmpty();
        assertThat(service.getECEStats()).isEqualTo(new SentimentAccuracyService.ECEStats(0.0, 0));
        assertThat(service.getAvgConfidence()).isZero();
    }

    private static SentimentAccuracyEntity accuracy(String score, String oneDay, String fiveDay,
                                                     String twentyOneDay) {
        SentimentAccuracyEntity entity = new SentimentAccuracyEntity();
        entity.setLlmScore(score);
        entity.setActualReturn1d(oneDay == null ? null : new BigDecimal(oneDay));
        entity.setActualReturn5d(fiveDay == null ? null : new BigDecimal(fiveDay));
        entity.setActualReturn21d(twentyOneDay == null ? null : new BigDecimal(twentyOneDay));
        return entity;
    }
}
