package com.swingtrade.strategy;

public record BacktestResult(
    double totalPnl,
    int tradeCount,
    double winRate,
    double sharpeRatio,
    double maxDrawdown,
    double avgTradeDuration,
    double profitFactor
) {}
