package com.swingtrade.api.dto;

import java.time.LocalDate;

/**
 * DTO for sentiment analysis responses.
 */
public class SentimentAnalysisResponse {

    private String symbol;
    private LocalDate analyzedAt;
    private SentimentScore score;
    private String summary;

    public SentimentAnalysisResponse() {
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDate analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public SentimentScore getScore() {
        return score;
    }

    public void setScore(SentimentScore score) {
        this.score = score;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * Sentiment score enumeration.
     */
    public enum SentimentScore {
        POSITIVE, NEUTRAL, NEGATIVE
    }
}
