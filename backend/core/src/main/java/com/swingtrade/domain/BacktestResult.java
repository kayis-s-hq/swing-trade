package com.swingtrade.domain;

import java.time.LocalDate;

public record BacktestResult(
    Long id, String symbol, LocalDate runDate, int totalTrades, int winningTrades,
    int losingTrades, double winRate, double avgGainPct, double avgLossPct,
    double maxDrawdownPct, double sharpeRatio, double totalReturn, double expectancy,
    double profitFactor, boolean hasEnoughData) {}
