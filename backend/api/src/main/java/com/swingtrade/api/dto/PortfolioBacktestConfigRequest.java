package com.swingtrade.api.dto;

import com.swingtrade.strategy.BacktestConfig;

import java.math.BigDecimal;

/** Optional portfolio backtest overrides. Unset values use production defaults. */
public record PortfolioBacktestConfigRequest(
        BigDecimal slippagePct,
        BigDecimal brokeragePerTrade,
        BigDecimal riskPerTradePct,
        BigDecimal initialCapital,
        Integer maxConcurrentPositions,
        Double atrMultiplierStop,
        Double rewardRiskRatio,
        Integer maxHoldingDays,
        Boolean signalExitEnabled,
        Integer trendBreakStreakDays
) {
    public BacktestConfig toBacktestConfig() {
        BacktestConfig defaults = BacktestConfig.defaults();
        return new BacktestConfig(
                valueOrDefault(slippagePct, defaults.slippagePct()),
                valueOrDefault(brokeragePerTrade, defaults.brokeragePerTrade()),
                valueOrDefault(riskPerTradePct, defaults.riskPerTradePct()),
                valueOrDefault(initialCapital, defaults.initialCapital()),
                valueOrDefault(maxConcurrentPositions, defaults.maxConcurrentPositions()),
                valueOrDefault(atrMultiplierStop, defaults.atrMultiplierStop()),
                valueOrDefault(rewardRiskRatio, defaults.rewardRiskRatio()),
                valueOrDefault(maxHoldingDays, defaults.maxHoldingDays()),
                valueOrDefault(signalExitEnabled, defaults.signalExitEnabled()),
                valueOrDefault(trendBreakStreakDays, defaults.trendBreakStreakDays()),
                defaults.riskManagementPolicy(), defaults.portfolioExposurePolicy());
    }

    private static <T> T valueOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
