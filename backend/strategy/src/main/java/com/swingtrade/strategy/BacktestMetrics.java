package com.swingtrade.strategy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Pure calculations for metrics derived from a daily marked-to-market equity curve. */
final class BacktestMetrics {

    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final double CALENDAR_DAYS_PER_YEAR = 365.25;

    private BacktestMetrics() {
    }

    static double cagrPct(double initialCapital, double finalCapital, LocalDate start, LocalDate end) {
        if (initialCapital <= 0 || finalCapital < 0 || start == null || end == null || !start.isBefore(end)) {
            return 0.0;
        }
        double years = ChronoUnit.DAYS.between(start, end) / CALENDAR_DAYS_PER_YEAR;
        if (years <= 0) {
            return 0.0;
        }
        if (finalCapital == 0) {
            return -100.0;
        }
        return (Math.pow(finalCapital / initialCapital, 1.0 / years) - 1.0) * 100.0;
    }

    static double sortinoRatio(List<Double> capitalCurve) {
        if (capitalCurve == null || capitalCurve.size() < 3) {
            return 0.0;
        }
        double sumReturns = 0.0;
        double sumDownsideSquares = 0.0;
        int observations = 0;
        for (int i = 1; i < capitalCurve.size(); i++) {
            double previous = capitalCurve.get(i - 1);
            double current = capitalCurve.get(i);
            if (previous == 0.0) {
                continue;
            }
            double dailyReturn = (current - previous) / previous;
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

    static double calmarRatio(double cagrPct, double maxDrawdownPct) {
        return maxDrawdownPct == 0.0 ? 0.0 : cagrPct / maxDrawdownPct;
    }
}
