package com.swingtrade.strategy;

import java.util.List;

/** Full output of a {@link PortfolioBacktestEngine} run (plan §6.1/§6.2/§6.6). */
public record PortfolioBacktestResult(
    List<PortfolioTrade> trades,
    List<DailyEquityPoint> equityCurve,
    List<ExcludedSymbol> excludedSymbols,
    PortfolioMetrics metrics,
    java.util.Map<String, Double> perSymbolPnlContributionPct,
    java.util.Map<ExitReason, Integer> exitReasonBreakdown,
    java.util.Map<Integer, Double> perYearReturnPct,
    boolean symbolConcentrationWarning
) {
}
