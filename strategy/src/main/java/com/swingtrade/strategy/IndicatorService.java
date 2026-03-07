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
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicatorsATR;
import org.ta4j.core.indicators.VolumeIndicator;
import org.ta4j.core.indicators.WeeklyHighIndicator;

/**
 * Service interface for calculating technical indicators used in trading strategies.
 * Provides methods for computing various technical indicators essential for swing trading.
 * 
 * @author Swing Trade Team
 * @since 1.0.0
 */
public interface IndicatorService {
    
    /**
     * Calculates the Exponential Moving Average (EMA) for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @param timeFrame The time frame for EMA calculation (e.g., 20 for 20-period EMA)
     * @return An EMA indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null or timeFrame is invalid
     */
    EMAIndicator calculateEMA(BarSeries barSeries, int timeFrame);
    
    /**
     * Calculates the Relative Strength Index (RSI) for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @param timeFrame The time frame for RSI calculation (e.g., 14 for 14-period RSI)
     * @return An RSI indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null or timeFrame is invalid
     */
    RSIIndicator calculateRSI(BarSeries barSeries, int timeFrame);
    
    /**
     * Calculates the Average True Range (ATR) for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @param timeFrame The time frame for ATR calculation (e.g., 14 for 14-period ATR)
     * @return An ATR indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null or timeFrame is invalid
     */
    ATRIndicator calculateATR(BarSeries barSeries, int timeFrame);
    
    /**
     * Calculates the Volume indicator for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @return A Volume indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null
     */
    VolumeIndicator calculateVolume(BarSeries barSeries);
    
    /**
     * Calculates the 52-week high for the given bar series.
     * 
     * @param barSeries The historical price data series
     * @return A Weekly High indicator for the specified parameters
     * @throws IllegalArgumentException if barSeries is null
     */
    WeeklyHighIndicator calculateWeeklyHigh(BarSeries barSeries);
}
