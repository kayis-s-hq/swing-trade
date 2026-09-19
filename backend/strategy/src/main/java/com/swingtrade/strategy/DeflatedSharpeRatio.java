package com.swingtrade.strategy;

/**
 * Deflated Sharpe Ratio (plan §6.4), after:
 * Bailey, D. H., &amp; Lopez de Prado, M. (2014). "The Deflated Sharpe Ratio: Correcting for
 * Selection Bias, Backtest Overfitting, and Non-Normality." Journal of Portfolio Management,
 * 40(5), 94-107.
 *
 * <p>The DSR tests the observed Sharpe ratio against the expected maximum Sharpe ratio one would
 * see purely by chance after {@code numTrials} independent trials (here, {@code N} = distinct
 * {@code params_hash} values tested for a strategy type, queried from
 * {@code strategy_experiment_log} - plan §6.4). It returns the probability that the true Sharpe
 * ratio exceeds zero, deflating for that selection bias: as {@code numTrials} grows, the bar for
 * "significant" rises (expectedMaxSharpe grows with sqrt(2*ln(N))), so the same observed Sharpe
 * yields a lower DSR the more variants were tried.
 *
 * <p>This is a simplified (non-normality skew/kurtosis terms omitted) but properly-biased
 * implementation: it is deliberately tested only for its monotonicity properties (plan §6.7),
 * since exact reference DSR values are hard to source independently.
 */
public final class DeflatedSharpeRatio {

    private DeflatedSharpeRatio() {
    }

    private static final double EULER_MASCHERONI = 0.5772156649015329;

    /**
     * @param observedSharpe   the (annualised) Sharpe ratio actually observed for the variant
     *                         under test
     * @param numTrials        N = number of independent params_hash trials evaluated for this
     *                         strategy type (plan §6.4); must be &gt;= 1
     * @param numObservations  number of return observations (e.g. daily returns) behind
     *                         {@code observedSharpe}; must be &gt; 1
     * @param sharpeStdError   standard error of the Sharpe-ratio estimator across trials
     *                         (variance of the trials' Sharpe ratios); 0 is treated as
     *                         "no dispersion information available" and falls back to a
     *                         Sharpe-ratio-scaled default so the deflation still increases with N
     * @return the deflated Sharpe ratio: P(true Sharpe &gt; 0 | selection bias from numTrials)
     */
    public static double compute(double observedSharpe, int numTrials, int numObservations, double sharpeStdError) {
        if (numTrials < 1) {
            throw new IllegalArgumentException("numTrials must be >= 1");
        }
        if (numObservations <= 1) {
            throw new IllegalArgumentException("numObservations must be > 1");
        }
        double stdError = sharpeStdError > 0 ? sharpeStdError : Math.max(0.1, Math.abs(observedSharpe) * 0.5 + 0.1);

        double expectedMaxSharpe = numTrials <= 1
            ? 0.0
            : stdError * ((1 - EULER_MASCHERONI) * inverseNormalCdf(1.0 - 1.0 / numTrials)
                + EULER_MASCHERONI * inverseNormalCdf(1.0 - 1.0 / (numTrials * Math.E)));

        double z = (observedSharpe - expectedMaxSharpe) * Math.sqrt(numObservations - 1);
        return normalCdf(z);
    }

    /** Standard normal CDF via the Abramowitz-Stegun erf approximation (double precision-adequate). */
    static double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    /** Inverse standard normal CDF (Acklam's rational approximation). */
    static double inverseNormalCdf(double p) {
        if (p <= 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (p >= 1.0) {
            return Double.POSITIVE_INFINITY;
        }
        // Beasley-Springer-Moro-style approximation via Newton refinement on erf-based normalCdf.
        double lo = -10.0;
        double hi = 10.0;
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2.0;
            if (normalCdf(mid) < p) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0;
    }

    private static double erf(double x) {
        // Abramowitz & Stegun 7.1.26, max error ~1.5e-7.
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
            + 0.254829592) * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }
}
