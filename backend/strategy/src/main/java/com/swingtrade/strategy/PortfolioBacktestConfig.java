package com.swingtrade.strategy;

import java.math.BigDecimal;

/**
 * Tuning parameters for {@link PortfolioBacktestEngine} (plan §6.1). Deliberately mirrors the
 * shape of {@code paper_trading_portfolio}'s settings (single source of truth for capital/risk
 * limits between live paper trading and backtesting) rather than inventing a parallel set of
 * knobs.
 *
 * @param initialCapital            starting cash
 * @param maxConcurrentPositions    hard cap on simultaneously open positions
 * @param maxCapitalPerPositionPct  max fraction of current equity a single position may consume,
 *                                  in [0,1]
 * @param riskPerTradePct           fraction of current equity risked per trade (entry-to-stop),
 *                                  in [0,1] - position size is {@code riskPerTradePct * equity /
 *                                  (entry - stop)}, capped by {@link #maxCapitalPerPositionPct}
 * @param slippagePct               fractional slippage applied to both entry and exit fills
 * @param costModel                 brokerage/cost model applied per round-trip (reuses the
 *                                  existing {@link BacktestCostModel} SPI)
 * @param atrPeriodForRanking       ATR period used to compute each candidate's ATR% for the
 *                                  capacity-limited ranking tiebreak (plan §6.1: lower ATR% wins)
 */
public record PortfolioBacktestConfig(
    BigDecimal initialCapital,
    int maxConcurrentPositions,
    BigDecimal maxCapitalPerPositionPct,
    BigDecimal riskPerTradePct,
    double slippagePct,
    BacktestCostModel costModel,
    int atrPeriodForRanking,
    BigDecimal brokeragePerTrade
) {
    public PortfolioBacktestConfig {
        if (initialCapital == null || initialCapital.signum() <= 0) {
            throw new IllegalArgumentException("initialCapital must be positive");
        }
        if (maxConcurrentPositions <= 0) {
            throw new IllegalArgumentException("maxConcurrentPositions must be positive");
        }
        if (maxCapitalPerPositionPct == null || maxCapitalPerPositionPct.signum() <= 0
            || maxCapitalPerPositionPct.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("maxCapitalPerPositionPct must be in (0,1]");
        }
        if (riskPerTradePct == null || riskPerTradePct.signum() <= 0
            || riskPerTradePct.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("riskPerTradePct must be in (0,1]");
        }
        if (costModel == null) {
            costModel = new ZerodhaDeliveryCostModel();
        }
        if (atrPeriodForRanking <= 0) {
            atrPeriodForRanking = 14;
        }
        if (brokeragePerTrade == null) {
            brokeragePerTrade = BigDecimal.ZERO;
        }
    }

    /** Convenience default mirroring typical paper_trading_portfolio settings. */
    public static PortfolioBacktestConfig defaults(BigDecimal initialCapital) {
        return new PortfolioBacktestConfig(initialCapital, 12, BigDecimal.valueOf(0.10),
            BigDecimal.valueOf(0.01), 0.001, new ZerodhaDeliveryCostModel(), 14, BigDecimal.valueOf(20));
    }
}
