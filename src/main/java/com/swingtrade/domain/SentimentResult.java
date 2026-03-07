package com.swingtrade.domain;

import java.time.LocalDate;

/**
 * Represents the sentiment analysis result for a stock on a specific date.
 * This record captures the sentiment score and summary for a stock based on various factors.
 *
 * @param symbol    the stock symbol (e.g., "AAPL")
 * @param date      the date of the sentiment analysis
 * @param score     the sentiment score (POSITIVE, NEUTRAL, NEGATIVE)
 * @param summary   a textual summary of the sentiment analysis
 */
public record SentimentResult(
    String symbol,
    LocalDate date,
    SentimentScore score,
    String summary
) {
    /**
     * Enum representing the different sentiment scores.
     */
    public enum SentimentScore {
        POSITIVE,
        NEUTRAL,
        NEGATIVE
    }
}
