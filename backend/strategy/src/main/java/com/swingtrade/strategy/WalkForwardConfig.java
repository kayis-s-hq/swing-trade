package com.swingtrade.strategy;

import java.time.LocalDate;

/**
 * Rolling walk-forward window configuration (plan §6.3): train {@code trainMonths} / test
 * {@code testMonths} / step {@code stepMonths}. "Train" here does not mean fitting new params -
 * the plan explicitly defers grid search to a later phase - it means the fixed {@code params}
 * passed to {@link WalkForwardRunner#run} are evaluated out-of-sample on each rolling test
 * window; the train window only bounds how much history is available for indicator warm-up
 * before each test window starts.
 *
 * @param trainMonths        length of the (unused-for-fitting) train window, in months
 * @param testMonths         length of each out-of-sample test window, in months
 * @param stepMonths         how far the window rolls forward between folds, in months
 * @param holdoutMonths      months reserved at the end of the overall range that are excluded
 *                            from folds unless {@code unlockHoldout} is true (plan §6.3)
 * @param unlockHoldout       when true, folds are allowed to run into the reserved hold-out
 *                            period; every such run is logged (plan §6.3: "logged when unlocked")
 */
public record WalkForwardConfig(int trainMonths, int testMonths, int stepMonths, int holdoutMonths,
                                 boolean unlockHoldout) {
    public WalkForwardConfig {
        if (trainMonths <= 0 || testMonths <= 0 || stepMonths <= 0) {
            throw new IllegalArgumentException("trainMonths/testMonths/stepMonths must be positive");
        }
        if (holdoutMonths < 0) {
            throw new IllegalArgumentException("holdoutMonths cannot be negative");
        }
    }

    /** Plan §6.3 defaults: train 24m / test 6m / step 6m, 6m global hold-out, locked. */
    public static WalkForwardConfig defaults() {
        return new WalkForwardConfig(24, 6, 6, 6, false);
    }

    LocalDate holdoutStart(LocalDate overallEnd) {
        return holdoutMonths == 0 ? overallEnd.plusDays(1) : overallEnd.minusMonths(holdoutMonths).plusDays(1);
    }
}
