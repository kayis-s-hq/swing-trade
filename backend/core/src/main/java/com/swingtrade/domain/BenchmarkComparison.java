package com.swingtrade.domain;

/**
 * Compares a strategy's return with a named benchmark over the same evaluation window.
 *
 * @param benchmarkName       stable identifier for the benchmark (for example, BUY_AND_HOLD)
 * @param strategyReturnPct   strategy total return, in percent
 * @param benchmarkReturnPct  benchmark return, in percent
 * @param excessReturnPct     strategy return minus benchmark return, in percentage points
 */
public record BenchmarkComparison(
    String benchmarkName,
    double strategyReturnPct,
    double benchmarkReturnPct,
    double excessReturnPct
) {
    public static final String BUY_AND_HOLD = "BUY_AND_HOLD";

    public BenchmarkComparison {
        if (benchmarkName == null || benchmarkName.isBlank()) {
            throw new IllegalArgumentException("Benchmark name cannot be blank");
        }
    }

    public static BenchmarkComparison buyAndHold(double strategyReturnPct, double benchmarkReturnPct) {
        return new BenchmarkComparison(BUY_AND_HOLD, strategyReturnPct, benchmarkReturnPct,
            strategyReturnPct - benchmarkReturnPct);
    }

    /** Returns a comparison when a benchmark cannot be calculated for the evaluation window. */
    public static BenchmarkComparison unavailable(double strategyReturnPct) {
        return new BenchmarkComparison(BUY_AND_HOLD, strategyReturnPct, 0.0, 0.0);
    }
}
