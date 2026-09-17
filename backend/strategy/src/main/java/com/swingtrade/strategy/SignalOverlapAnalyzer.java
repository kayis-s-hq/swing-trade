package com.swingtrade.strategy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Signal overlap / independence diagnostics between two variants' backtest runs (plan §6.5):
 * Jaccard similarity of their entry {@code (symbol, date)} sets, and Pearson correlation of their
 * daily portfolio returns. Needed to judge whether two variants are actually behaving
 * differently, not just producing differently-labelled but near-identical trades.
 */
public final class SignalOverlapAnalyzer {

    private SignalOverlapAnalyzer() {
    }

    /** Jaccard similarity of the two variants' entry (symbol, entryDate) sets, in [0,1]. */
    public static double entryJaccardSimilarity(List<PortfolioTrade> a, List<PortfolioTrade> b) {
        Set<EntryKey> setA = entryKeys(a);
        Set<EntryKey> setB = entryKeys(b);
        if (setA.isEmpty() && setB.isEmpty()) {
            return 1.0;
        }
        Set<EntryKey> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<EntryKey> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private static Set<EntryKey> entryKeys(List<PortfolioTrade> trades) {
        Set<EntryKey> keys = new HashSet<>();
        for (PortfolioTrade t : trades) {
            keys.add(new EntryKey(t.symbol(), t.entryDate()));
        }
        return keys;
    }

    /**
     * Pearson correlation of two daily equity curves' returns, aligned by date (dates present in
     * only one series are ignored). Returns 0.0 if fewer than 2 aligned observations exist.
     */
    public static double dailyReturnCorrelation(List<DailyEquityPoint> a, List<DailyEquityPoint> b) {
        var returnsA = returnsByDate(a);
        var returnsB = returnsByDate(b);

        List<Double> xs = new java.util.ArrayList<>();
        List<Double> ys = new java.util.ArrayList<>();
        for (var entry : returnsA.entrySet()) {
            Double y = returnsB.get(entry.getKey());
            if (y != null) {
                xs.add(entry.getValue());
                ys.add(y);
            }
        }
        if (xs.size() < 2) {
            return 0.0;
        }
        double[] x = xs.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = ys.stream().mapToDouble(Double::doubleValue).toArray();
        return pearson(x, y);
    }

    private static java.util.Map<LocalDate, Double> returnsByDate(List<DailyEquityPoint> curve) {
        java.util.Map<LocalDate, Double> result = new java.util.LinkedHashMap<>();
        for (int i = 1; i < curve.size(); i++) {
            double prev = curve.get(i - 1).equity().doubleValue();
            double cur = curve.get(i).equity().doubleValue();
            result.put(curve.get(i).date(), prev == 0.0 ? 0.0 : (cur - prev) / prev);
        }
        return result;
    }

    static double pearson(double[] x, double[] y) {
        int n = x.length;
        double meanX = mean(x);
        double meanY = mean(y);
        double cov = 0.0;
        double varX = 0.0;
        double varY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }
        if (varX == 0.0 || varY == 0.0) {
            return 0.0;
        }
        return cov / Math.sqrt(varX * varY);
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return values.length == 0 ? 0.0 : sum / values.length;
    }

    private record EntryKey(String symbol, LocalDate entryDate) {
    }
}
