package com.swingtrade.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents structured output for sentiment analysis with POSITIVE/NEUTRAL/NEGATIVE classification.
 * Used with Spring AI BeanOutputConverter for structured output parsing.
 */
public class SentimentOutput {
    @JsonProperty("score")
    private SentimentType sentiment;
    @JsonProperty("summary")
    private String reasoning;
    private Double confidence;
    @JsonProperty("red_flags")
    private List<String> redFlags = new ArrayList<>();
    @JsonProperty("catalysts")
    private List<String> catalysts = new ArrayList<>();

    public SentimentOutput(SentimentType sentiment, String reasoning, Double confidence) {
        this(sentiment, reasoning, confidence, List.of(), List.of());
    }

    public SentimentOutput(SentimentType sentiment, String reasoning, Double confidence,
                                   List<String> redFlags, List<String> catalysts) {
        this.sentiment = sentiment;
        this.reasoning = reasoning;
        this.confidence = confidence;
        this.redFlags = redFlags != null ? redFlags : List.of();
        this.catalysts = catalysts != null ? catalysts : List.of();
    }

    public SentimentType getSentiment() {
        return sentiment;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Double getConfidence() {
        return confidence;
    }

    public List<String> getRedFlags() {
        return redFlags;
    }

    public List<String> getCatalysts() {
        return catalysts;
    }

    @Override
    public String toString() {
        return "SentimentOutput{" +
                "sentiment=" + sentiment +
                ", reasoning='" + reasoning + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}