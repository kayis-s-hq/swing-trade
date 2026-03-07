package com.swingtrade.llm.util;

import com.swingtrade.llm.SentimentType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for sentiment-related operations.
 */
public class SentimentUtils {
    
    /**
     * Gets all sentiment types.
     * 
     * @return List of all sentiment types
     */
    public static List<SentimentType> getAllSentimentTypes() {
        return Arrays.asList(SentimentType.values());
    }
    
    /**
     * Converts string representation to sentiment type.
     * 
     * @param sentimentString The string representation
     * @return Corresponding sentiment type or NEUTRAL if invalid
     */
    public static SentimentType fromString(String sentimentString) {
        try {
            return SentimentType.valueOf(sentimentString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SentimentType.NEUTRAL;
        }
    }
    
    /**
     * Gets sentiment type description.
     * 
     * @param sentiment The sentiment type
     * @return Human-readable description
     */
    public static String getDescription(SentimentType sentiment) {
        switch (sentiment) {
            case POSITIVE: return "Bullish market conditions";
            case NEUTRAL: return "Stable or mixed market conditions"; 
            case NEGATIVE: return "Bearish market conditions";
            default: return "Unknown sentiment";
        }
    }
}
