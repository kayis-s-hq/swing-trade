package com.swingtrade.data.service;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
