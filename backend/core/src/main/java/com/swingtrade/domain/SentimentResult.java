package com.swingtrade.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents the sentiment analysis result for a stock on a specific date.
 * This record captures the sentiment score and summary for a stock based on
 * analysis of news articles, corporate announcements, and market sentiment.
 *
 * @param id          the unique identifier for the sentiment result (database auto-generated)
 * @param symbol      the stock symbol (e.g., "RELIANCE", "TCS")
 * @param date        the date of the sentiment analysis
 * @param score       the sentiment score (POSITIVE, NEUTRAL, NEGATIVE)
 * @param summary     a textual summary of the sentiment analysis
 * @param rawContent  the raw content analyzed (news articles, announcements)
 * @param confidence  the confidence level of the sentiment score (0.0 to 1.0)
 * @param analyzedAt  the timestamp when the sentiment was analyzed
 * @param source      provenance: LLM, KEYWORD, or DEFAULT
 */
public record SentimentResult(
    Long id,
    String symbol,
    LocalDate date,
    SentimentScore score,
    String summary,
    String rawContent,
    Double confidence,
    LocalDate analyzedAt,
    List<String> redFlags,
    List<String> catalysts,
    String promptHash,
    String modelVersion,
    int articleCount,
    String source
) {
    /** Compatibility constructor for callers predating provenance tracking. */
    public SentimentResult(Long id, String symbol, LocalDate date, SentimentScore score,
                           String summary, String rawContent, Double confidence,
                           LocalDate analyzedAt, List<String> redFlags, List<String> catalysts,
                           String promptHash, String modelVersion, int articleCount) {
        this(id, symbol, date, score, summary, rawContent, confidence, analyzedAt,
            redFlags, catalysts, promptHash, modelVersion, articleCount, "DEFAULT");
    }
    /**
     * Enum representing the different sentiment scores.
     */
    public enum SentimentScore {
        POSITIVE("Positive"),
        NEUTRAL("Neutral"),
        NEGATIVE("Negative"),
        UNKNOWN("Unknown");

        private final String displayName;

        SentimentScore(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Creates a new SentimentResult with normalized confidence.
     *
     * @param symbol the stock symbol
     * @param date the analysis date
     * @param score the sentiment score
     * @param summary the summary
     * @param rawContent the raw content analyzed
     * @param confidence the confidence level (will be clamped to 0-1)
     * @return a new SentimentResult instance
     */
    public static SentimentResult create(
        String symbol,
        LocalDate date,
        SentimentScore score,
        String summary,
        String rawContent,
        Double confidence
    ) {
        return create(symbol, date, score, summary, rawContent, confidence, List.of(), List.of());
    }

    public static SentimentResult create(
        String symbol,
        LocalDate date,
        SentimentScore score,
        String summary,
        String rawContent,
        Double confidence,
        List<String> redFlags,
        List<String> catalysts
    ) {
        Double normalizedConfidence = confidence != null ?
            Math.max(0.0, Math.min(1.0, confidence)) : null;

        return new SentimentResult(
            null,
            symbol,
            date,
            score,
            summary,
            rawContent,
            normalizedConfidence,
            LocalDate.now(),
            redFlags != null ? redFlags : List.of(),
            catalysts != null ? catalysts : List.of(),
            null,
            null,
            0,
            "DEFAULT"
        );
    }

    /**
     * Returns true if the sentiment is positive.
     *
     * @return true if POSITIVE
     */
    public boolean isPositive() {
        return SentimentScore.POSITIVE == score;
    }

    /**
     * Returns true if the sentiment is neutral.
     *
     * @return true if NEUTRAL
     */
    public boolean isNeutral() {
        return SentimentScore.NEUTRAL == score;
    }

    /**
     * Returns true if the sentiment is negative.
     *
     * @return true if NEGATIVE
     */
    public boolean isNegative() {
        return SentimentScore.NEGATIVE == score;
    }

    public boolean isUnknown() {
        return SentimentScore.UNKNOWN == score;
    }

    /**
     * Returns true if the sentiment supports a long position entry.
     *
     * @return true if POSITIVE or NEUTRAL
     */
    public boolean supportsEntry() {
        return isPositive() || isNeutral();
    }
}
