package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rolling walk-forward evaluation over {@link PortfolioBacktestEngine} (plan §6.3): builds
 * train-{@code trainMonths}/test-{@code testMonths}/step-{@code stepMonths} folds across
 * {@code [rangeStart, rangeEnd]}, runs each fold's test window out-of-sample with the caller's
 * fixed {@code params}, and reports the concatenated OOS equity curve plus stability metrics.
 *
 * <p>The train window itself is not used to fit anything here - the plan explicitly defers grid
 * search to a later phase ("optional grid search endpoint in later phase, capped trials"); a
 * fixed set of params is evaluated OOS per fold "for stability". The train window length still
 * matters in that it determines each fold's earliest usable evaluation date, keeping folds
 * comparable to a true train/test walk-forward even though selection isn't implemented yet.
 */
public final class WalkForwardRunner {

    private static final Logger log = LoggerFactory.getLogger(WalkForwardRunner.class);

    private final PortfolioBacktestEngine engine;

    public WalkForwardRunner() {
        this(new PortfolioBacktestEngine());
    }

    public WalkForwardRunner(PortfolioBacktestEngine engine) {
        this.engine = engine;
    }

    /**
     * @param candlesBySymbol chronologically-ordered candle history per symbol, spanning at least
     *                        {@code trainMonths} of warm-up before {@code rangeStart}
     * @param rangeStart      earliest date any fold's test window may start
     * @param rangeEnd        latest date any fold's test window may end (subject to the hold-out)
     */
    public WalkForwardResult run(Map<String, List<OhlcvCandle>> candlesBySymbol, SignalStrategy strategy,
                                  StrategyParamsView params, PortfolioBacktestConfig backtestConfig,
                                  WalkForwardConfig walkForwardConfig, LocalDate rangeStart, LocalDate rangeEnd,
                                  double annualRiskFreeRatePct) {
        LocalDate holdoutStart = walkForwardConfig.holdoutStart(rangeEnd);
        LocalDate effectiveEnd = walkForwardConfig.unlockHoldout() ? rangeEnd : holdoutStart.minusDays(1);
        if (walkForwardConfig.unlockHoldout()) {
            log.warn("Walk-forward hold-out UNLOCKED: folds may use data from {} to {} (normally reserved)",
                holdoutStart, rangeEnd);
        }

        List<WalkForwardFold> folds = new ArrayList<>();
        List<DailyEquityPoint> concatenated = new ArrayList<>();

        LocalDate testStart = rangeStart.plusMonths(walkForwardConfig.trainMonths());
        while (true) {
            LocalDate testEnd = testStart.plusMonths(walkForwardConfig.testMonths()).minusDays(1);
            if (testEnd.isAfter(effectiveEnd)) {
                break;
            }
            PortfolioBacktestResult foldResult;
            try {
                foldResult = engine.run(candlesBySymbol, strategy, params, backtestConfig, testStart, testEnd,
                    annualRiskFreeRatePct);
            } catch (IllegalStateException e) {
                // No surviving symbols / no trading dates for this fold - skip it rather than
                // aborting the whole walk-forward run.
                log.warn("Skipping walk-forward fold {}..{}: {}", testStart, testEnd, e.getMessage());
                testStart = testStart.plusMonths(walkForwardConfig.stepMonths());
                continue;
            }
            folds.add(new WalkForwardFold(testStart, testEnd, foldResult));
            concatenated.addAll(foldResult.equityCurve());
            testStart = testStart.plusMonths(walkForwardConfig.stepMonths());
        }

        double[] foldSharpes = folds.stream().mapToDouble(f -> f.result().metrics().sharpeRatio()).toArray();
        double sharpeStdDev = MetricsCalculator.stdDev(foldSharpes);

        double[] foldMaxDrawdowns = folds.stream().mapToDouble(f -> f.result().metrics().maxDrawdownPct()).toArray();
        boolean unstable = isUnstable(foldMaxDrawdowns);

        return new WalkForwardResult(folds, concatenated, sharpeStdDev, unstable, walkForwardConfig.unlockHoldout());
    }

    /** Plan §6.3: "flag variant if any fold MaxDD > 2x median fold MaxDD". */
    static boolean isUnstable(double[] foldMaxDrawdowns) {
        if (foldMaxDrawdowns.length < 2) {
            return false;
        }
        double median = median(foldMaxDrawdowns);
        if (median <= 0.0) {
            return false;
        }
        for (double dd : foldMaxDrawdowns) {
            if (dd > 2.0 * median) {
                return true;
            }
        }
        return false;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
