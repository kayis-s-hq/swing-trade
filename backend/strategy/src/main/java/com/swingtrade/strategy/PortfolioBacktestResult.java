package com.swingtrade.strategy;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of a shared-capital portfolio backtest.
 *
 * <p>The production portfolio path marks open positions at each supplied trading candle close;
 * callers using the legacy trade-only engine overload retain the entry-notional fallback. The
 * point exposes settled cash and unsettled exit proceeds so cash availability is not confused
 * with economic equity.</p>
 */
public record PortfolioBacktestResult(
        LocalDate evaluationStart,
        LocalDate evaluationEnd,
        double initialCapital,
        double finalCapital,
        double totalReturn,
        double maxDrawdownPct,
        double sharpeRatio,
        double cagrPct,
        double sortinoRatio,
        double calmarRatio,
        int totalTrades,
        int winningTrades,
        int rejectedTrades,
        List<BacktestTrade> trades,
        List<PortfolioEquityPoint> equityCurve
) {
    public PortfolioBacktestResult {
        trades = List.copyOf(trades);
        equityCurve = List.copyOf(equityCurve);
    }
}
