package com.swingtrade.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CompositeAnalysis(
    String symbol,
    LocalDate date,

    // Composite
    int compositeScore,
    String compositeSignal,
    BigDecimal compositeConfidence,
    List<SourceScore> sources,

    // News sentiment
    NewsScore news,

    // Technical signal
    TechnicalScore technical,

    // Fundamentals
    FundamentalScore fundamentals,

    // Backtest (standalone, not weighted)
    BacktestScore backtest,

    // Summary reasoning
    String reasoning
) {
    public record SourceScore(String name, int score, double weight, String description) {}
    public record NewsScore(int score, String summary, List<String> catalysts, List<String> redFlags, int articleCount) {}
    public record TechnicalScore(int score, String signal, double confidence, List<String> indicators) {}
    public record FundamentalScore(int score, List<String> factors) {}
    public record BacktestScore(int totalTrades, double winRate, double profitFactor,
                                double maxDrawdown, double totalReturn, double expectancy,
                                boolean hasEnoughData) {}
}