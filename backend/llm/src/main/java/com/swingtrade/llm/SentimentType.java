package com.swingtrade.llm;

/**
 * Enum representing sentiment types for structured output.
 */
public enum SentimentType {
    /**
     * Positive sentiment indicating bullish market conditions.
     */
    POSITIVE,
    
    /**
     * Neutral sentiment indicating stable or mixed market conditions.
     */
    NEUTRAL,
    
    /**
     * Negative sentiment indicating bearish market conditions.
     */
    NEGATIVE,

    /** The response could not be classified reliably. */
    UNKNOWN
}
