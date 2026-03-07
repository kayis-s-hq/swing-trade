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
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicatorsATR;
import org.ta4j.core.indicators.VolumeIndicator;
import org.ta4j.core.indicators.WeeklyHighIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DecimalNum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the trading strategy with comprehensive rule sets.
 * Implements both entry and exit signal rules according to swing trading requirements.
 * 
 * @author Swing Trade Team
 * @since 1.0.0
 */
@Component
public class DefaultStrategy implements Strategy {
    
    private static final int EMA_FAST_PERIOD = 12;
    private static final int EMA_SLOW_PERIOD = 26;
    private static final int RSI_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int VOLUME_PERIOD = 20;
    private static final int WEEKLY_HIGH_PERIOD = 52;
    
    /**
     * Generates a trading strategy based on the provided bar series.
     * 
     * @param barSeries The historical price data series to analyze
     * @return A Ta4j Strategy object representing the trading logic
     * @throws IllegalArgumentException if barSeries is null
     */
    @Override
    public org.ta4j.core.Strategy generateStrategy(BarSeries barSeries) {
        if (barSeries == null) {
            throw new IllegalArgumentException("BarSeries cannot be null");
        }
        
        // Initialize indicators
        EMAIndicator emaFast = new EMAIndicator(barSeries, EMA_FAST_PERIOD);
        EMAIndicator emaSlow = new EMAIndicator(barSeries, EMA_SLOW_PERIOD);
        RSIIndicator rsi = new RSIIndicator(barSeries, RSI_PERIOD);
        ATRIndicator atr = new ATRIndicator(barSeries, ATR_PERIOD);
        VolumeIndicator volume = new VolumeIndicator(barSeries);
        WeeklyHighIndicator weeklyHigh = new WeeklyHighIndicator(barSeries);
        
        // Create entry rules (ALL must be true)
        // Rule 1: EMA(12) crosses above EMA(26)
        BooleanIndicator emaCrossUp = new OverIndicator(emaFast, emaSlow);
        
        // Rule 2: RSI below 30 (oversold condition)
        Num rsiThreshold = DecimalNum.valueOf(30);
        BooleanIndicator rsiOversold = new LessThanIndicator(rsi, rsiThreshold);
        
        // Rule 3: Volume above average
        Num volumeThreshold = volume.getValue(0).multipliedBy(DecimalNum.valueOf(1.5));
        BooleanIndicator volumeAboveAverage = new GreaterThanIndicator(volume, volumeThreshold);
        
        // Rule 4: Price above 52-week high
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        BooleanIndicator priceAboveWeeklyHigh = new GreaterThanIndicator(closePrice, weeklyHigh);
        
        // Combine all entry rules with AND
        Rule entryRule = emaCrossUp.and(rsiOversold).and(volumeAboveAverage).and(priceAboveWeeklyHigh);
        
        // Exit rules (ANY triggers exit)
        // Rule 1: EMA(12) crosses below EMA(26)
        BooleanIndicator emaCrossDown = new UnderIndicator(emaFast, emaSlow);
        
        // Rule 2: RSI above 70 (overbought condition)
        Num rsiOverboughtThreshold = DecimalNum.valueOf(70);
        BooleanIndicator rsiOverbought = new GreaterThanIndicator(rsi, rsiOverboughtThreshold);
        
        // Rule 3: Stop loss based on ATR (2x ATR)
        Num stopLossFactor = DecimalNum.valueOf(2.0);
        Num atrValue = atr.getValue(0);
        Num stopLossLevel = new ClosePriceIndicator(barSeries).getValue(0).minus(atrValue.multipliedBy(stopLossFactor));
        BooleanIndicator stopLossTrigger = new LessThanIndicator(new ClosePriceIndicator(barSeries), stopLossLevel);
        
        // Rule 4: Take profit (5% gain)
        Num takeProfitFactor = DecimalNum.valueOf(1.05);
        Num takeProfitLevel = new ClosePriceIndicator(barSeries).getValue(0).multipliedBy(takeProfitFactor);
        BooleanIndicator takeProfitTrigger = new GreaterThanIndicator(new ClosePriceIndicator(barSeries), takeProfitLevel);
        
        // Combine all exit rules with OR
        Rule exitRule = emaCrossDown.or(rsiOverbought).or(stopLossTrigger).or(takeProfitTrigger);
        
        // Create strategy with combined rules
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * Evaluates whether a trade should be executed based on current market conditions.
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
        
        // Generate the strategy and evaluate if it should trigger
        org.ta4j.core.Strategy ta4jStrategy = generateStrategy(barSeries);
        return ta4jStrategy.shouldEnter(index);
    }
}
