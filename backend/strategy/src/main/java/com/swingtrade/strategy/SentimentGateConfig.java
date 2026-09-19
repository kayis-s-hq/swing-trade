package com.swingtrade.strategy;

import com.swingtrade.domain.SentimentResult;

/**
 * Config for the {@code sentimentGate} overlay (plan §5.5): blocks a BUY when the most recent
 * sentiment for the symbol is below {@code minScore} or older than {@code maxAgeDays}.
 *
 * <p>Uses the existing {@link SentimentResult.SentimentScore} model (NEGATIVE &lt; NEUTRAL/UNKNOWN
 * &lt; POSITIVE, see {@link SentimentResult#supportsEntry()}) rather than inventing a new scale,
 * per the plan's instruction to reuse the real sentiment model.
 *
 * @param minScore   minimum acceptable {@link SentimentResult.SentimentScore}, ordered
 *                    NEGATIVE &lt; NEUTRAL/UNKNOWN &lt; POSITIVE (default NEUTRAL)
 * @param maxAgeDays  maximum age (in calendar days, relative to the signal date) of the sentiment
 *                    result for it to be considered fresh (default 3)
 */
public record SentimentGateConfig(SentimentResult.SentimentScore minScore, int maxAgeDays) {

    public SentimentGateConfig {
        if (minScore == null) {
            minScore = SentimentResult.SentimentScore.NEUTRAL;
        }
        if (maxAgeDays <= 0) {
            maxAgeDays = 3;
        }
    }

    public static SentimentGateConfig defaults() {
        return new SentimentGateConfig(SentimentResult.SentimentScore.NEUTRAL, 3);
    }

    /** Ordinal rank used to compare scores against {@link #minScore()}: NEGATIVE &lt; NEUTRAL/UNKNOWN &lt; POSITIVE. */
    static int rank(SentimentResult.SentimentScore score) {
        return switch (score) {
            case NEGATIVE -> 0;
            case NEUTRAL, UNKNOWN -> 1;
            case POSITIVE -> 2;
        };
    }
}
