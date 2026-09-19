package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Pure champion/challenger promotion-eligibility algorithm (plan &sect;7.4).
 *
 * <p>This class is deliberately self-contained: it knows nothing about {@code StrategyConfig},
 * positions, or persistence. Callers assemble the pre-computed inputs (tenure, closed-trade
 * P&amp;L after costs for each side, max drawdown, and walk-forward results) and this class only
 * applies the four promotion rules to them. This keeps the rules unit-testable without a
 * database or Spring context.</p>
 *
 * <p>Four conditions, all must hold for {@link Status#ELIGIBLE} (unless the sample-size gate
 * in condition 1 fails first, in which case the overall result is
 * {@link Status#INSUFFICIENT_SAMPLE} rather than a ranking):</p>
 * <ol>
 *   <li>Challenger has been in SHADOW mode on its current version for at least
 *       {@link #MIN_TENURE_DAYS} days AND has at least {@link #MIN_CLOSED_TRADES} closed
 *       paper trades.</li>
 *   <li>Challenger's paper expectancy (after costs) exceeds the champion's, with a bootstrap
 *       90% CI of the difference excluding zero. When either side has fewer than
 *       {@link #MIN_TRADES_PER_SIDE_FOR_BOOTSTRAP} trades, the bootstrap is not statistically
 *       reliable, so this condition instead compares the sign of walk-forward OOS Sharpe
 *       (challenger &gt; champion).</li>
 *   <li>Challenger's paper max drawdown is at most {@link #MAX_DRAWDOWN_MULTIPLIER} times the
 *       champion's max drawdown over the same period.</li>
 *   <li>Challenger's walk-forward OOS Sharpe is positive AND its deflated-Sharpe-style p-value
 *       is below {@link #MAX_DEFLATED_SHARPE_P_VALUE}. If no walk-forward run exists yet for the
 *       challenger, this condition is reported as not-met with a "no data yet" note rather than
 *       throwing.</li>
 * </ol>
 */
public final class PromotionEligibilityChecker {

    public static final long MIN_TENURE_DAYS = 60;
    public static final int MIN_CLOSED_TRADES = 30;
    /**
     * Below this many trades on either side, a bootstrap confidence interval on the mean
     * difference is not reliable (too few resamples to estimate the tail), so condition 2 falls
     * back to comparing walk-forward OOS Sharpe sign instead. This threshold is a documented
     * judgment call, not a value derived from the plan doc.
     */
    public static final int MIN_TRADES_PER_SIDE_FOR_BOOTSTRAP = 20;
    public static final double MAX_DRAWDOWN_MULTIPLIER = 1.2;
    public static final double MAX_DEFLATED_SHARPE_P_VALUE = 0.1;
    private static final int BOOTSTRAP_ITERATIONS = 2000;
    private static final double CI_LOWER_PERCENTILE = 0.05;
    private static final double CI_UPPER_PERCENTILE = 0.95;

    private final Random random;

    public PromotionEligibilityChecker() {
        this(new Random());
    }

    /** Package-visible seam for deterministic tests of the bootstrap resampling. */
    PromotionEligibilityChecker(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public enum Status { ELIGIBLE, NOT_ELIGIBLE, INSUFFICIENT_SAMPLE }

    /** One promotion rule's evaluated outcome, for a future UI checklist. */
    public record ConditionResult(String name, boolean met, String actualValue, String threshold, String note) { }

    public record Result(Status status, List<ConditionResult> conditions, List<String> notes) {
        public Result {
            conditions = List.copyOf(conditions);
            notes = List.copyOf(notes);
        }
    }

    /**
     * Pre-assembled inputs for one promotion-eligibility check. All monetary P&amp;L figures are
     * after transaction costs, consistent with how {@code PortfolioBacktestResult} reports
     * returns. Walk-forward fields are nullable to represent "no walk-forward run exists yet".
     */
    public record Input(
        long challengerTenureDaysInCurrentMode,
        List<BigDecimal> challengerClosedTradePnl,
        List<BigDecimal> championClosedTradePnl,
        double challengerMaxDrawdownPct,
        double championMaxDrawdownPct,
        Double challengerWalkForwardSharpe,
        Double challengerDeflatedSharpePValue,
        Double championWalkForwardSharpe
    ) {
        public Input {
            challengerClosedTradePnl = List.copyOf(challengerClosedTradePnl);
            championClosedTradePnl = List.copyOf(championClosedTradePnl);
        }

        public boolean challengerWalkForwardDataAvailable() {
            return challengerWalkForwardSharpe != null && challengerDeflatedSharpePValue != null;
        }
    }

    public Result evaluate(Input input) {
        Objects.requireNonNull(input, "input");
        List<ConditionResult> conditions = new ArrayList<>();

        boolean sufficientSample = input.challengerTenureDaysInCurrentMode() >= MIN_TENURE_DAYS
            && input.challengerClosedTradePnl().size() >= MIN_CLOSED_TRADES;
        conditions.add(new ConditionResult(
            "sample_size",
            sufficientSample,
            input.challengerTenureDaysInCurrentMode() + "d tenure, "
                + input.challengerClosedTradePnl().size() + " closed trades",
            ">=" + MIN_TENURE_DAYS + "d and >=" + MIN_CLOSED_TRADES + " trades",
            null));

        ConditionResult expectancy = evaluateExpectancy(input);
        conditions.add(expectancy);

        ConditionResult drawdown = evaluateDrawdown(input);
        conditions.add(drawdown);

        ConditionResult walkForward = evaluateWalkForward(input);
        conditions.add(walkForward);

        List<String> notes = new ArrayList<>();
        Status status;
        if (!sufficientSample) {
            status = Status.INSUFFICIENT_SAMPLE;
            notes.add("Challenger has not accumulated enough SHADOW tenure or closed paper trades "
                + "for a reliable ranking yet.");
        } else if (expectancy.met() && drawdown.met() && walkForward.met()) {
            status = Status.ELIGIBLE;
        } else {
            status = Status.NOT_ELIGIBLE;
        }
        return new Result(status, conditions, notes);
    }

    private ConditionResult evaluateExpectancy(Input input) {
        List<BigDecimal> challenger = input.challengerClosedTradePnl();
        List<BigDecimal> champion = input.championClosedTradePnl();
        double challengerExpectancy = mean(challenger);
        double championExpectancy = mean(champion);

        if (challenger.size() < MIN_TRADES_PER_SIDE_FOR_BOOTSTRAP
                || champion.size() < MIN_TRADES_PER_SIDE_FOR_BOOTSTRAP) {
            boolean sharpeAvailable = input.challengerWalkForwardSharpe() != null
                && input.championWalkForwardSharpe() != null;
            boolean met = sharpeAvailable
                && input.challengerWalkForwardSharpe() > input.championWalkForwardSharpe();
            String note = "Fewer than " + MIN_TRADES_PER_SIDE_FOR_BOOTSTRAP + " trades on one or both sides; "
                + "bootstrap CI is unreliable, so this falls back to comparing walk-forward OOS Sharpe sign."
                + (sharpeAvailable ? "" : " Walk-forward Sharpe unavailable for one or both sides.");
            return new ConditionResult("expectancy_vs_champion", met,
                "challenger Sharpe=" + input.challengerWalkForwardSharpe()
                    + ", champion Sharpe=" + input.championWalkForwardSharpe(),
                "challenger walk-forward Sharpe > champion walk-forward Sharpe (fallback)",
                note);
        }

        double[] bootstrapCi = bootstrapDifferenceCi(challenger, champion);
        boolean excludesZero = bootstrapCi[0] > 0 || bootstrapCi[1] < 0;
        boolean met = challengerExpectancy > championExpectancy && excludesZero;
        return new ConditionResult("expectancy_vs_champion", met,
            String.format("challenger=%.2f, champion=%.2f, 90%% CI of diff=[%.2f, %.2f]",
                challengerExpectancy, championExpectancy, bootstrapCi[0], bootstrapCi[1]),
            "challenger expectancy > champion expectancy, 90% CI of difference excludes zero",
            null);
    }

    private ConditionResult evaluateDrawdown(Input input) {
        double limit = input.championMaxDrawdownPct() * MAX_DRAWDOWN_MULTIPLIER;
        boolean met = input.challengerMaxDrawdownPct() <= limit;
        return new ConditionResult("max_drawdown",
            met,
            "challenger=" + input.challengerMaxDrawdownPct() + "%, champion=" + input.championMaxDrawdownPct() + "%",
            "challenger MaxDD <= " + MAX_DRAWDOWN_MULTIPLIER + " x champion MaxDD (" + limit + "%)",
            null);
    }

    private ConditionResult evaluateWalkForward(Input input) {
        if (!input.challengerWalkForwardDataAvailable()) {
            return new ConditionResult("walk_forward_significance", false, "n/a",
                "OOS Sharpe > 0 and deflated-Sharpe p-value < " + MAX_DEFLATED_SHARPE_P_VALUE,
                "No walk-forward run exists yet for the challenger.");
        }
        boolean met = input.challengerWalkForwardSharpe() > 0
            && input.challengerDeflatedSharpePValue() < MAX_DEFLATED_SHARPE_P_VALUE;
        return new ConditionResult("walk_forward_significance", met,
            "Sharpe=" + input.challengerWalkForwardSharpe() + ", p=" + input.challengerDeflatedSharpePValue(),
            "OOS Sharpe > 0 and deflated-Sharpe p-value < " + MAX_DEFLATED_SHARPE_P_VALUE,
            null);
    }

    private double[] bootstrapDifferenceCi(List<BigDecimal> challenger, List<BigDecimal> champion) {
        double[] challengerValues = toDoubles(challenger);
        double[] championValues = toDoubles(champion);
        double[] diffs = new double[BOOTSTRAP_ITERATIONS];
        for (int i = 0; i < BOOTSTRAP_ITERATIONS; i++) {
            double challengerMean = resampleMean(challengerValues);
            double championMean = resampleMean(championValues);
            diffs[i] = challengerMean - championMean;
        }
        java.util.Arrays.sort(diffs);
        double lower = percentile(diffs, CI_LOWER_PERCENTILE);
        double upper = percentile(diffs, CI_UPPER_PERCENTILE);
        return new double[] {lower, upper};
    }

    private double resampleMean(double[] values) {
        double sum = 0.0;
        for (int i = 0; i < values.length; i++) {
            sum += values[random.nextInt(values.length)];
        }
        return sum / values.length;
    }

    private static double percentile(double[] sortedValues, double fraction) {
        if (sortedValues.length == 0) return 0.0;
        int index = (int) Math.floor(fraction * (sortedValues.length - 1));
        return sortedValues[Math.max(0, Math.min(sortedValues.length - 1, index))];
    }

    private static double[] toDoubles(List<BigDecimal> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).doubleValue();
        }
        return result;
    }

    private static double mean(List<BigDecimal> values) {
        if (values.isEmpty()) return 0.0;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return sum.doubleValue() / values.size();
    }
}
