package com.swingtrade.strategy;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of a shared-capital portfolio backtest.
 *
 * <p>The curve is daily over the requested window. Since the existing {@link BacktestTrade}
 * contract carries no intermediate candles, open positions are marked at their entry notional;
 * the point still exposes settled cash and unsettled exit proceeds so cash availability is not
 * confused with economic equity.</p>
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
