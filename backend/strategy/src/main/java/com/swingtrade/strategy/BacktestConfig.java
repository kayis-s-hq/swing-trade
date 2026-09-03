package com.swingtrade.strategy;

/**
 * Tunable parameters for a swing-trade backtest run.
 *
 * @param slippagePct           fraction added to entry price / subtracted from exit price to model execution slippage (0.001 = 0.1%)
 * @param brokeragePerTrade     flat brokerage charged once per round-trip trade
 * @param riskPerTradePct       fraction of capital risked per trade, used for position sizing (0.01 = 1%)
 * @param initialCapital        starting capital for the simulation
 * @param maxConcurrentPositions reserved for future multi-symbol portfolio simulation; a single-symbol backtest never holds more than one open position by construction
 * @param atrMultiplierStop     ATR multiple subtracted from entry price to set the stop loss
 * @param rewardRiskRatio       multiple of risk (entry - stop) added to entry price to set the target
 * @param maxHoldingDays        maximum bars a position may stay open before a time-stop exit
 * @param signalExitEnabled     opt-in flag to also exit on the same trend/RSI confluence used by
 *                              {@code PriceActionSignalEngine}'s live SELL signal (checked after
 *                              STOP_LOSS/TARGET_HIT, before TREND_BREAK/TIME_STOP). Defaults to
 *                              {@code false} so existing backtest behavior is unchanged unless a
 *                              caller opts in.
 * @param trendBreakStreakDays  consecutive closes below EMA20 required to trigger a TREND_BREAK
 *                              exit (checked after STOP_LOSS/TARGET_HIT/SIGNAL_EXIT, before
 *                              TIME_STOP). Set higher than {@code maxHoldingDays} to effectively
 *                              disable it and let positions run to STOP_LOSS/TARGET_HIT/TIME_STOP
 *                              only. Defaults to disabled: TREND_BREAK is a backtest-only
 *                              construct with no live-trading counterpart (the paper-trading
 *                              engine only ever exits on STOP_LOSS/TARGET_HIT price levels or a
 *                              live SELL signal), so leaving it on by default tested a strategy
 *                              variant the live system doesn't run. Measured on 2026-08-30 across
 *                              the 10-symbol watchlist: disabling it moved overall win rate from
 *                              39.5% to 47.5%, Sharpe from -0.02 to +0.08, and total P&amp;L from
 *                              +16.4K to +38.2K - the 13 trades it used to cut early at a 15.4%
 *                              win rate mostly went on to win at TIME_STOP instead.
 */
public record BacktestConfig(
    double slippagePct,
    double brokeragePerTrade,
    double riskPerTradePct,
    double initialCapital,
    int maxConcurrentPositions,
    double atrMultiplierStop,
    double rewardRiskRatio,
    int maxHoldingDays,
    boolean signalExitEnabled,
    int trendBreakStreakDays
) {

    public static BacktestConfig defaults() {
        return new BacktestConfig(
                0.001, 20.0, 0.01,
                500_000.0, 5, 2.0,
                2.5, 20, false, 21
        );
    }
}
