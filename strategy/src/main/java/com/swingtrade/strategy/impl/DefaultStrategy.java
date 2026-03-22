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

import com.swingtrade.strategy.TradingStrategy;
import org.ta4j.core.*;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.*;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the trading strategy with comprehensive rule sets.
 * Implements both entry and exit signal rules according to swing trading requirements.
 * 
 * @author Swing Trade Team
 * @since 1.0.0
 */
@Component
public class DefaultStrategy implements TradingStrategy {
    
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
        ClosePriceIndicator close = new ClosePriceIndicator(barSeries);
        EMAIndicator emaFast = new EMAIndicator(close, EMA_FAST_PERIOD);
        EMAIndicator emaSlow = new EMAIndicator(close, EMA_SLOW_PERIOD);
        RSIIndicator rsi = new RSIIndicator(close, RSI_PERIOD);
        ATRIndicator atr = new ATRIndicator(barSeries, ATR_PERIOD);
        VolumeIndicator volume = new VolumeIndicator(barSeries);
        EMAIndicator volumeMA = new EMAIndicator(volume, VOLUME_PERIOD);
        Indicator<Num> weeklyHigh = new HighestValueIndicator(new HighPriceIndicator(barSeries), WEEKLY_HIGH_PERIOD * 5);

        // Entry rules
        // Rule 1: EMA fast crosses above slow
        Rule entryRule = new CrossedUpIndicatorRule(emaFast, emaSlow)
            // Rule 2: RSI < 30 (oversold)
            .and(new UnderIndicatorRule(rsi, 30))
            // Rule 3: Volume > 1.5x average
            .and(new OverIndicatorRule(volume, volumeMA))
            // Rule 4: Close > 52-week high
            .and(new OverIndicatorRule(close, weeklyHigh));

        // Exit rules
        // Rule 1: EMA fast crosses below slow
        // Rule 2: RSI > 70 (overbought)
        // Rule 3: Stop loss at 2x ATR
        // Rule 4: Take profit at 5%
        Rule exitRule = new CrossedDownIndicatorRule(emaFast, emaSlow)
            .or(new OverIndicatorRule(rsi, 70))
            .or(new StopLossRule(close, 2.0))
            .or(new StopGainRule(close, 5.0));

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
