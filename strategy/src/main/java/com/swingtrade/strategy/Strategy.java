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
import org.ta4j.core.TradingRecord;

/**
 * Interface defining the core trading strategy contract.
 * This interface represents a trading strategy that can be applied to financial data.
 * 
 * @author Swing Trade Team
 * @since 1.0.0
 */
public interface Strategy {
    
    /**
     * Generates a trading strategy based on the provided bar series.
     * 
     * @param barSeries The historical price data series to analyze
     * @return A Ta4j Strategy object representing the trading logic
     * @throws IllegalArgumentException if barSeries is null
     */
    org.ta4j.core.Strategy generateStrategy(BarSeries barSeries);
    
    /**
     * Evaluates whether a trade should be executed based on current market conditions.
     * 
     * @param barSeries The historical price data series
     * @param index The current index in the bar series
     * @param tradingRecord The record of past trades
     * @return true if trade should be executed, false otherwise
     * @throws IllegalArgumentException if any parameter is null
     */
    boolean shouldExecuteTrade(BarSeries barSeries, int index, TradingRecord tradingRecord);
}
