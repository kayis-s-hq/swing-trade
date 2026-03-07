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

import com.swingtrade.strategy.Strategy;
import com.swingtrade.strategy.StrategyContext;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicatorsATR;
import org.ta4j.core.indicators.VolumeIndicator;
import org.ta4j.core.indicators.WeeklyHighIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DecimalNum;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of StrategyContext.
 * Manages trading strategy execution with comprehensive indicator calculations.
 * 
 * @author Swing Trade Team
 * @since 1.0.0
 */
public class DefaultStrategyContext implements StrategyContext {
    
    private final Map<String, Strategy> strategyCache = new HashMap<>();
    
    /**
     * Gets the current strategy for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @return A configured Strategy object for the given data
     * @throws IllegalArgumentException if barSeries is null
     */
    @Override
    public Strategy getStrategy(BarSeries barSeries) {
        if (barSeries == null) {
            throw new IllegalArgumentException("BarSeries cannot be null");
        }
        
        // Using cached strategy if exists, otherwise create new
        String cacheKey = barSeries.getName() != null ? barSeries.getName() : "default";
        return strategyCache.computeIfAbsent(cacheKey, k -> new DefaultStrategy());
    }
    
    /**
     * Checks if a trade should be executed based on current strategy rules.
     * 
     * @param barSeries The historical price data series
     * @param index The current index in the bar series
     * @param tradingRecord The record of past trades
     * @return true if trade should be executed, false otherwise
     * @throws IllegalArgumentException if any parameter is null
     */
    @Override
    public boolean shouldExecuteTrade(BarSeries barSeries, int index, TradingRecord tradingRecord) {
        if (barSeries == null || tradingRecord == null) {
            throw new IllegalArgumentException("BarSeries and TradingRecord cannot be null");
        }
        
        // Check if the strategy says to execute
        Strategy strategy = getStrategy(barSeries);
        return strategy.shouldExecuteTrade(barSeries, index, tradingRecord);
    }
}
