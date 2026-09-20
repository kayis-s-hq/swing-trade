package com.swingtrade.strategy;

import com.swingtrade.domain.BenchmarkComparison;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate performance metrics for a single-symbol {@link BacktestEngine} run.
 *
 * <p>Percent-of-capital fields ({@code avgGainPct}, {@code avgLossPct}, {@code maxDrawdownPct},
 * {@code totalReturn}) are {@link BigDecimal} at {@link FinancialScale#PERCENT_SCALE}. Trade-count
 * {@code winRate}, {@code expectancy} and the statistical ratios (Sharpe, Sortino, Calmar, CAGR) stay
 * {@code double}; see {@link BacktestMetrics}.</p>
 *
 * @param avgGainPct    average pnlPct across winning trades (0 if none)
 * @param avgLossPct    average absolute pnlPct across losing trades, as a positive number (0 if none)
 * @param maxDrawdownPct largest peak-to-trough drop in the equity curve, as a percentage
 * @param sharpeRatio   annualized Sharpe ratio computed from the daily equity-curve return series
 * @param totalReturn   total return over the backtest period, as a percentage of initial capital
 * @param cagrPct       annualized return over the evaluated calendar period
 * @param sortinoRatio  annualized return-to-downside-volatility ratio from the daily equity curve
 * @param calmarRatio   CAGR divided by maximum drawdown percentage
 * @param benchmarkComparison comparison with the buy-and-hold return over the evaluation window
 * @param expectancy    winRate/100 * avgGainPct - (1 - winRate/100) * avgLossPct, in percent-per-trade terms
 */
public record BacktestResult(
    String symbol,
    int totalTrades,
    int winningTrades,
    int losingTrades,
    double winRate,
    BigDecimal avgGainPct,
    BigDecimal avgLossPct,
    BigDecimal maxDrawdownPct,
    double sharpeRatio,
    BigDecimal totalReturn,
    double expectancy,
    List<BacktestTrade> trades,
    double cagrPct,
    double sortinoRatio,
    double calmarRatio,
    BenchmarkComparison benchmarkComparison
) {
    /** Source-compatible constructor for callers that do not yet provide risk-adjusted metrics. */
    public BacktestResult(String symbol, int totalTrades, int winningTrades, int losingTrades,
                          double winRate, BigDecimal avgGainPct, BigDecimal avgLossPct, BigDecimal maxDrawdownPct,
                          double sharpeRatio, BigDecimal totalReturn, double expectancy,
                          List<BacktestTrade> trades) {
        this(symbol, totalTrades, winningTrades, losingTrades, winRate, avgGainPct, avgLossPct,
                maxDrawdownPct, sharpeRatio, totalReturn, expectancy, trades, 0.0, 0.0, 0.0,
                BenchmarkComparison.unavailable(totalReturn.doubleValue()));
    }

    /** Source-compatible constructor for callers that provide risk-adjusted metrics only. */
    public BacktestResult(String symbol, int totalTrades, int winningTrades, int losingTrades,
                          double winRate, BigDecimal avgGainPct, BigDecimal avgLossPct, BigDecimal maxDrawdownPct,
                          double sharpeRatio, BigDecimal totalReturn, double expectancy,
                          List<BacktestTrade> trades, double cagrPct, double sortinoRatio,
                          double calmarRatio) {
        this(symbol, totalTrades, winningTrades, losingTrades, winRate, avgGainPct, avgLossPct,
                maxDrawdownPct, sharpeRatio, totalReturn, expectancy, trades, cagrPct, sortinoRatio,
                calmarRatio, BenchmarkComparison.unavailable(totalReturn.doubleValue()));
    }
}
