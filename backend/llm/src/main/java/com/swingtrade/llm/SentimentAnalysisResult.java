package com.swingtrade.llm;

/**
 * Represents structured output for sentiment analysis with POSITIVE/NEUTRAL/NEGATIVE classification.
 */
public class SentimentAnalysisResult {
    private SentimentType sentiment;
    private String reasoning;
    private Double confidence;

    /**
     * Creates a new sentiment analysis result.
     * 
     * @param sentiment The sentiment classification (POSITIVE/NEUTRAL/NEGATIVE)
     * @param reasoning The reasoning behind the sentiment classification
     * @param confidence Confidence score (0.0-1.0)
     */
    public SentimentAnalysisResult(SentimentType sentiment, String reasoning, Double confidence) {
        this.sentiment = sentiment;
        this.reasoning = reasoning;
        this.confidence = confidence;
    }

    /**
     * Gets the sentiment classification.
     * 
     * @return The sentiment type
     */
    public SentimentType getSentiment() {
        return sentiment;
    }

    /**
     * Gets the reasoning behind the sentiment classification.
     * 
     * @return The reasoning text
     */
    public String getReasoning() {
        return reasoning;
    }

    /**
     * Gets the confidence score.
     * 
     * @return Confidence value between 0.0 and 1.0
     */
    public Double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "SentimentAnalysisResult{" +
                "sentiment=" + sentiment +
                ", reasoning='" + reasoning + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
