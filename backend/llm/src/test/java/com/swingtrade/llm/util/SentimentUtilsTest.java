package com.swingtrade.llm.util;

import com.swingtrade.llm.SentimentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentimentUtilsTest {

    @Test
    void exposesAndParsesAllSentimentTypes() {
        assertThat(SentimentUtils.getAllSentimentTypes()).containsExactly(SentimentType.values());
        assertThat(SentimentUtils.fromString("positive")).isEqualTo(SentimentType.POSITIVE);
        assertThat(SentimentUtils.fromString("not-a-sentiment")).isEqualTo(SentimentType.NEUTRAL);
        assertThat(SentimentUtils.getDescription(SentimentType.POSITIVE))
                .isEqualTo("Bullish market conditions");
        assertThat(SentimentUtils.getDescription(SentimentType.NEUTRAL))
                .isEqualTo("Stable or mixed market conditions");
        assertThat(SentimentUtils.getDescription(SentimentType.NEGATIVE))
                .isEqualTo("Bearish market conditions");
        assertThat(SentimentUtils.getDescription(SentimentType.UNKNOWN))
                .isEqualTo("Unknown sentiment");
    }
}
