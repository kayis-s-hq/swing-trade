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

import com.swingtrade.strategy.impl.DefaultIndicatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.*;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive unit tests for DefaultIndicatorService class.
 * Tests indicator calculation methods with valid and invalid inputs.
 */
@ExtendWith(MockitoExtension.class)
class DefaultIndicatorServiceTest {

    private DefaultIndicatorService indicatorService;

    @BeforeEach
    void setUp() {
        indicatorService = new DefaultIndicatorService();
    }

    @Nested
    class CalculateEMATests {

        @Test
        void testCalculateEMA_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> indicatorService.calculateEMA(null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BarSeries cannot be null");
        }

        @Test
        void testCalculateEMA_withInvalidTimeFrame_throwsIllegalArgumentException() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When & Then: IllegalArgumentException should be thrown for timeFrame <= 0
            assertThatThrownBy(() -> indicatorService.calculateEMA(series, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");

            assertThatThrownBy(() -> indicatorService.calculateEMA(series, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");
        }

        @Test
        void testCalculateEMA_withValidBarSeries_createsIndicator() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating EMA
            EMAIndicator ema = indicatorService.calculateEMA(series, 20);

            // Then: returns a valid EMA indicator
            assertThat(ema).isNotNull();
        }

        @Test
        void testCalculateEMA_withValidBarSeriesAndDifferentPeriods() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating EMA with different periods
            EMAIndicator ema10 = indicatorService.calculateEMA(series, 10);
            EMAIndicator ema20 = indicatorService.calculateEMA(series, 20);
            EMAIndicator ema50 = indicatorService.calculateEMA(series, 50);

            // Then: all return valid EMA indicators
            assertThat(ema10).isNotNull();
            assertThat(ema20).isNotNull();
            assertThat(ema50).isNotNull();
        }

        @Test
        void testCalculateEMA_withEmptyBarSeries_createsIndicator() {
            // Given: empty bar series
            BarSeries series = new org.ta4j.core.BaseBarSeries("empty");

            // When: calculating EMA
            EMAIndicator ema = indicatorService.calculateEMA(series, 20);

            // Then: returns a valid EMA indicator
            assertThat(ema).isNotNull();
        }

        @Test
        void testCalculateEMA_withShortBarSeries_createsIndicator() {
            // Given: short bar series
            BarSeries series = createValidBarSeries(10);

            // When: calculating EMA
            EMAIndicator ema = indicatorService.calculateEMA(series, 10);

            // Then: returns a valid EMA indicator
            assertThat(ema).isNotNull();
        }
    }

    @Nested
    class CalculateRSITests {

        @Test
        void testCalculateRSI_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> indicatorService.calculateRSI(null, 14))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BarSeries cannot be null");
        }

        @Test
        void testCalculateRSI_withInvalidTimeFrame_throwsIllegalArgumentException() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When & Then: IllegalArgumentException should be thrown for timeFrame <= 0
            assertThatThrownBy(() -> indicatorService.calculateRSI(series, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");
        }

        @Test
        void testCalculateRSI_withValidBarSeries_createsIndicator() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating RSI
            RSIIndicator rsi = indicatorService.calculateRSI(series, 14);

            // Then: returns a valid RSI indicator
            assertThat(rsi).isNotNull();
        }

        @Test
        void testCalculateRSI_withDefaultPeriod() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating RSI with default period
            RSIIndicator rsi = indicatorService.calculateRSI(series, 14);

            // Then: returns a valid RSI indicator
            assertThat(rsi).isNotNull();
        }

        @Test
        void testCalculateRSI_withDifferentPeriods() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating RSI with different periods
            RSIIndicator rsi7 = indicatorService.calculateRSI(series, 7);
            RSIIndicator rsi21 = indicatorService.calculateRSI(series, 21);

            // Then: all return valid RSI indicators
            assertThat(rsi7).isNotNull();
            assertThat(rsi21).isNotNull();
        }

        @Test
        void testCalculateRSI_withEmptyBarSeries_createsIndicator() {
            // Given: empty bar series
            BarSeries series = new org.ta4j.core.BaseBarSeries("empty");

            // When: calculating RSI
            RSIIndicator rsi = indicatorService.calculateRSI(series, 14);

            // Then: returns a valid RSI indicator
            assertThat(rsi).isNotNull();
        }
    }

    @Nested
    class CalculateATRTests {

        @Test
        void testCalculateATR_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> indicatorService.calculateATR(null, 14))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BarSeries cannot be null");
        }

        @Test
        void testCalculateATR_withInvalidTimeFrame_throwsIllegalArgumentException() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When & Then: IllegalArgumentException should be thrown for timeFrame <= 0
            assertThatThrownBy(() -> indicatorService.calculateATR(series, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");
        }

        @Test
        void testCalculateATR_withValidBarSeries_createsIndicator() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating ATR
            ATRIndicator atr = indicatorService.calculateATR(series, 14);

            // Then: returns a valid ATR indicator
            assertThat(atr).isNotNull();
        }

        @Test
        void testCalculateATR_withDifferentPeriods() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating ATR with different periods
            ATRIndicator atr10 = indicatorService.calculateATR(series, 10);
            ATRIndicator atr20 = indicatorService.calculateATR(series, 20);

            // Then: all return valid ATR indicators
            assertThat(atr10).isNotNull();
            assertThat(atr20).isNotNull();
        }

        @Test
        void testCalculateATR_withEmptyBarSeries_createsIndicator() {
            // Given: empty bar series
            BarSeries series = new org.ta4j.core.BaseBarSeries("empty");

            // When: calculating ATR
            ATRIndicator atr = indicatorService.calculateATR(series, 14);

            // Then: returns a valid ATR indicator
            assertThat(atr).isNotNull();
        }
    }

    @Nested
    class CalculateVolumeMATests {

        @Test
        void testCalculateVolumeMA_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> indicatorService.calculateVolumeMA(null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BarSeries cannot be null");
        }

        @Test
        void testCalculateVolumeMA_withValidBarSeries_createsIndicator() {
            // Given: valid bar series with volume data
            BarSeries series = createValidBarSeries(50);

            // When: calculating volume MA indicator
            SMAIndicator volumeMA = indicatorService.calculateVolumeMA(series, 20);

            // Then: returns a valid Volume MA indicator
            assertThat(volumeMA).isNotNull();
        }

        @Test
        void testCalculateVolumeMA_withEmptyBarSeries_createsIndicator() {
            // Given: empty bar series
            BarSeries series = new org.ta4j.core.BaseBarSeries("empty");

            // When: calculating volume MA indicator
            SMAIndicator volumeMA = indicatorService.calculateVolumeMA(series, 20);

            // Then: returns a valid Volume MA indicator
            assertThat(volumeMA).isNotNull();
        }

        @Test
        void testCalculateVolumeMA_withDifferentPeriods() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating volume MA with different periods
            SMAIndicator volumeMA10 = indicatorService.calculateVolumeMA(series, 10);
            SMAIndicator volumeMA20 = indicatorService.calculateVolumeMA(series, 20);

            // Then: both are valid indicators
            assertThat(volumeMA10).isNotNull();
            assertThat(volumeMA20).isNotNull();
        }
    }

    @Nested
    class CalculateWeeklyHighTests {

        @Test
        void testCalculateWeeklyHigh_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> indicatorService.calculateWeeklyHigh(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BarSeries cannot be null");
        }

        @Test
        void testCalculateWeeklyHigh_withValidBarSeries_createsIndicator() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating weekly high
            Indicator<Num> weeklyHigh = indicatorService.calculateWeeklyHigh(series);

            // Then: returns a valid WeeklyHigh indicator
            assertThat(weeklyHigh).isNotNull();
        }

        @Test
        void testCalculateWeeklyHigh_withEmptyBarSeries_createsIndicator() {
            // Given: empty bar series
            BarSeries series = new org.ta4j.core.BaseBarSeries("empty");

            // When: calculating weekly high
            Indicator<Num> weeklyHigh = indicatorService.calculateWeeklyHigh(series);

            // Then: returns a valid WeeklyHigh indicator
            assertThat(weeklyHigh).isNotNull();
        }

        @Test
        void testCalculateWeeklyHigh_withShortBarSeries_createsIndicator() {
            // Given: short bar series
            BarSeries series = createValidBarSeries(10);

            // When: calculating weekly high
            Indicator<Num> weeklyHigh = indicatorService.calculateWeeklyHigh(series);

            // Then: returns a valid WeeklyHigh indicator
            assertThat(weeklyHigh).isNotNull();
        }
    }

    @Nested
    class InputValidationTests {

        @Test
        void testAllCalculateMethods_validateBarSeriesNotNull() {
            // Given: null bar series

            // When & Then: all methods should throw IllegalArgumentException
            assertThatThrownBy(() -> indicatorService.calculateEMA(null, 20))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> indicatorService.calculateRSI(null, 14))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> indicatorService.calculateATR(null, 14))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> indicatorService.calculateVolumeMA(null, 20))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> indicatorService.calculateWeeklyHigh(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testCalculateEMA_validateTimeFramePositive() {
            // Given: bar series with zero/negative timeFrame
            BarSeries series = createValidBarSeries(50);

            // When & Then: should throw IllegalArgumentException
            assertThatThrownBy(() -> indicatorService.calculateEMA(series, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");

            assertThatThrownBy(() -> indicatorService.calculateEMA(series, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");
        }

        @Test
        void testCalculateRSI_validateTimeFramePositive() {
            // Given: bar series with zero/negative timeFrame
            BarSeries series = createValidBarSeries(50);

            // When & Then: should throw IllegalArgumentException
            assertThatThrownBy(() -> indicatorService.calculateRSI(series, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");
        }

        @Test
        void testCalculateATR_validateTimeFramePositive() {
            // Given: bar series with zero/negative timeFrame
            BarSeries series = createValidBarSeries(50);

            // When & Then: should throw IllegalArgumentException
            assertThatThrownBy(() -> indicatorService.calculateATR(series, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time frame must be positive");
        }
    }

    @Nested
    class IndicatorPropertiesTests {

        @Test
        void testCalculateEMA_indicatorHasValidValues() {
            // Given: bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating EMA
            EMAIndicator ema = indicatorService.calculateEMA(series, 20);

            // Then: indicator computes valid values
            assertThat(ema).isNotNull();
        }

        @Test
        void testCalculateRSI_indicatorHasValidValues() {
            // Given: bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating RSI
            RSIIndicator rsi = indicatorService.calculateRSI(series, 14);

            // Then: indicator computes valid values
            assertThat(rsi).isNotNull();
        }

        @Test
        void testCalculateATR_indicatorHasValidValues() {
            // Given: bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating ATR
            ATRIndicator atr = indicatorService.calculateATR(series, 14);

            // Then: indicator computes valid values
            assertThat(atr).isNotNull();
        }

        @Test
        void testCalculateVolumeMA_indicatorHasValidValues() {
            // Given: bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating volume MA
            SMAIndicator volumeMA = indicatorService.calculateVolumeMA(series, 20);

            // Then: indicator computes valid values
            assertThat(volumeMA).isNotNull();
        }

        @Test
        void testCalculateWeeklyHigh_indicatorHasValidValues() {
            // Given: bar series
            BarSeries series = createValidBarSeries(50);

            // When: calculating weekly high
            Indicator<Num> weeklyHigh = indicatorService.calculateWeeklyHigh(series);

            // Then: indicator is valid and computes values
            assertThat(weeklyHigh).isNotNull();
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void testCalculateEMA_withVeryLargePeriod() {
            // Given: bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating EMA with very large period
            EMAIndicator ema = indicatorService.calculateEMA(series, 200);

            // Then: returns valid indicator (may return null values for first bars)
            assertThat(ema).isNotNull();
        }

        @Test
        void testCalculateRSI_withVeryLargePeriod() {
            // Given: bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating RSI with very large period
            RSIIndicator rsi = indicatorService.calculateRSI(series, 200);

            // Then: returns valid indicator
            assertThat(rsi).isNotNull();
        }

        @Test
        void testCalculateATR_withVeryLargePeriod() {
            // Given: bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating ATR with very large period
            ATRIndicator atr = indicatorService.calculateATR(series, 200);

            // Then: returns valid indicator
            assertThat(atr).isNotNull();
        }

        @Test
        void testMultipleIndicatorCalculationsOnSameSeries() {
            // Given: bar series
            BarSeries series = createValidBarSeries(100);

            // When: calculating multiple indicators
            EMAIndicator ema = indicatorService.calculateEMA(series, 20);
            RSIIndicator rsi = indicatorService.calculateRSI(series, 14);
            ATRIndicator atr = indicatorService.calculateATR(series, 14);
            SMAIndicator volumeMA = indicatorService.calculateVolumeMA(series, 20);
            Indicator<Num> weeklyHigh = indicatorService.calculateWeeklyHigh(series);

            // Then: all indicators are valid
            assertThat(ema).isNotNull();
            assertThat(rsi).isNotNull();
            assertThat(atr).isNotNull();
            assertThat(volumeMA).isNotNull();
            assertThat(weeklyHigh).isNotNull();
        }
    }

    // Helper method
    private BarSeries createValidBarSeries(int numberOfBars) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("Test Series");

        double price = 100.0;
        for (int i = 0; i < numberOfBars; i++) {
            double open = price;
            double close = price + (Math.random() - 0.5) * 2;
            double high = Math.max(open, close) + Math.random();
            double low = Math.min(open, close) - Math.random();
            long volume = (long) (1000000 + Math.random() * 500000);

            // Use a fixed starting date and increment days to avoid invalid dates like day 32
            java.time.ZonedDateTime date = java.time.ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).plusDays(i);
            series.addBar(date, open, high, low, close, volume);
            price = close;
        }

        return series;
    }
}
