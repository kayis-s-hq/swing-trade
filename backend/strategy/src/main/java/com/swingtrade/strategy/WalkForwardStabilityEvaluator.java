package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyConfig;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Bounded parameter search with per-fold train/validate separation.
 *
 * <p>This class deliberately knows nothing about a concrete strategy or portfolio simulator.
 * The caller supplies the runner, so experiments cannot silently change live defaults.</p>
 */
public final class WalkForwardStabilityEvaluator {
    public static final int MAX_CANDIDATES = 64;
    public static final int MAX_FOLDS = 8;

    @FunctionalInterface
    public interface BacktestRunner {
        BacktestResult run(StrategyConfig strategyConfig, BacktestConfig backtestConfig,
                           LocalDate start, LocalDate end);
    }

    public record Window(LocalDate trainStart, LocalDate trainEnd,
                         LocalDate validationStart, LocalDate validationEnd) {
        public Window {
            Objects.requireNonNull(trainStart, "trainStart");
            Objects.requireNonNull(trainEnd, "trainEnd");
            Objects.requireNonNull(validationStart, "validationStart");
            Objects.requireNonNull(validationEnd, "validationEnd");
            if (trainStart.isAfter(trainEnd) || validationStart.isAfter(validationEnd)
                    || !trainEnd.isBefore(validationStart)) {
                throw new IllegalArgumentException("Walk-forward windows must be ordered and non-overlapping");
            }
        }
    }

    public record FoldResult(Window window, Map<String, Object> selectedParameters,
                             BacktestResult train, BacktestResult validation) { }

    public record CandidateResult(StrategyConfig config, List<BacktestResult> trainResults,
                                  List<BacktestResult> validationResults,
                                  double averageTrainSharpe, double averageValidationSharpe,
                                  double averageValidationReturn) {
        public CandidateResult {
            trainResults = List.copyOf(trainResults);
            validationResults = List.copyOf(validationResults);
        }
    }

    public enum Decision { ACCEPT, REJECT_SPIKY_OPTIMUM, REJECT_UNSTABLE, REJECT_OVERFIT }

    public record Result(List<CandidateResult> candidates, List<FoldResult> folds,
                         StrategyConfig selectedConfig, double validationSharpeStdDev,
                         double validationMaxDrawdownMedian, double deflatedSharpe,
                         double deflatedSharpePValue, Decision decision,
                         List<String> limitations) {
        public Result {
            candidates = List.copyOf(candidates);
            folds = List.copyOf(folds);
            limitations = List.copyOf(limitations);
        }
    }

    public Result evaluate(StrategyConfig baseConfig, BacktestConfig backtestConfig,
                           Map<String, ? extends List<?>> parameterRanges,
                           List<Window> windows, BacktestRunner runner) {
        Objects.requireNonNull(baseConfig, "baseConfig");
        Objects.requireNonNull(backtestConfig, "backtestConfig");
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(runner, "runner");
        if (windows.isEmpty() || windows.size() > MAX_FOLDS) {
            throw new IllegalArgumentException("windows must contain 1-" + MAX_FOLDS + " folds");
        }

        List<StrategyConfig> configs = BoundedParameterGrid.expand(parameterRanges, MAX_CANDIDATES)
            .stream().map(parameters -> withParameters(baseConfig, parameters)).toList();
        List<CandidateResult> candidates = new ArrayList<>();
        for (StrategyConfig config : configs) {
            List<BacktestResult> train = windows.stream()
                .map(w -> runner.run(config, backtestConfig, w.trainStart(), w.trainEnd())).toList();
            List<BacktestResult> validation = windows.stream()
                .map(w -> runner.run(config, backtestConfig, w.validationStart(), w.validationEnd())).toList();
            candidates.add(new CandidateResult(config, train, validation,
                average(train, BacktestResult::sharpeRatio), average(validation, BacktestResult::sharpeRatio),
                average(validation, BacktestResult::totalReturn)));
        }

        List<FoldResult> folds = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            int foldIndex = i;
            CandidateResult winner = candidates.stream().max(Comparator
                .comparingDouble((CandidateResult c) -> c.trainResults().get(foldIndex).sharpeRatio())
                .thenComparingDouble(c -> c.trainResults().get(foldIndex).totalReturn())
                .thenComparing(c -> c.config().paramsHash())).orElseThrow();
            folds.add(new FoldResult(windows.get(i), winner.config().params(),
                winner.trainResults().get(i), winner.validationResults().get(i)));
        }
        CandidateResult selected = candidates.stream().max(Comparator
            .comparingDouble(CandidateResult::averageValidationSharpe)
            .thenComparingDouble(CandidateResult::averageValidationReturn)
            .thenComparing(c -> c.config().paramsHash())).orElseThrow();
        double[] sharpes = folds.stream().mapToDouble(f -> f.validation().sharpeRatio()).toArray();
        double[] drawdowns = folds.stream().mapToDouble(f -> f.validation().maxDrawdownPct()).sorted().toArray();
        double stdDev = standardDeviation(sharpes);
        double medianDrawdown = median(drawdowns);
        double dsr = deflatedSharpe(selected.averageValidationSharpe(), candidates.size(), windows.size());
        double pValue = 1.0 - normalCdf(dsr);
        Decision decision = decision(selected, folds, stdDev, medianDrawdown, pValue);
        return new Result(candidates, folds, selected.config(), stdDev, medianDrawdown, dsr, pValue,
            decision, List.of("Selection is based on historical data and is not a guarantee of future returns.",
                "The deflated Sharpe is an approximation; it does not model dependence, regime change, or transaction-cost uncertainty.",
                "This evaluator does not provide CSCV probability of backtest overfitting or a portfolio-level result."));
    }

    private static StrategyConfig withParameters(StrategyConfig base, Map<String, Object> overrides) {
        var params = new java.util.TreeMap<>(base.params());
        params.putAll(overrides);
        return StrategyConfig.create(base.variantId(), base.version(), base.strategyType(), params,
            base.overlays(), base.mode(), base.paperCapital(), base.current(), base.notes(), base.createdAt());
    }

    private static double average(List<BacktestResult> values, ToDoubleFunction<BacktestResult> metric) {
        return values.stream().mapToDouble(metric).average().orElse(0.0);
    }

    private static Decision decision(CandidateResult selected, List<FoldResult> folds, double stdDev,
                                     double medianDrawdown, double pValue) {
        boolean spiky = selected.averageTrainSharpe() > 1.0
            && selected.averageValidationSharpe() < selected.averageTrainSharpe() * 0.5;
        boolean unstable = stdDev > 1.0 || (medianDrawdown > 0
            && folds.stream().anyMatch(f -> f.validation().maxDrawdownPct() > 2 * medianDrawdown));
        if (spiky) return Decision.REJECT_SPIKY_OPTIMUM;
        if (unstable) return Decision.REJECT_UNSTABLE;
        if (selected.averageValidationSharpe() <= 0 || pValue > 0.1) return Decision.REJECT_OVERFIT;
        return Decision.ACCEPT;
    }

    private static double standardDeviation(double[] values) {
        if (values.length < 2) return 0.0;
        double mean = java.util.Arrays.stream(values).average().orElse(0.0);
        return Math.sqrt(java.util.Arrays.stream(values).map(v -> (v - mean) * (v - mean)).average().orElse(0.0));
    }

    private static double median(double[] sorted) {
        if (sorted.length == 0) return 0.0;
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    private static double deflatedSharpe(double sharpe, int trials, int folds) {
        if (trials < 2 || folds < 2) return sharpe;
        double expectedMax = Math.sqrt(2 * Math.log(trials));
        return (sharpe - expectedMax) * Math.sqrt(folds);
    }

    private static double normalCdf(double value) {
        return 0.5 * (1 + erf(value / Math.sqrt(2)));
    }

    // Abramowitz and Stegun 7.1.26, sufficient for a decision threshold rather than inference.
    private static double erf(double x) {
        double sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double polynomial = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
            - 0.284496736) * t + 0.254829592) * t;
        return sign * (1 - polynomial * Math.exp(-x * x));
    }
}
