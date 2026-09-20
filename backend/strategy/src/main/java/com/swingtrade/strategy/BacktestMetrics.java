package com.swingtrade.strategy;

import com.swingtrade.domain.BenchmarkComparison;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pure calculations for metrics derived from a daily marked-to-market equity curve.
 *
 * <p>Inputs are {@link BigDecimal} money amounts. Period returns, total return and drawdown are
 * computed in {@code BigDecimal} at {@link FinancialScale#RATIO} precision and rounded to
 * {@link FinancialScale#PERCENT_SCALE}; total return and max drawdown are therefore exact
 * {@code BigDecimal} percentages. Sharpe, Sortino and CAGR
 * need {@code sqrt}/{@code pow}, which {@code BigDecimal} does not provide, so they are the one
 * documented conversion boundary: the money-derived returns are converted once with
 * {@link BigDecimal#doubleValue()} and the statistics run in {@code double}. Their results are
 * dimensionless ratios/percentages, not money, so no monetary precision is lost.</p>
 *
 * <p><b>Deliberately {@code double}</b> (statistical, unitless, need sqrt/pow or are annualised
 * estimates): Sharpe, Sortino, Calmar, CAGR, the trade-count win rate and the per-trade expectancy
 * (a win-rate-weighted blend of those). Percent-of-capital results (total return, max drawdown,
 * average gain/loss) are {@code BigDecimal}. The buy-and-hold benchmark comparison in {@code core}
 * remains {@code double} because it is a reporting value derived from a market index.</p>
 */
final class BacktestMetrics {

    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final double CALENDAR_DAYS_PER_YEAR = 365.25;

    private BacktestMetrics() {
    }

    static double cagrPct(BigDecimal initialCapital, BigDecimal finalCapital, LocalDate start, LocalDate end) {
        if (initialCapital.signum() <= 0 || finalCapital.signum() < 0 || start == null || end == null
                || !start.isBefore(end)) {
            return 0.0;
        }
        double years = ChronoUnit.DAYS.between(start, end) / CALENDAR_DAYS_PER_YEAR;
        if (years <= 0) {
            return 0.0;
        }
        if (finalCapital.signum() == 0) {
            return -100.0;
        }
        double growth = finalCapital.divide(initialCapital, FinancialScale.RATIO).doubleValue();
        return (Math.pow(growth, 1.0 / years) - 1.0) * 100.0;
    }

    /** Total return as a percentage of initial capital. */
    static BigDecimal totalReturnPct(BigDecimal initialCapital, BigDecimal finalCapital) {
        return FinancialScale.percentOf(finalCapital.subtract(initialCapital), initialCapital);
    }

    /** Largest peak-to-trough drop of the equity curve, in percent; 0 for an empty curve. */
    static BigDecimal maxDrawdownPct(List<BigDecimal> capitalCurve) {
        if (capitalCurve == null || capitalCurve.isEmpty()) {
            return FinancialScale.percent(BigDecimal.ZERO);
        }
        BigDecimal peak = capitalCurve.getFirst();
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal value : capitalCurve) {
            peak = peak.max(value);
            if (peak.signum() > 0) {
                BigDecimal drawdown = peak.subtract(value).multiply(FinancialScale.HUNDRED)
                        .divide(peak, FinancialScale.RATIO);
                maxDrawdown = maxDrawdown.max(drawdown);
            }
        }
        return FinancialScale.percent(maxDrawdown);
    }

    /** Period-over-period returns as fractions; a zero previous value yields a zero return. */
    private static double[] periodReturns(List<BigDecimal> capitalCurve) {
        double[] returns = new double[capitalCurve.size() - 1];
        for (int i = 1; i < capitalCurve.size(); i++) {
            BigDecimal previous = capitalCurve.get(i - 1);
            returns[i - 1] = previous.signum() == 0 ? 0.0
                    : capitalCurve.get(i).subtract(previous).divide(previous, FinancialScale.RATIO).doubleValue();
        }
        return returns;
    }

    static double sortinoRatio(List<BigDecimal> capitalCurve) {
        if (capitalCurve == null || capitalCurve.size() < 3) {
            return 0.0;
        }
        double sumReturns = 0.0;
        double sumDownsideSquares = 0.0;
        int observations = 0;
        for (int i = 1; i < capitalCurve.size(); i++) {
            BigDecimal previous = capitalCurve.get(i - 1);
            if (previous.signum() == 0) {
                continue;
            }
            double dailyReturn = capitalCurve.get(i).subtract(previous)
                    .divide(previous, FinancialScale.RATIO).doubleValue();
            sumReturns += dailyReturn;
            sumDownsideSquares += Math.pow(Math.min(dailyReturn, 0.0), 2);
            observations++;
        }
        if (observations == 0) {
            return 0.0;
        }
        double downsideDeviation = Math.sqrt(sumDownsideSquares / observations);
        return downsideDeviation == 0.0
                ? 0.0
                : (sumReturns / observations) / downsideDeviation * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    static double sharpeRatio(List<BigDecimal> capitalCurve) {
        if (capitalCurve == null || capitalCurve.size() < 3) {
            return 0.0;
        }
        double[] returns = periodReturns(capitalCurve);
        double mean = java.util.Arrays.stream(returns).average().orElse(0.0);
        double variance = java.util.Arrays.stream(returns).map(value -> Math.pow(value - mean, 2)).average().orElse(0.0);
        double deviation = Math.sqrt(variance);
        return deviation == 0.0 ? 0.0 : mean / deviation * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    static double calmarRatio(double cagrPct, BigDecimal maxDrawdownPct) {
        return maxDrawdownPct.signum() == 0 ? 0.0 : cagrPct / maxDrawdownPct.doubleValue();
    }

    static BenchmarkComparison buyAndHoldComparison(BigDecimal strategyReturnPct,
                                                    BigDecimal startingClose,
                                                    BigDecimal endingClose) {
        if (startingClose == null || endingClose == null || startingClose.signum() <= 0
                || endingClose.signum() < 0) {
            return BenchmarkComparison.unavailable(strategyReturnPct.doubleValue());
        }
        double benchmarkReturnPct = endingClose.subtract(startingClose)
                .divide(startingClose, java.math.MathContext.DECIMAL128)
                .doubleValue() * 100.0;
        return BenchmarkComparison.buyAndHold(strategyReturnPct.doubleValue(), benchmarkReturnPct);
    }
}
