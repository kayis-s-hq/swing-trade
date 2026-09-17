package com.swingtrade.strategy;

import com.swingtrade.domain.BenchmarkComparison;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of a shared-capital portfolio backtest.
 *
 * <p>The production portfolio path marks open positions at each supplied trading candle close;
 * callers using the legacy trade-only engine overload retain the entry-notional fallback. The
 * point exposes settled cash and unsettled exit proceeds so cash availability is not confused
 * with economic equity.</p>
 */
public record PortfolioBacktestResult(
        LocalDate evaluationStart,
        LocalDate evaluationEnd,
        double initialCapital,
        double finalCapital,
        double totalReturn,
        double maxDrawdownPct,
        double sharpeRatio,
        double cagrPct,
        double sortinoRatio,
        double calmarRatio,
        int totalTrades,
        int winningTrades,
        int rejectedTrades,
        List<BacktestTrade> trades,
        List<PortfolioEquityPoint> equityCurve,
        List<String> rejectionReasons,
        BenchmarkComparison benchmarkComparison
) {
    /** Source-compatible constructor for callers without rejection-reason details. */
    public PortfolioBacktestResult(LocalDate evaluationStart, LocalDate evaluationEnd, double initialCapital,
                                   double finalCapital, double totalReturn, double maxDrawdownPct,
                                   double sharpeRatio, double cagrPct, double sortinoRatio, double calmarRatio,
                                   int totalTrades, int winningTrades, int rejectedTrades,
                                   List<BacktestTrade> trades, List<PortfolioEquityPoint> equityCurve) {
        this(evaluationStart, evaluationEnd, initialCapital, finalCapital, totalReturn, maxDrawdownPct,
                sharpeRatio, cagrPct, sortinoRatio, calmarRatio, totalTrades, winningTrades, rejectedTrades,
                trades, equityCurve, List.of(), BenchmarkComparison.unavailable(totalReturn));
    }

    public PortfolioBacktestResult {
        trades = List.copyOf(trades);
        equityCurve = List.copyOf(equityCurve);
        rejectionReasons = List.copyOf(rejectionReasons);
        benchmarkComparison = benchmarkComparison == null
                ? BenchmarkComparison.unavailable(totalReturn) : benchmarkComparison;
    }
}
