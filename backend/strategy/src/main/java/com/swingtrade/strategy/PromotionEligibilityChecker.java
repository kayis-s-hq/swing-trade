package com.swingtrade.strategy;

import java.util.List;

/**
 * Champion/challenger promotion eligibility checklist (plan §7.4). Pure/stateless, mirroring
 * {@link MetricsCalculator}/{@link DeflatedSharpeRatio}/{@link WalkForwardRunner}: this class
 * only evaluates the four conditions from data already assembled by the caller (paper trades,
 * paper MaxDD, walk-forward OOS Sharpe, DSR p-value, tenure). It never queries a store or
 * repository and never mutates anything - see {@link PromotionEligibilityResult} for the
 * read-only checklist it returns.
 *
 * <p><b>Scope note (deliberately out of scope for this class):</b> the plan's §7.4 "manual
 * confirm" promotion action, any REST endpoint, and the dashboard checklist UI (§8) are a later
 * phase; this class is only the underlying eligibility-check logic those would call.
 */
public final class PromotionEligibilityChecker {

    /**
     * Plan §7.4: "≥ 60 calendar days in SHADOW on current version".
     */
    static final long MIN_TENURE_DAYS = 60;

    /**
     * Plan §7.4: "≥ 30 closed paper trades", and the same count doubles as the trade-count floor
     * below which condition 4's overall sample is considered too thin to judge at all (tenure
     * gate).
     */
    static final int MIN_CLOSED_TRADES = 30;

    /**
     * Plan §7.4 condition 2 is terse on the bootstrap-CI fallback threshold ("if trade count too
     * low"). {@link MetricsCalculator#bootstrapMeanCi} is a resampling-with-replacement CI on the
     * per-trade R-multiples; below ~20 trades per side that resampling distribution is too sparse
     * (too few distinct values to resample from) to trust a CI's exclude-zero verdict, so this
     * checker falls back to the walk-forward OOS Sharpe sign comparison instead. This is a
     * documented judgment call, not a value taken from the plan text.
     */
    static final int MIN_TRADES_FOR_BOOTSTRAP_CI = 20;

    /**
     * Plan §7.4 condition 3: "Paper MaxDD ≤ 1.2 × champion MaxDD over same period."
     */
    static final double MAX_DRAWDOWN_MULTIPLIER = 1.2;

    /**
     * Plan §7.4 condition 4: "DSR p < 0.1" (matches the leaderboard's existing "likely noise"
     * badge threshold in §6.4).
     */
    static final double MAX_DSR_P_VALUE = 0.1;

    private static final int BOOTSTRAP_SAMPLES = 2000;
    private static final long BOOTSTRAP_SEED = 42L;

    private PromotionEligibilityChecker() {
    }

    /**
     * Evaluates all four promotion conditions for a challenger against its champion.
     *
     * @param tenureCalendarDays       calendar days the challenger has spent in SHADOW on its
     *                                 current version
     * @param challengerTrades         challenger's closed paper trades (exit only, same period as
     *                                 {@code championTrades})
     * @param championTrades           champion's closed paper trades over the same period
     * @param challengerMaxDrawdownPct challenger's paper MaxDD (%, positive) over the period
     * @param championMaxDrawdownPct   champion's paper MaxDD (%, positive) over the same period
     * @param challengerWalkForwardOosSharpe challenger's walk-forward OOS Sharpe, or {@code null}
     *                                       if no walk-forward run exists yet
     * @param championWalkForwardOosSharpe   champion's walk-forward OOS Sharpe, used only as the
     *                                       bootstrap-CI fallback's sign-consistency reference;
     *                                       {@code null} if unavailable
     * @param challengerDsrPValue      challenger's DSR p-value (plan §6.4), or {@code null} if no
     *                                 walk-forward/experiment-log run exists yet
     */
    public static PromotionEligibilityResult evaluate(
        long tenureCalendarDays,
        List<PortfolioTrade> challengerTrades,
        List<PortfolioTrade> championTrades,
        double challengerMaxDrawdownPct,
        double championMaxDrawdownPct,
        Double challengerWalkForwardOosSharpe,
        Double championWalkForwardOosSharpe,
        Double challengerDsrPValue
    ) {
        challengerTrades = challengerTrades == null ? List.of() : challengerTrades;
        championTrades = championTrades == null ? List.of() : championTrades;

        ConditionResult tenureCondition = evaluateTenure(tenureCalendarDays, challengerTrades.size());
        boolean insufficientSample = !tenureCondition.met();

        ConditionResult expectancyCondition = evaluateExpectancy(challengerTrades, championTrades,
            challengerWalkForwardOosSharpe, championWalkForwardOosSharpe);
        ConditionResult drawdownCondition = evaluateDrawdown(challengerMaxDrawdownPct, championMaxDrawdownPct);
        ConditionResult walkForwardCondition = evaluateWalkForward(challengerWalkForwardOosSharpe, challengerDsrPValue);

        PromotionStatus status;
        if (insufficientSample) {
            status = PromotionStatus.INSUFFICIENT_SAMPLE;
        } else if (tenureCondition.met() && expectancyCondition.met() && drawdownCondition.met()
            && walkForwardCondition.met()) {
            status = PromotionStatus.ELIGIBLE;
        } else {
            status = PromotionStatus.NOT_ELIGIBLE;
        }

        return new PromotionEligibilityResult(status, tenureCondition, expectancyCondition, drawdownCondition,
            walkForwardCondition);
    }

    /** Condition 1: tenure + sample size gate. */
    private static ConditionResult evaluateTenure(long tenureCalendarDays, int closedTradeCount) {
        boolean met = tenureCalendarDays >= MIN_TENURE_DAYS && closedTradeCount >= MIN_CLOSED_TRADES;
        String actual = "tenureDays=" + tenureCalendarDays + ", closedTrades=" + closedTradeCount;
        String threshold = "tenureDays>=" + MIN_TENURE_DAYS + ", closedTrades>=" + MIN_CLOSED_TRADES;
        return ConditionResult.of(met, actual, threshold);
    }

    /**
     * Condition 2: challenger expectancy (after costs, i.e. net P&L per trade already reflected
     * in {@link PortfolioTrade#pnl()}) must exceed champion's, with the difference's bootstrap
     * 90% CI excluding zero. Below {@link #MIN_TRADES_FOR_BOOTSTRAP_CI} trades on either side, the
     * bootstrap CI is considered too unreliable and this falls back to walk-forward OOS Sharpe
     * sign consistency (challenger's OOS Sharpe > champion's) per the plan's fallback clause.
     */
    private static ConditionResult evaluateExpectancy(List<PortfolioTrade> challengerTrades,
                                                        List<PortfolioTrade> championTrades,
                                                        Double challengerOosSharpe, Double championOosSharpe) {
        boolean bootstrapReliable = challengerTrades.size() >= MIN_TRADES_FOR_BOOTSTRAP_CI
            && championTrades.size() >= MIN_TRADES_FOR_BOOTSTRAP_CI;

        double challengerExpectancy = expectancyRupees(challengerTrades);
        double championExpectancy = expectancyRupees(championTrades);

        if (bootstrapReliable) {
            double[] diffs = pairwiseDiffSample(challengerTrades, championTrades);
            double[] ci = MetricsCalculator.bootstrapMeanCi(diffs, BOOTSTRAP_SAMPLES, BOOTSTRAP_SEED);
            boolean excludesZero = ci[0] > 0.0;
            boolean met = challengerExpectancy > championExpectancy && excludesZero;
            String actual = String.format("challengerExpectancy=%.2f, championExpectancy=%.2f, diffCi90=[%.2f, %.2f]",
                challengerExpectancy, championExpectancy, ci[0], ci[1]);
            return ConditionResult.of(met, actual,
                "challenger expectancy > champion AND bootstrap 90% CI of difference excludes 0");
        }

        // Fallback: too few trades for a meaningful bootstrap CI - fall back to walk-forward OOS
        // Sharpe sign consistency (challenger favoured over champion).
        boolean haveBothSharpes = challengerOosSharpe != null && championOosSharpe != null;
        boolean met = haveBothSharpes && challengerOosSharpe > championOosSharpe;
        String actual = String.format(
            "trade count too low for bootstrap CI (challenger=%d, champion=%d trades); fallback: challengerOosSharpe=%s, championOosSharpe=%s",
            challengerTrades.size(), championTrades.size(),
            challengerOosSharpe == null ? "n/a" : String.format("%.3f", challengerOosSharpe),
            championOosSharpe == null ? "n/a" : String.format("%.3f", championOosSharpe));
        return ConditionResult.of(met, actual,
            "fallback (< " + MIN_TRADES_FOR_BOOTSTRAP_CI + " trades/side): challenger walk-forward OOS Sharpe > champion's");
    }

    /** Condition 3: drawdown guard. */
    private static ConditionResult evaluateDrawdown(double challengerMaxDrawdownPct, double championMaxDrawdownPct) {
        double allowedMax = MAX_DRAWDOWN_MULTIPLIER * championMaxDrawdownPct;
        boolean met = challengerMaxDrawdownPct <= allowedMax;
        String actual = String.format("challengerMaxDD=%.2f%%, championMaxDD=%.2f%%", challengerMaxDrawdownPct,
            championMaxDrawdownPct);
        String threshold = String.format("challengerMaxDD <= %.1fx championMaxDD (%.2f%%)", MAX_DRAWDOWN_MULTIPLIER,
            allowedMax);
        return ConditionResult.of(met, actual, threshold);
    }

    /**
     * Condition 4: walk-forward + overfitting guard. Reports "not met, no data" (met=false, no
     * exception) when no walk-forward run exists yet for the challenger.
     */
    private static ConditionResult evaluateWalkForward(Double oosSharpe, Double dsrPValue) {
        if (oosSharpe == null || dsrPValue == null) {
            return ConditionResult.of(false, "no walk-forward/DSR data available yet",
                "OOS Sharpe > 0 AND DSR p < " + MAX_DSR_P_VALUE);
        }
        boolean met = oosSharpe > 0.0 && dsrPValue < MAX_DSR_P_VALUE;
        String actual = String.format("oosSharpe=%.3f, dsrPValue=%.3f", oosSharpe, dsrPValue);
        String threshold = "oosSharpe > 0 AND dsrPValue < " + MAX_DSR_P_VALUE;
        return ConditionResult.of(met, actual, threshold);
    }

    private static double expectancyRupees(List<PortfolioTrade> trades) {
        if (trades.isEmpty()) {
            return 0.0;
        }
        return trades.stream().mapToDouble(t -> t.pnl().doubleValue()).average().orElse(0.0);
    }

    /**
     * Builds a sample of per-trade P&L differences to bootstrap: paired index-by-index where both
     * lists overlap (same-period trade sequences aren't naturally paired 1:1, so this pairs by
     * chronological order as the best available proxy), then any remaining unmatched trades on the
     * longer side are compared against the other side's mean so they still contribute evidence
     * without fabricating a false pairing.
     */
    private static double[] pairwiseDiffSample(List<PortfolioTrade> challengerTrades, List<PortfolioTrade> championTrades) {
        int n = Math.max(challengerTrades.size(), championTrades.size());
        double championMean = expectancyRupees(championTrades);
        double challengerMean = expectancyRupees(challengerTrades);
        double[] diffs = new double[n];
        for (int i = 0; i < n; i++) {
            double c = i < challengerTrades.size() ? challengerTrades.get(i).pnl().doubleValue() : challengerMean;
            double h = i < championTrades.size() ? championTrades.get(i).pnl().doubleValue() : championMean;
            diffs[i] = c - h;
        }
        return diffs;
    }

    /** Overall promotion status (plan §7.4's explicit three-way UI distinction). */
    public enum PromotionStatus {
        ELIGIBLE,
        NOT_ELIGIBLE,
        INSUFFICIENT_SAMPLE
    }

    /** One checklist condition's outcome, actual value and threshold (for a future UI checklist). */
    public record ConditionResult(boolean met, String actualValue, String threshold) {
        static ConditionResult of(boolean met, String actualValue, String threshold) {
            return new ConditionResult(met, actualValue, threshold);
        }
    }

    /** The full plan §7.4 checklist result: overall status plus each condition's breakdown. */
    public record PromotionEligibilityResult(
        PromotionStatus status,
        ConditionResult tenureAndSampleSize,
        ConditionResult expectancyVsChampion,
        ConditionResult drawdownGuard,
        ConditionResult walkForwardAndOverfitting
    ) {
    }
}
