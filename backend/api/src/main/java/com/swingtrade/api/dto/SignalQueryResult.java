package com.swingtrade.api.dto;

import com.swingtrade.domain.Signal;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO classes for signal query results.
 */
public final class SignalQueryResult {
    private SignalQueryResult() {} // prevent instantiation

    public static List<String> allSignalTypes() {
        return List.of("BUY", "SELL", "HOLD");
    }

    public record TechnicalAnalysis(String symbol, LocalDate date, List<String> indicators, double strength) {
    }

    public record SentimentAnalysis(String symbol, LocalDate date, String score, String summary) {
    }

    public record CombinedSignal(String symbol, LocalDate date, Signal technicalSignal, SentimentAnalysis sentiment,
                                 Signal.SignalType finalSignal) {
    }
}