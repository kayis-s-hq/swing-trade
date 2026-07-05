package com.swingtrade.strategy;

import java.util.List;

/**
 * Aggregate performance metrics for a single-symbol {@link BacktestEngine} run.
 *
 * @param avgGainPct    average pnlPct across winning trades (0 if none)
 * @param avgLossPct    average absolute pnlPct across losing trades, as a positive number (0 if none)
 * @param maxDrawdownPct largest peak-to-trough drop in the equity curve, as a percentage
 * @param sharpeRatio   annualized Sharpe ratio computed from the daily equity-curve return series
 * @param totalReturn   total return over the backtest period, as a percentage of initial capital
 * @param expectancy    winRate/100 * avgGainPct - (1 - winRate/100) * avgLossPct, in percent-per-trade terms
 */
public record BacktestResult(
    String symbol,
    int totalTrades,
    int winningTrades,
    int losingTrades,
    double winRate,
    double avgGainPct,
    double avgLossPct,
    double maxDrawdownPct,
    double sharpeRatio,
    double totalReturn,
    double expectancy,
    List<BacktestTrade> trades
) {}
