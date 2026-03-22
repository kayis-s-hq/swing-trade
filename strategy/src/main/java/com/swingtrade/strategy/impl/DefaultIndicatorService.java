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

import com.swingtrade.strategy.IndicatorService;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import org.springframework.stereotype.Service;

/**
 * Default implementation of IndicatorService for calculating technical indicators.
 * Provides comprehensive support for EMA, RSI, ATR, Volume, and 52-week high calculations.
 * 
 * @author Swing Trade Team
 * @since 1.0.0
 */
@Service
public class DefaultIndicatorService implements IndicatorService {
    
    private static final int DEFAULT_EMA_PERIOD = 20;
    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final int DEFAULT_ATR_PERIOD = 14;
    private static final int DEFAULT_VOLUME_PERIOD = 20;
    private static final int DEFAULT_WEEKLY_HIGH_PERIOD = 52;
    
    /**
     * Calculates the Exponential Moving Average (EMA) for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @param timeFrame The time frame for EMA calculation (e.g., 20 for 20-period EMA)
     * @return An EMA indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null or timeFrame is invalid
     */
    @Override
    public EMAIndicator calculateEMA(BarSeries barSeries, int timeFrame) {
        if (barSeries == null) {
            throw new IllegalArgumentException("BarSeries cannot be null");
        }
        if (timeFrame <= 0) {
            throw new IllegalArgumentException("Time frame must be positive");
        }

        return new EMAIndicator(new ClosePriceIndicator(barSeries), timeFrame);
    }
    
    /**
     * Calculates the Relative Strength Index (RSI) for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @param timeFrame The time frame for RSI calculation (e.g., 14 for 14-period RSI)
     * @return An RSI indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null or timeFrame is invalid
     */
    @Override
    public RSIIndicator calculateRSI(BarSeries barSeries, int timeFrame) {
        if (barSeries == null) {
            throw new IllegalArgumentException("BarSeries cannot be null");
        }
        if (timeFrame <= 0) {
            throw new IllegalArgumentException("Time frame must be positive");
        }

        return new RSIIndicator(new ClosePriceIndicator(barSeries), timeFrame);
    }
    
    /**
     * Calculates the Average True Range (ATR) for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @param timeFrame The time frame for ATR calculation (e.g., 14 for 14-period ATR)
     * @return An ATR indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null or timeFrame is invalid
     */
    @Override
    public ATRIndicator calculateATR(BarSeries barSeries, int timeFrame) {
        if (barSeries == null) {
            throw new IllegalArgumentException("BarSeries cannot be null");
        }
        if (timeFrame <= 0) {
            throw new IllegalArgumentException("Time frame must be positive");
        }
        
        return new ATRIndicator(barSeries, timeFrame);
    }
    
    /**
     * Calculates the Volume Moving Average indicator for the given bar series.
     *
     * @param barSeries The historical price data series
     * @param period The period for volume MA calculation
     * @return A Volume Moving Average indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null
     */
    @Override
    public SMAIndicator calculateVolumeMA(BarSeries barSeries, int period) {
        if (barSeries == null) {
            throw new IllegalArgumentException("BarSeries cannot be null");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }

        return new SMAIndicator(new VolumeIndicator(barSeries), period);
    }
    
    /**
     * Calculates the 52-week high for the given bar series.
     *
     * @param barSeries The historical price data series
     * @return A Weekly High indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null
     */
    @Override
    public Indicator<Num> calculateWeeklyHigh(BarSeries barSeries) {
        if (barSeries == null) {
            throw new IllegalArgumentException("BarSeries cannot be null");
        }

        return new HighestValueIndicator(new HighPriceIndicator(barSeries), DEFAULT_WEEKLY_HIGH_PERIOD);
    }
}
