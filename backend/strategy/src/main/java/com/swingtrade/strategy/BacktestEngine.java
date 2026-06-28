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

package com.swingtrade.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

/**
 * Interface for managing backtesting of trading strategies.
 * Provides methods for simulating trading strategies against historical data.
 * 
 * @author Swing Trade Team
 * @since 1.0.0
 */
public interface BacktestEngine {
    
    /**
     * Executes a backtest for the given strategy and historical data.
     * Simulates realistic trading execution with proper order handling.
     * 
     * @param strategy The trading strategy to backtest
     * @param barSeries The historical price data series
     * @return The backtest results containing performance metrics
     * @throws IllegalArgumentException if any parameter is null
     */
    BacktestResult runBacktest(Strategy strategy, BarSeries barSeries);
    
    /**
     * Executes a backtest for the given strategy and historical data with execution simulation.
     * Simulates realistic trading execution with proper order handling and transaction costs.
     * 
     * @param strategy The trading strategy to backtest
     * @param barSeries The historical price data series
     * @param commissionRate The commission rate per trade (e.g., 0.001 for 0.1%)
     * @return The backtest results containing performance metrics
     * @throws IllegalArgumentException if any parameter is null
     */
    BacktestResult runBacktestWithExecution(Strategy strategy, BarSeries barSeries, double commissionRate);
}
