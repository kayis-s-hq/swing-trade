package com.swingtrade.llm.service;

import com.swingtrade.llm.SentimentOutput;
import com.swingtrade.llm.SentimentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentimentOutputValidatorTest {

    @Test
    void keepsItemsThatCiteAvailableArticles() {
        SentimentOutput output = output(List.of("[1] SEBI probe", "[2] [3] Earnings miss"),
                List.of("[2] New contract"));

        SentimentOutput validated = SentimentOutputValidator.validate(output, 3);

        assertThat(validated.getRedFlags()).containsExactlyElementsOf(output.getRedFlags());
        assertThat(validated.getCatalysts()).containsExactly("[2] New contract");
    }

    @Test
    void rejectsItemsWithoutCitationsOrWithOutOfRangeCitations() {
        SentimentOutput output = output(List.of("SEBI probe", "[0] invalid", "[3] unavailable"),
                List.of("new contract [99]", "[1] supported"));

        SentimentOutput validated = SentimentOutputValidator.validate(output, 2);

        assertThat(validated.getRedFlags()).isEmpty();
        assertThat(validated.getCatalysts()).containsExactly("[1] supported");
    }

    @Test
    void parseResponseWithArticleCountAppliesGrounding() {
        SentimentAnalyzer analyzer = new SentimentAnalyzer(new tools.jackson.databind.ObjectMapper());

        SentimentOutput result = analyzer.parseResponse("""
                {"score":"NEGATIVE","confidence":0.8,"summary":"Risk",
                 "red_flags":["[1] supported", "unsupported risk"],
                 "catalysts":["[4] unavailable"]}
                """, 1);

        assertThat(result.getRedFlags()).containsExactly("[1] supported");
        assertThat(result.getCatalysts()).isEmpty();
    }

    private static SentimentOutput output(List<String> redFlags, List<String> catalysts) {
        return new SentimentOutput(SentimentType.NEUTRAL, "summary", 0.5, redFlags, catalysts);
    }
}
