package com.swingtrade.strategy;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of a shared-capital portfolio backtest.
 *
 * <p>The equity curve is event-driven: it records the initial value and each date on which an
 * entry or exit is processed. Open positions are valued at their entry notional because the
 * existing {@link BacktestTrade} contract does not carry daily candles; this avoids inventing
 * interim prices while still enforcing capital and concurrency constraints.</p>
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
