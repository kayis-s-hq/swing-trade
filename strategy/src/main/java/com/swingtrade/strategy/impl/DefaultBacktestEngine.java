/*
 * Copyright 2026 Swing Trade
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swingtrade.strategy.impl;

import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import org.springframework.stereotype.Service;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.*;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.TradeOnNextOpenModel;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Default implementation of BacktestEngine for simulating trading strategies.
 * Provides realistic execution simulation with proper transaction cost handling.
 *
 * @author Swing Trade Team
 * @since 1.0.0
 */
@Service
public class DefaultBacktestEngine implements BacktestEngine {

    private static final double DEFAULT_COMMISSION_RATE = 0.001; // 0.1%
    private static final double RISK_FREE_RATE = 0.06; // 6% annual risk-free rate
    private static final double CAPITAL_PER_POSITION_PCT = 0.20; // 20% capital per position
    private static final int MAX_CONCURRENT_POSITIONS = 5; // Maximum 5 concurrent positions

    @Override
    public BacktestResult runBacktest(Strategy strategy, BarSeries barSeries) {
        if (strategy == null || barSeries == null) {
            throw new IllegalArgumentException("Strategy and BarSeries cannot be null");
        }
        return runBacktestWithExecution(strategy, barSeries, DEFAULT_COMMISSION_RATE);
    }

    @Override
    public BacktestResult runBacktestWithExecution(Strategy strategy, BarSeries barSeries, double commissionRate) {
        if (strategy == null || barSeries == null) {
            throw new IllegalArgumentException("Strategy and BarSeries cannot be null");
        }

        // TA4J 0.16: Use BacktestExecutor with TradeOnNextOpenModel
        LinearTransactionCostModel costModel = new LinearTransactionCostModel(commissionRate);
        BacktestExecutor executor = new BacktestExecutor(barSeries, costModel, costModel, new TradeOnNextOpenModel());
        List<org.ta4j.core.reports.TradingStatement> statements = executor.execute(List.of(strategy), null);

        if (statements == null || statements.isEmpty()) {
            return new BacktestResult(0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        // TA4J 0.16: Extract metrics from reports
        org.ta4j.core.reports.PerformanceReport performance = statements.get(0).getPerformanceReport();
        org.ta4j.core.reports.PositionStatsReport positionStats = statements.get(0).getPositionStatsReport();

        // Create a TradingRecord from the strategy's trades for detailed analysis
        TradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        // Calculate basic metrics
        double totalPnL = performance.getTotalProfitLoss().doubleValue();
        int tradeCount = (int) (positionStats.getProfitCount().doubleValue()
                + positionStats.getLossCount().doubleValue()
                + positionStats.getBreakEvenCount().doubleValue());
        double winRate = calculateWinRate(positionStats);
        double sharpeRatio = calculateSharpeRatio(tradingRecord, barSeries);
        double maxDrawdown = calculateMaxDrawdown(tradingRecord);
        double avgTradeDuration = calculateAvgTradeDuration(tradingRecord);
        double profitFactor = calculateProfitFactor(performance);

        return new BacktestResult(totalPnL, tradeCount, winRate, sharpeRatio, maxDrawdown, avgTradeDuration, profitFactor);
    }

    private double calculateWinRate(org.ta4j.core.reports.PositionStatsReport positionStats) {
        double profitCount = positionStats.getProfitCount().doubleValue();
        double lossCount = positionStats.getLossCount().doubleValue();

        double totalTrades = profitCount + lossCount;
        if (totalTrades == 0) {
            return 0.0;
        }

        return (profitCount / totalTrades) * 100;
    }

    private double calculateSharpeRatio(TradingRecord tradingRecord, BarSeries barSeries) {
        // Extract positions to calculate returns from individual trades
        List<org.ta4j.core.Position> positions = tradingRecord.getPositions();
        if (positions == null || positions.isEmpty()) {
            return 0.0;
        }

        // Calculate individual trade returns from completed positions
        double[] returns = positions.stream()
            .filter(org.ta4j.core.Position::isClosed)
            .mapToDouble(position -> {
                double entryPrice = position.getEntry().getPricePerAsset().doubleValue();
                double exitPrice = position.getExit().getPricePerAsset().doubleValue();
                return (exitPrice - entryPrice) / entryPrice;
            })
            .toArray();

        if (returns.length < 2) {
            return 0.0; // Can't calculate standard deviation with fewer than 2 trades
        }

        // Calculate mean return
        double mean = Arrays.stream(returns).average().orElse(0.0);

        // Calculate standard deviation of returns
        double variance = Arrays.stream(returns)
            .map(r -> Math.pow(r - mean, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) {
            return 0.0; // No volatility
        }

        // Sharpe ratio = (avg_return - risk_free_rate) / std_dev * sqrt(252)
        double sharpeRatio = (mean - RISK_FREE_RATE) / stdDev * Math.sqrt(252);
        return sharpeRatio;
    }

    private double calculateMaxDrawdown(TradingRecord tradingRecord) {
        List<org.ta4j.core.Position> positions = tradingRecord.getPositions();
        if (positions == null || positions.isEmpty()) {
            return 0.0;
        }

        // Initial equity
        double initialEquity = 100000;
        double currentEquity = initialEquity;
        double peakEquity = initialEquity;
        double maxDrawdown = 0.0;

        // Process positions in chronological order
        for (org.ta4j.core.Position position : positions) {
            if (position.isClosed()) {
                double pnl = position.getProfit().doubleValue();
                currentEquity += pnl;

                // Update peak equity
                if (currentEquity > peakEquity) {
                    peakEquity = currentEquity;
                }

                // Calculate drawdown from peak
                if (peakEquity > 0) {
                    double drawdown = (peakEquity - currentEquity) / peakEquity * 100;
                    if (drawdown > maxDrawdown) {
                        maxDrawdown = drawdown;
                    }
                }
            }
        }

        return maxDrawdown;
    }

    private double calculateAvgTradeDuration(TradingRecord tradingRecord) {
        List<org.ta4j.core.Position> positions = tradingRecord.getPositions();
        if (positions == null || positions.isEmpty()) {
            return 0.0;
        }

        // Count closed positions
        long closedCount = positions.stream()
            .filter(org.ta4j.core.Position::isClosed)
            .count();

        if (closedCount == 0) {
            return 0.0;
        }

        // Calculate average duration in bars
        OptionalDouble avgDuration = positions.stream()
            .filter(org.ta4j.core.Position::isClosed)
            .mapToInt(position -> {
                int entryIndex = position.getEntry().getIndex();
                int exitIndex = position.getExit().getIndex();
                return exitIndex - entryIndex;
            })
            .average();

        return avgDuration.orElse(0.0);
    }

    private double calculateProfitFactor(org.ta4j.core.reports.PerformanceReport performance) {
        double totalProfit = performance.getTotalProfit().doubleValue();
        double totalLoss = Math.abs(performance.getTotalLoss().doubleValue());

        if (totalLoss == 0) {
            return totalProfit > 0 ? Double.MAX_VALUE : 0.0;
        }

        return totalProfit / totalLoss;
    }
}
