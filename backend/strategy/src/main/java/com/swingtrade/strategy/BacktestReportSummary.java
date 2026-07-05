package com.swingtrade.strategy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated view across a {@link BacktestEngine#runBacktestAll} run, as saved by
 * {@link BacktestEngine#generateReport}.
 *
 * @param overallWinRate     trade-weighted win rate across all symbols (sum of winning trades / sum of total trades)
 * @param overallSharpeRatio simple mean of each symbol's Sharpe ratio (symbols with zero trades excluded)
 */
public record BacktestReportSummary(
    LocalDateTime generatedAt,
    int symbolsBacktested,
    List<BacktestResult> top10ByWinRate,
    List<BacktestResult> top10ByTotalReturn,
    double overallWinRate,
    double overallSharpeRatio,
    List<BacktestResult> results
) {}
