package com.swingtrade.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO classes formerly nested in SignalService.
 */
public final class SignalQueryResult {
    private SignalQueryResult() {} // prevent instantiation

    public record Signal(String symbol, SignalType type, Double confidence, LocalDate date, String reasoning) {
        public Signal(String symbol, String type, Double confidence, LocalDate date, String reasoning) {
            this(symbol, SignalType.valueOf(type), confidence, date, reasoning);
        }
    }

    public enum SignalType {
        BUY, SELL, HOLD
    }

    public record TechnicalAnalysis(String symbol, LocalDate date, List<String> indicators, double strength) {
    }

    public record SentimentAnalysis(String symbol, LocalDate date, String score, String summary) {
    }

    public record CombinedSignal(String symbol, LocalDate date, Signal technicalSignal, SentimentAnalysis sentiment,
                                 SignalType finalSignal) {
    }
}