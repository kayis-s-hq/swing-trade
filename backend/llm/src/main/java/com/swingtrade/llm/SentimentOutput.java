package com.swingtrade.llm;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String source;
    /** Why a non-LLM fallback produced this output; excluded from the model-facing schema. */
    @JsonIgnore
    private String degradedReason;

    /**
     * No-arg constructor required by Jackson/Spring AI's BeanOutputConverter.
     * Without it every structured-output parse failed with "no Creators, like
     * default constructor, exist" and fell through to the manual JSON-extraction
     * fallback on each sentiment call.
     */
    public SentimentOutput() {
    }

    public SentimentOutput(SentimentType sentiment, String reasoning, Double confidence) {
        this(sentiment, reasoning, confidence, List.of(), List.of(), "LLM");
    }

    public SentimentOutput(SentimentType sentiment, String reasoning, Double confidence,
                                   List<String> redFlags, List<String> catalysts) {
        this(sentiment, reasoning, confidence, redFlags, catalysts, "LLM");
    }

    public SentimentOutput(SentimentType sentiment, String reasoning, Double confidence,
                           List<String> redFlags, List<String> catalysts, String source) {
        this.sentiment = sentiment;
        this.reasoning = reasoning;
        this.confidence = confidence;
        this.redFlags = redFlags != null ? redFlags : List.of();
        this.catalysts = catalysts != null ? catalysts : List.of();
        this.source = source != null && !source.isBlank() ? source : "DEFAULT";
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

    // Setters exist for Jackson/BeanOutputConverter population. The @JsonProperty
    // names on the fields ("score", "summary", "red_flags") are what the model
    // actually emits, so binding happens through those rather than the Java names.

    public void setSentiment(SentimentType sentiment) {
        this.sentiment = sentiment;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public void setRedFlags(List<String> redFlags) {
        this.redFlags = redFlags != null ? redFlags : List.of();
    }

    public void setCatalysts(List<String> catalysts) {
        this.catalysts = catalysts != null ? catalysts : List.of();
    }

    public String getSource() { return source; }

    @JsonIgnore
    public String getDegradedReason() {
        return degradedReason;
    }

    @JsonIgnore
    public void setDegradedReason(String degradedReason) {
        this.degradedReason = degradedReason;
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
