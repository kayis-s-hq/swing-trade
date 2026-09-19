package com.swingtrade.strategy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Computes the full {@link PortfolioMetrics} set from a daily mark-to-market equity curve and
 * closed trades (plan §6.2). Pure/stateless - every method is deterministic given its inputs
 * (the bootstrap CI uses a fixed seed so results are reproducible and testable).
 */
public final class MetricsCalculator {

    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final double CALENDAR_DAYS_PER_YEAR = 365.25;
    private static final int BOOTSTRAP_SAMPLES = 2000;
    private static final long BOOTSTRAP_SEED = 42L;

    private MetricsCalculator() {
    }

    /**
     * @param equityCurve         daily mark-to-market equity, chronologically ordered, at least 2 points
     * @param trades              closed trades in the window
     * @param annualRiskFreeRatePct annualised risk-free rate as a percentage (default per plan is 6.5)
     */
    public static PortfolioMetrics compute(List<DailyEquityPoint> equityCurve, List<PortfolioTrade> trades,
                                            double annualRiskFreeRatePct) {
        if (equityCurve == null || equityCurve.size() < 2) {
            throw new IllegalArgumentException("equityCurve must have at least 2 points");
        }
        trades = trades == null ? List.of() : trades;

        double[] equity = equityCurve.stream().mapToDouble(p -> p.equity().doubleValue()).toArray();
        double[] dailyReturns = dailyReturns(equity);

        LocalDate start = equityCurve.get(0).date();
        LocalDate end = equityCurve.get(equityCurve.size() - 1).date();
        double initial = equity[0];
        double last = equity[equity.length - 1];

        double totalReturnPct = initial == 0 ? 0.0 : ((last - initial) / initial) * 100.0;
        double cagrPct = cagrPct(initial, last, start, end);
        double annualVolPct = stdDev(dailyReturns) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0;

        double dailyRf = Math.pow(1.0 + annualRiskFreeRatePct / 100.0, 1.0 / TRADING_DAYS_PER_YEAR) - 1.0;
        double sharpe = sharpeRatio(dailyReturns, dailyRf);
        double sortino = sortinoRatio(dailyReturns, dailyRf);

        DrawdownStats dd = drawdownStats(equityCurve);
        double calmar = dd.maxDrawdownPct == 0.0 ? 0.0 : cagrPct / dd.maxDrawdownPct;

        List<PortfolioTrade> wins = trades.stream().filter(PortfolioTrade::isWin).toList();
        List<PortfolioTrade> losses = trades.stream().filter(t -> !t.isWin()).toList();
        int n = trades.size();
        int w = wins.size();

        double winRate = n == 0 ? 0.0 : (w / (double) n);
        double[] wilson = wilsonScoreInterval(w, n, 1.959963984540054);

        double avgWinPct = wins.isEmpty() ? 0.0 : wins.stream().mapToDouble(PortfolioTrade::pnlPct).average().orElse(0.0);
        double avgLossPct = losses.isEmpty() ? 0.0
            : Math.abs(losses.stream().mapToDouble(PortfolioTrade::pnlPct).average().orElse(0.0));
        double payoffRatio = avgLossPct == 0.0 ? 0.0 : avgWinPct / avgLossPct;

        double[] rMultiples = trades.stream().mapToDouble(PortfolioTrade::rMultiple).toArray();
        double expectancyR = mean(rMultiples);
        double[] expectancyCi = bootstrapMeanCi(rMultiples, BOOTSTRAP_SAMPLES, BOOTSTRAP_SEED);
        double expectancyRupees = trades.isEmpty() ? 0.0
            : trades.stream().mapToDouble(t -> t.pnl().doubleValue()).average().orElse(0.0);

        double grossProfit = wins.stream().mapToDouble(t -> t.pnl().doubleValue()).sum();
        double grossLoss = Math.abs(losses.stream().mapToDouble(t -> t.pnl().doubleValue()).sum());
        double profitFactor = grossLoss == 0.0 ? (grossProfit > 0 ? Double.POSITIVE_INFINITY : 0.0) : grossProfit / grossLoss;

        double avgHoldingDays = trades.isEmpty() ? 0.0 : trades.stream().mapToInt(PortfolioTrade::holdingDays).average().orElse(0.0);
        long totalCalendarDays = ChronoUnit.DAYS.between(start, end) + 1;
        double totalHeldDays = trades.stream().mapToInt(PortfolioTrade::holdingDays).sum();
        double exposurePct = totalCalendarDays == 0 ? 0.0 : Math.min(100.0, (totalHeldDays / totalCalendarDays) * 100.0);

        double turnover = trades.stream()
            .mapToDouble(t -> t.entryPrice().doubleValue() * t.quantity())
            .sum() / (initial == 0 ? 1.0 : initial);

        double grossPnlSum = trades.stream()
            .mapToDouble(t -> (t.exitPrice().doubleValue() - t.entryPrice().doubleValue()) * t.quantity())
            .sum();
        double netPnlSum = trades.stream().mapToDouble(t -> t.pnl().doubleValue()).sum();
        double totalCosts = grossPnlSum - netPnlSum;
        double costDragPct = initial == 0 ? 0.0 : (totalCosts / initial) * 100.0;

        return new PortfolioMetrics(
            cagrPct, totalReturnPct, annualVolPct, sharpe, sortino,
            dd.maxDrawdownPct, dd.maxDrawdownDurationDays, calmar,
            n, winRate * 100.0, wilson[0] * 100.0, wilson[1] * 100.0,
            avgWinPct, avgLossPct, payoffRatio,
            expectancyR, expectancyCi[0], expectancyCi[1], expectancyRupees,
            profitFactor, exposurePct, avgHoldingDays, turnover, costDragPct,
            PortfolioMetrics.BenchmarkComparison.stub());
    }

    /** Per-symbol P&L contribution as a percentage of total net P&L. Flags none itself; see caller for the >40% rule (plan §6.2). */
    public static Map<String, Double> perSymbolPnlContributionPct(List<PortfolioTrade> trades) {
        double total = trades.stream().mapToDouble(t -> t.pnl().doubleValue()).sum();
        Map<String, Double> bySymbol = new HashMap<>();
        for (PortfolioTrade t : trades) {
            bySymbol.merge(t.symbol(), t.pnl().doubleValue(), Double::sum);
        }
        Map<String, Double> result = new HashMap<>();
        double totalAbs = Math.abs(total);
        for (Map.Entry<String, Double> e : bySymbol.entrySet()) {
            result.put(e.getKey(), totalAbs == 0.0 ? 0.0 : (e.getValue() / total) * 100.0);
        }
        return result;
    }

    public static Map<ExitReason, Integer> exitReasonBreakdown(List<PortfolioTrade> trades) {
        Map<ExitReason, Integer> result = new HashMap<>();
        for (PortfolioTrade t : trades) {
            result.merge(t.exitReason(), 1, Integer::sum);
        }
        return result;
    }

    public static Map<Integer, Double> perYearReturnPct(List<DailyEquityPoint> equityCurve) {
        Map<Integer, Double> firstOfYear = new HashMap<>();
        Map<Integer, Double> lastOfYear = new HashMap<>();
        for (DailyEquityPoint p : equityCurve) {
            int year = p.date().getYear();
            firstOfYear.putIfAbsent(year, p.equity().doubleValue());
            lastOfYear.put(year, p.equity().doubleValue());
        }
        Map<Integer, Double> result = new HashMap<>();
        for (Integer year : firstOfYear.keySet()) {
            double first = firstOfYear.get(year);
            double last = lastOfYear.get(year);
            result.put(year, first == 0.0 ? 0.0 : ((last - first) / first) * 100.0);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Core math
    // -----------------------------------------------------------------------

    static double[] dailyReturns(double[] equity) {
        double[] returns = new double[equity.length - 1];
        for (int i = 1; i < equity.length; i++) {
            double prev = equity[i - 1];
            returns[i - 1] = prev == 0.0 ? 0.0 : (equity[i] - prev) / prev;
        }
        return returns;
    }

    static double cagrPct(double initial, double last, LocalDate start, LocalDate end) {
        if (initial <= 0 || last < 0 || start == null || end == null || !start.isBefore(end)) {
            return 0.0;
        }
        double years = ChronoUnit.DAYS.between(start, end) / CALENDAR_DAYS_PER_YEAR;
        if (years <= 0) {
            return 0.0;
        }
        if (last == 0) {
            return -100.0;
        }
        return (Math.pow(last / initial, 1.0 / years) - 1.0) * 100.0;
    }

    static double sharpeRatio(double[] dailyReturns, double dailyRiskFree) {
        if (dailyReturns.length < 2) {
            return 0.0;
        }
        double[] excess = new double[dailyReturns.length];
        for (int i = 0; i < dailyReturns.length; i++) {
            excess[i] = dailyReturns[i] - dailyRiskFree;
        }
        double meanExcess = mean(excess);
        double sd = stdDev(excess);
        return sd == 0.0 ? 0.0 : (meanExcess / sd) * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    static double sortinoRatio(double[] dailyReturns, double dailyRiskFree) {
        if (dailyReturns.length < 2) {
            return 0.0;
        }
        double sumExcess = 0.0;
        double sumDownsideSq = 0.0;
        for (double r : dailyReturns) {
            double excess = r - dailyRiskFree;
            sumExcess += excess;
            sumDownsideSq += Math.pow(Math.min(excess, 0.0), 2);
        }
        int n = dailyReturns.length;
        double meanExcess = sumExcess / n;
        double downsideDeviation = Math.sqrt(sumDownsideSq / n);
        return downsideDeviation == 0.0 ? 0.0 : (meanExcess / downsideDeviation) * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    static DrawdownStats drawdownStats(List<DailyEquityPoint> equityCurve) {
        double peak = equityCurve.get(0).equity().doubleValue();
        LocalDate peakDate = equityCurve.get(0).date();
        double maxDrawdown = 0.0;
        int maxDurationDays = 0;

        for (DailyEquityPoint p : equityCurve) {
            double value = p.equity().doubleValue();
            if (value >= peak) {
                peak = value;
                peakDate = p.date();
            } else if (peak > 0) {
                double drawdown = (peak - value) / peak * 100.0;
                maxDrawdown = Math.max(maxDrawdown, drawdown);
                int durationDays = (int) ChronoUnit.DAYS.between(peakDate, p.date());
                maxDurationDays = Math.max(maxDurationDays, durationDays);
            }
        }
        return new DrawdownStats(maxDrawdown, maxDurationDays);
    }

    /**
     * Wilson score interval for a binomial proportion (plan §6.2: win-rate 95% CI). {@code z} is
     * the standard normal quantile (1.959963984540054 for 95%).
     *
     * @return {@code [low, high]}, both in [0,1]; {@code [0,1]} when {@code n == 0}
     */
    static double[] wilsonScoreInterval(int successes, int n, double z) {
        if (n == 0) {
            return new double[] {0.0, 1.0};
        }
        double p = successes / (double) n;
        double z2 = z * z;
        double denominator = 1 + z2 / n;
        double center = p + z2 / (2 * n);
        double margin = z * Math.sqrt((p * (1 - p) / n) + (z2 / (4.0 * n * n)));
        double low = (center - margin) / denominator;
        double high = (center + margin) / denominator;
        return new double[] {Math.max(0.0, low), Math.min(1.0, high)};
    }

    /**
     * Bootstrap 95% CI for the mean of {@code samples} via resampling with replacement
     * (plan §6.2: expectancy CI). Fixed seed -> deterministic/reproducible/testable.
     *
     * @return {@code [low, high]}; {@code [0,0]} for an empty/single-element input
     */
    static double[] bootstrapMeanCi(double[] samples, int bootstrapSamples, long seed) {
        if (samples.length < 2) {
            return new double[] {0.0, 0.0};
        }
        Random random = new Random(seed);
        double[] means = new double[bootstrapSamples];
        for (int b = 0; b < bootstrapSamples; b++) {
            double sum = 0.0;
            for (int i = 0; i < samples.length; i++) {
                sum += samples[random.nextInt(samples.length)];
            }
            means[b] = sum / samples.length;
        }
        double[] sorted = means.clone();
        java.util.Arrays.sort(sorted);
        int lowIdx = (int) Math.floor(0.025 * sorted.length);
        int highIdx = (int) Math.min(sorted.length - 1, Math.ceil(0.975 * sorted.length));
        return new double[] {sorted[lowIdx], sorted[highIdx]};
    }

    static double mean(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    static double stdDev(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double m = mean(values);
        double sumSq = 0.0;
        for (double v : values) {
            sumSq += Math.pow(v - m, 2);
        }
        return Math.sqrt(sumSq / values.length);
    }

    private record DrawdownStats(double maxDrawdownPct, int maxDrawdownDurationDays) {
    }
}
