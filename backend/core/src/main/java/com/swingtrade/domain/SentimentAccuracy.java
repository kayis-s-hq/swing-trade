package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents the accuracy of an LLM sentiment prediction against actual market returns.
 *
 * @param id              the unique identifier
 * @param symbol          the stock symbol
 * @param analysisDate    the date the sentiment was analyzed
 * @param llmScore        the LLM sentiment score (POSITIVE, NEGATIVE, NEUTRAL)
 * @param llmConfidence   the LLM confidence score (0.0-1.0)
 * @param numericScore    numeric representation of LLM score (-0.5 to 0.5)
 * @param actualReturn1d  actual market return over 1-day window
 * @param actualReturn5d  actual market return over 5-day window
 * @param actualReturn21d actual market return over 21-day window
 * @param groundTruthLabel ground truth label (UP, DOWN, FLAT)
 * @param wasCorrect      whether the LLM prediction was correct
 * @param pnlPct          actual P&L percentage
 * @param marketRegime    market regime at analysis time (BULL, BEAR, NEUTRAL)
 * @param promptHash      hash of the prompt used for sentiment analysis
 * @param modelVersion    version of the LLM model used
 * @param evaluatedAt     timestamp when accuracy was evaluated
 */
public record SentimentAccuracy(
    Long id,
    String symbol,
    LocalDate analysisDate,
    String llmScore,
    Float llmConfidence,
    Float numericScore,
    BigDecimal actualReturn1d,
    BigDecimal actualReturn5d,
    BigDecimal actualReturn21d,
    String groundTruthLabel,
    Boolean wasCorrect,
    BigDecimal pnlPct,
    String marketRegime,
    String promptHash,
    String modelVersion,
    LocalDateTime evaluatedAt
) {
}