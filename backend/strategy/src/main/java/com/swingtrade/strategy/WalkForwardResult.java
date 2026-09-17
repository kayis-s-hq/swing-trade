package com.swingtrade.strategy;

import java.util.List;

/**
 * Aggregated walk-forward output (plan §6.3): the concatenated OOS equity curve across every
 * fold, the per-fold results, and the stability flags.
 *
 * @param folds                    per-fold OOS results, in chronological order
 * @param concatenatedEquityCurve  every fold's equity curve concatenated in order (OOS equity
 *                                 path across the whole walk-forward run)
 * @param sharpeStdDev             standard deviation of the per-fold Sharpe ratios (stability
 *                                 metric, plan §6.3)
 * @param unstable                 true when any fold's MaxDD exceeds 2x the median fold MaxDD
 *                                 (plan §6.3: "flag variant if any fold MaxDD > 2x median")
 * @param holdoutUnlocked          whether this run was allowed to use the reserved hold-out
 *                                 period (plan §6.3: logged/flagged, never silent)
 */
public record WalkForwardResult(List<WalkForwardFold> folds, List<DailyEquityPoint> concatenatedEquityCurve,
                                 double sharpeStdDev, boolean unstable, boolean holdoutUnlocked) {
}
