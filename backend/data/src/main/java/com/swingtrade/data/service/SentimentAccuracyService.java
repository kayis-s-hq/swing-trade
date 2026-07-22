package com.swingtrade.data.service;

import com.swingtrade.data.entity.SentimentAccuracyEntity;
import com.swingtrade.data.repository.SentimentAccuracyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Provides sentiment accuracy metrics for the monitoring dashboard.
 */
@Service
public class SentimentAccuracyService {

    private final SentimentAccuracyRepository accuracyRepo;

    public SentimentAccuracyService(SentimentAccuracyRepository accuracyRepo) {
        this.accuracyRepo = accuracyRepo;
    }

    public AccuracyStats getAccuracyStats() {
        long total = accuracyRepo.countAll();
        long correct = accuracyRepo.countCorrect();
        double accuracyPct = total > 0 ? (double) correct / total * 100 : 0.0;

        Map<String, Integer> bySentiment = new HashMap<>();
        Map<String, Integer> bySymbol = new HashMap<>();

        for (SentimentAccuracyEntity e : accuracyRepo.findAll()) {
            bySentiment.merge(e.getLlmScore(), 1, Integer::sum);
            bySymbol.merge(e.getSymbol(), 1, Integer::sum);
        }

        return new AccuracyStats(
            (int) total,
            (int) correct,
            Math.round(accuracyPct * 100.0) / 100.0,
            bySentiment,
            bySymbol
        );
    }

    public List<AccuracyByWindow> getAccuracyByWindow() {
        List<SentimentAccuracyEntity> all = accuracyRepo.findAll();
        Map<String, List<SentimentAccuracyEntity>> windows = new LinkedHashMap<>();

        for (SentimentAccuracyEntity e : all) {
            String window;
            if (e.getActualReturn1d() != null) window = "1-day";
            else if (e.getActualReturn5d() != null) window = "5-day";
            else window = "21-day";
            windows.computeIfAbsent(window, k -> new ArrayList<>()).add(e);
        }

        List<AccuracyByWindow> result = new ArrayList<>();
        for (var entry : windows.entrySet()) {
            var list = entry.getValue();
            int total = list.size();
            long correct = list.stream().filter(e -> Boolean.TRUE.equals(e.getWasCorrect())).count();
            double accuracy = total > 0 ? (double) correct / total : 0.0;
            double avgReturn = list.stream()
                .filter(e -> e.getActualReturn5d() != null)
                .mapToDouble(e -> e.getActualReturn5d().doubleValue())
                .average().orElse(0.0);

            result.add(new AccuracyByWindow(
                entry.getKey(), total, Math.round(accuracy * 10000.0) / 100.0,
                Math.round(avgReturn * 10000.0) / 100.0
            ));
        }
        return result;
    }

    public List<AccuracyByRegime> getAccuracyByRegime() {
        List<Object[]> rows = accuracyRepo.accuracyByRegime();
        List<AccuracyByRegime> result = new ArrayList<>();
        for (Object[] row : rows) {
            String regime = (String) row[0];
            long total = ((Number) row[1]).longValue();
            double accuracy = Math.round(((Number) row[2]).doubleValue() * 10000.0) / 100.0;
            double avgConf = Math.round(((Number) row[3]).doubleValue() * 10000.0) / 100.0;
            result.add(new AccuracyByRegime(regime, (int) total, accuracy, avgConf));
        }
        return result;
    }

    public List<AccuracyBySymbol> getAccuracyBySymbol() {
        List<Object[]> rows = accuracyRepo.accuracyBySymbol();
        List<AccuracyBySymbol> result = new ArrayList<>();
        for (Object[] row : rows) {
            String symbol = (String) row[0];
            long total = ((Number) row[1]).longValue();
            double accuracy = Math.round(((Number) row[2]).doubleValue() * 10000.0) / 100.0;
            double avgConf = Math.round(((Number) row[3]).doubleValue() * 10000.0) / 100.0;
            result.add(new AccuracyBySymbol(symbol, (int) total, accuracy, avgConf));
        }
        return result;
    }

    public List<CalibrationData> getCalibrationData() {
        List<Object[]> rows = accuracyRepo.calibrationData();
        List<CalibrationData> result = new ArrayList<>();
        for (Object[] row : rows) {
            double bin = ((Number) row[0]).doubleValue();
            double actualAcc = Math.round(((Number) row[1]).doubleValue() * 10000.0) / 100.0;
            double predConf = Math.round(((Number) row[2]).doubleValue() * 10000.0) / 100.0;
            double error = Math.round(Math.abs(predConf - actualAcc) * 10000.0) / 100.0;
            long count = ((Number) row[3]).longValue();
            result.add(new CalibrationData(bin, predConf, actualAcc, error, count));
        }
        return result;
    }

    public List<RollingICResult> getRollingIC(int windowDays) {
        LocalDate since = LocalDate.now().minusDays(windowDays);
        List<Object[]> pairs = accuracyRepo.scoreReturnPairsSince(since);

        List<Double> scores = new ArrayList<>();
        List<Double> returns = new ArrayList<>();
        for (Object[] row : pairs) {
            scores.add(((BigDecimal) row[0]).doubleValue());
            returns.add(((BigDecimal) row[1]).doubleValue());
        }

        if (scores.size() < 3) {
            return List.of();
        }

        double ic = computeSpearmanIC(scores, returns);
        LocalDate end = LocalDate.now();

        return List.of(new RollingICResult(end.toString(), ic));
    }

    private double computeSpearmanIC(List<Double> scores, List<Double> returns) {
        int n = scores.size();
        double[] rankScores = rank(scores);
        double[] rankReturns = rank(returns);

        double sumD2 = 0;
        for (int i = 0; i < n; i++) {
            double d = rankScores[i] - rankReturns[i];
            sumD2 += d * d;
        }

        return 1.0 - (6.0 * sumD2) / (n * (n * n - 1));
    }

    private double[] rank(List<Double> values) {
        int n = values.size();
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double v = values.get(i);
            int r = 1;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (values.get(j) < v) r++;
                else if (values.get(j).equals(v) && j < i) r++;
            }
            result[i] = r;
        }
        return result;
    }

    public SignalVolumeStats getSignalVolumeStats() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);
        LocalDate thirtyDaysAgo = today.minusDays(30);

        long todayCount = accuracyRepo.countSince(today);
        long sevenDayCount = accuracyRepo.countSince(sevenDaysAgo);
        long thirtyDayCount = accuracyRepo.countSince(thirtyDaysAgo);

        return new SignalVolumeStats(
            (int) todayCount,
            (int) sevenDayCount,
            Math.round(sevenDayCount / 7.0 * 100.0) / 100.0,
            Math.round(thirtyDayCount / 30.0 * 100.0) / 100.0
        );
    }

    public ECEStats getECEStats() {
        List<Object[]> rows = accuracyRepo.calibrationData();
        long totalWeight = 0;
        double ece = 0;

        for (Object[] row : rows) {
            double actualAcc = ((Number) row[1]).doubleValue();
            double predConf = ((Number) row[2]).doubleValue();
            long count = ((Number) row[3]).longValue();
            ece += Math.abs(predConf - actualAcc) * count;
            totalWeight += count;
        }

        if (totalWeight > 0) ece /= totalWeight;

        return new ECEStats(
            Math.round(ece * 10000.0) / 100.0,
            rows.size()
        );
    }

    public long getDirectionalCount() {
        return accuracyRepo.countDirectionalSince(LocalDate.of(2020, 1, 1));
    }

    public long getDirectionalCorrectCount() {
        return accuracyRepo.countCorrectDirectionalSince(LocalDate.of(2020, 1, 1));
    }

    public double getAvgConfidence() {
        long total = accuracyRepo.countAll();
        if (total == 0) return 0.0;
        List<SentimentAccuracyEntity> all = accuracyRepo.findAll();
        double sum = all.stream()
            .filter(e -> e.getLlmConfidence() != null)
            .mapToDouble(e -> e.getLlmConfidence())
            .sum();
        return Math.round((sum / total) * 10000.0) / 100.0;
    }

    // Inner record types — kept in data module to avoid circular dependency with api module

    public record AccuracyStats(
        int total,
        int correct,
        double accuracyPct,
        Map<String, Integer> bySentiment,
        Map<String, Integer> bySymbol
    ) {}

    public record AccuracyByWindow(
        String window,
        int total,
        double accuracy,
        double avgReturn
    ) {}

    public record AccuracyByRegime(
        String regime,
        int total,
        double accuracy,
        double avgConfidence
    ) {}

    public record AccuracyBySymbol(
        String symbol,
        int totalSignals,
        double accuracy,
        double avgConfidence
    ) {}

    public record CalibrationData(
        double confidenceBin,
        double predictedConfidence,
        double actualAccuracy,
        double error,
        long count
    ) {}

    public record RollingICResult(
        String date,
        double spearmanIC
    ) {}

    public record SignalVolumeStats(
        int todayCount,
        int sevenDayCount,
        double sevenDayAvg,
        double thirtyDayAvg
    ) {}

    public record ECEStats(
        double ece,
        int bins
    ) {}
}