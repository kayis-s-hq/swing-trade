package com.swingtrade.strategy;

/**
 * Full metrics set for a {@link PortfolioBacktestEngine} run (plan §6.2). All percentage fields
 * are expressed as e.g. {@code 12.5} meaning 12.5%, matching the existing {@code BacktestResult}
 * convention.
 *
 * <p>Benchmark alpha/beta against Nifty buy&amp;hold is deliberately stubbed at {@code null} -
 * see {@link BenchmarkComparison}. Real Nifty series ingestion is deferred to when RS_NIFTY
 * (plan §5.4) is built and a benchmark data source has to exist anyway (plan A3/F16).
 */
public record PortfolioMetrics(
    double cagrPct,
    double totalReturnPct,
    double annualizedVolatilityPct,
    double sharpeRatio,
    double sortinoRatio,
    double maxDrawdownPct,
    int maxDrawdownDurationDays,
    double calmarRatio,
    int totalTrades,
    double winRatePct,
    double winRateCiLowPct,
    double winRateCiHighPct,
    double avgWinPct,
    double avgLossPct,
    double payoffRatio,
    double expectancyR,
    double expectancyRCiLow,
    double expectancyRCiHigh,
    double expectancyRupees,
    double profitFactor,
    double exposurePct,
    double avgHoldingDays,
    double turnover,
    double costDragPct,
    BenchmarkComparison benchmarkComparison
) {

    /**
     * Wilson-score 95% CI on win rate, in [0,1] each bound. {@code null} benchmark stub per the
     * class doc; kept nested so callers see explicitly that it's a stub, not a silently-omitted
     * metric.
     */
    public record BenchmarkComparison(boolean available, Double alpha, Double beta, String note) {
        public static BenchmarkComparison stub() {
            return new BenchmarkComparison(false, null, null,
                "Nifty buy&hold benchmark not wired up (plan A3/F16) - deferred to RS_NIFTY (§5.4) "
                    + "when a benchmark index data source is ingested anyway.");
        }
    }
}
