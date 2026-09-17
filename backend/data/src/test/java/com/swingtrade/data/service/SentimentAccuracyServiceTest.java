package com.swingtrade.data.service;

import com.swingtrade.data.entity.SentimentAccuracyEntity;
import com.swingtrade.data.repository.SentimentAccuracyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    private static SentimentAccuracyEntity accuracy(String score, String oneDay, String fiveDay,
                                                     String twentyOneDay) {
        SentimentAccuracyEntity entity = new SentimentAccuracyEntity();
        entity.setLlmScore(score);
        entity.setActualReturn1d(new BigDecimal(oneDay));
        entity.setActualReturn5d(new BigDecimal(fiveDay));
        entity.setActualReturn21d(new BigDecimal(twentyOneDay));
        return entity;
    }
}
