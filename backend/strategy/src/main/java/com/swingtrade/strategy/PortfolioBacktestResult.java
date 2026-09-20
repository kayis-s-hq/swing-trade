package com.swingtrade.strategy;

import com.swingtrade.domain.BenchmarkComparison;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of a shared-capital portfolio backtest.
 *
 * <p>The production portfolio path marks open positions at each supplied trading candle close;
 * callers using the legacy trade-only engine overload retain the entry-notional fallback. The
 * point exposes settled cash and unsettled exit proceeds so cash availability is not confused
 * with economic equity.</p>
 *
 * <p>{@code strategyVariantId} attributes this run to the {@link com.swingtrade.domain.StrategyConfig}
 * variant (or plain {@link TradingStrategy} name, when no persisted variant is involved) whose
 * rules generated every trade in the run. The portfolio backtest currently evaluates exactly one
 * strategy across every symbol in a run, so attribution is per-run rather than per-trade; comparing
 * variants means running this method once per variant and comparing the resulting
 * {@code strategyVariantId}-tagged results (see {@code PromotionEligibilityChecker} for the
 * single-symbol analogue this is meant to eventually feed). May be {@code null} for legacy callers
 * that have not been updated to supply an id.</p>
 */
public record PortfolioBacktestResult(
        LocalDate evaluationStart,
        LocalDate evaluationEnd,
        BigDecimal initialCapital,
        BigDecimal finalCapital,
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
        BenchmarkComparison benchmarkComparison,
        String strategyVariantId
) {
    /** Source-compatible constructor for callers without rejection-reason details. */
    public PortfolioBacktestResult(LocalDate evaluationStart, LocalDate evaluationEnd, BigDecimal initialCapital,
                                   BigDecimal finalCapital, double totalReturn, double maxDrawdownPct,
                                   double sharpeRatio, double cagrPct, double sortinoRatio, double calmarRatio,
                                   int totalTrades, int winningTrades, int rejectedTrades,
                                   List<BacktestTrade> trades, List<PortfolioEquityPoint> equityCurve) {
        this(evaluationStart, evaluationEnd, initialCapital, finalCapital, totalReturn, maxDrawdownPct,
                sharpeRatio, cagrPct, sortinoRatio, calmarRatio, totalTrades, winningTrades, rejectedTrades,
                trades, equityCurve, List.of(), BenchmarkComparison.unavailable(totalReturn), null);
    }

    /** Source-compatible constructor for callers without a strategy-variant attribution. */
    public PortfolioBacktestResult(LocalDate evaluationStart, LocalDate evaluationEnd, BigDecimal initialCapital,
                                   BigDecimal finalCapital, double totalReturn, double maxDrawdownPct,
                                   double sharpeRatio, double cagrPct, double sortinoRatio, double calmarRatio,
                                   int totalTrades, int winningTrades, int rejectedTrades,
                                   List<BacktestTrade> trades, List<PortfolioEquityPoint> equityCurve,
                                   List<String> rejectionReasons, BenchmarkComparison benchmarkComparison) {
        this(evaluationStart, evaluationEnd, initialCapital, finalCapital, totalReturn, maxDrawdownPct,
                sharpeRatio, cagrPct, sortinoRatio, calmarRatio, totalTrades, winningTrades, rejectedTrades,
                trades, equityCurve, rejectionReasons, benchmarkComparison, null);
    }

    public PortfolioBacktestResult {
        trades = List.copyOf(trades);
        equityCurve = List.copyOf(equityCurve);
        rejectionReasons = List.copyOf(rejectionReasons);
        benchmarkComparison = benchmarkComparison == null
                ? BenchmarkComparison.unavailable(totalReturn) : benchmarkComparison;
    }
}
