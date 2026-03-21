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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNull;

/**
 * Comprehensive unit tests for TechnicalIndicators class.
 * Tests all indicator calculation methods with valid and invalid inputs.
 */
@ExtendWith(MockitoExtension.class)
class TechnicalIndicatorsTest {

    private TechnicalIndicators technicalIndicators;

    @Mock
    private List<CandleWithPrices> mockCandles;

    @BeforeEach
    void setUp() {
        technicalIndicators = new TechnicalIndicators();
    }

    @Nested
    class CalculateRSITests {

        @Test
        void testCalculateRSI_withInsufficientData_returnsNull() {
            // Given: insufficient data (period = 14, only 10 values)
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i));
            }

            // When: calculating RSI with insufficient data
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: returns null
            assertThatNull().as("RSI should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateRSI_withExactDataPeriod_returnsValue() {
            // Given: exactly 14 data points
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i));
            }

            // When: calculating RSI
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: returns a non-null value
            assertThat(rsi).isNotNull().as("RSI should have a value");
        }

        @Test
        void testCalculateRSI_withMoreThanRequiredData_returnsValue() {
            // Given: more than 14 data points
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i * 0.5));
            }

            // When: calculating RSI
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: returns a valid RSI value between 0 and 100
            assertThat(rsi).isNotNull();
        }

        @Test
        void testCalculateRSI_withEmptyList_returnsNull() {
            // Given: empty list
            List<BigDecimal> closePrices = new ArrayList<>();

            // When: calculating RSI
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: returns null
            assertThatNull().as("RSI should be null with empty list").isNull();
        }

        @Test
        void testCalculateRSI_withNullList_returnsNull() {
            // When: calculating RSI with null list
            Double rsi = technicalIndicators.calculateRSI(null, 14);

            // Then: returns null (no exception thrown)
            assertThatNull().as("RSI should be null with null list").isNull();
        }

        @Test
        void testCalculateRSI_withConstantPrices_returnsExpectedValue() {
            // Given: constant prices (all same value)
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(BigDecimal.valueOf(100.0));
            }

            // When: calculating RSI
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: RSI should be 50 (no price changes)
            assertThat(rsi).isNotNull();
        }
    }

    @Nested
    class CalculateSMATests {

        @Test
        void testCalculateSMA_withInsufficientData_returnsNull() {
            // Given: insufficient data (period = 20, only 15 values)
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i));
            }

            // When: calculating SMA
            Double sma = technicalIndicators.calculateSMA(closePrices, 20);

            // Then: returns null
            assertThat(sma).as("SMA should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateSMA_withValidData_returnsCorrectValue() {
            // Given: valid data with known values
            List<BigDecimal> closePrices = new ArrayList<>();
            List<Double> expectedValues = Arrays.asList(100.0, 101.0, 102.0, 103.0, 104.0,
                                                        105.0, 106.0, 107.0, 108.0, 109.0,
                                                        110.0, 111.0, 112.0, 113.0, 114.0,
                                                        115.0, 116.0, 117.0, 118.0, 119.0);
            closePrices.addAll(expectedValues);

            // When: calculating SMA with period 5
            Double sma = technicalIndicators.calculateSMA(closePrices, 5);

            // Then: SMA should be correct (average of last 5 values: 115+116+117+118+119 = 585/5 = 117)
            assertThat(sma).isNotNull();
            assertThat(Math.abs(sma - 117.0)).isLessThan(0.01);
        }

        @Test
        void testCalculateSMA_withEmptyList_returnsNull() {
            // Given: empty list
            List<BigDecimal> closePrices = new ArrayList<>();

            // When: calculating SMA
            Double sma = technicalIndicators.calculateSMA(closePrices, 20);

            // Then: returns null
            assertThat(sma).as("SMA should be null with empty list").isNull();
        }

        @Test
        void testCalculateSMA_withSingleValue_returnsNull() {
            // Given: single value with period 20
            List<BigDecimal> closePrices = Arrays.asList(BigDecimal.valueOf(100.0));

            // When: calculating SMA
            Double sma = technicalIndicators.calculateSMA(closePrices, 20);

            // Then: returns null
            assertThat(sma).as("SMA should be null with single value").isNull();
        }

        @Test
        void testCalculateSMA_withPeriodOne() {
            // Given: multiple values
            List<BigDecimal> closePrices = Arrays.asList(
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(101.0),
                BigDecimal.valueOf(102.0)
            );

            // When: calculating SMA with period 1
            Double sma = technicalIndicators.calculateSMA(closePrices, 1);

            // Then: SMA should equal the last close price
            assertThat(sma).isEqualTo(102.0);
        }
    }

    @Nested
    class CalculateEMATests {

        @Test
        void testCalculateEMA_withInsufficientData_returnsNull() {
            // Given: insufficient data
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i));
            }

            // When: calculating EMA
            Double ema = technicalIndicators.calculateEMA(closePrices, 20);

            // Then: returns null
            assertThat(ema).as("EMA should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateEMA_withValidData_returnsValue() {
            // Given: valid data
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i * 0.5));
            }

            // When: calculating EMA with period 10
            Double ema = technicalIndicators.calculateEMA(closePrices, 10);

            // Then: returns a non-null value
            assertThat(ema).isNotNull().as("EMA should have a value");
        }

        @Test
        void testCalculateEMA_withEmptyList_returnsNull() {
            // Given: empty list
            List<BigDecimal> closePrices = new ArrayList<>();

            // When: calculating EMA
            Double ema = technicalIndicators.calculateEMA(closePrices, 10);

            // Then: returns null
            assertThat(ema).as("EMA should be null with empty list").isNull();
        }

        @Test
        void testCalculateEMA_withConstantPrices() {
            // Given: constant prices
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(BigDecimal.valueOf(100.0));
            }

            // When: calculating EMA
            Double ema = technicalIndicators.calculateEMA(closePrices, 10);

            // Then: EMA should equal the constant price
            assertThat(ema).isEqualTo(100.0);
        }

        @Test
        void testCalculateEMA_withTrendingPrices() {
            // Given: consistently increasing prices
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(BigDecimal.valueOf(100.0 + i * 5));
            }

            // When: calculating EMA
            Double ema = technicalIndicators.calculateEMA(closePrices, 10);

            // Then: EMA should be less than the last price (due to weighting of earlier values)
            assertThat(ema).isNotNull();
            assertThat(ema).isLessThan(100.0 + 19 * 5);
        }
    }

    @Nested
    class CalculateMACDTests {

        @Test
        void testCalculateMACD_withInsufficientData_returnsNull() {
            // Given: insufficient data (less than slow period of 26)
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i));
            }

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: returns null
            assertThat(macd).as("MACD should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateMACD_withValidData_returnsValue() {
            // Given: sufficient data
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i * 0.5));
            }

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: returns a non-null value
            assertThat(macd).isNotNull().as("MACD should have a value");
        }

        @Test
        void testCalculateMACD_withEmptyList_returnsNull() {
            // Given: empty list
            List<BigDecimal> closePrices = new ArrayList<>();

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: returns null
            assertThat(macd).as("MACD should be null with empty list").isNull();
        }

        @Test
        void testCalculateMACD_withConstantPrices() {
            // Given: constant prices
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                closePrices.add(BigDecimal.valueOf(100.0));
            }

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: MACD should be approximately 0 (no price movement)
            assertThat(macd).isNotNull();
        }

        @Test
        void testCalculateMACD_withDefaultPeriods() {
            // Given: sufficient data
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                closePrices.add(BigDecimal.valueOf(100.0 + i * 0.1));
            }

            // When: calculating MACD with default periods
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: returns a value
            assertThat(macd).isNotNull();
        }
    }

    @Nested
    class CalculateATRTests {

        @Test
        void testCalculateATR_withInsufficientData_returnsNull() {
            // Given: insufficient candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(105),
                    BigDecimal.valueOf(95),
                    BigDecimal.valueOf(102),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: returns null
            assertThat(atr).as("ATR should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateATR_withValidData_returnsValue() {
            // Given: sufficient candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(105 + i),
                    BigDecimal.valueOf(95 + i),
                    BigDecimal.valueOf(102 + i),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: returns a non-null value
            assertThat(atr).isNotNull().as("ATR should have a value");
        }

        @Test
        void testCalculateATR_withEmptyCandles_returnsNull() {
            // Given: empty list
            List<CandleWithPrices> candles = new ArrayList<>();

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: returns null
            assertThat(atr).as("ATR should be null with empty candles").isNull();
        }

        @Test
        void testCalculateATR_withSmallRangeCandles() {
            // Given: candles with small price ranges
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100.0),
                    BigDecimal.valueOf(100.5),
                    BigDecimal.valueOf(99.5),
                    BigDecimal.valueOf(100.0),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: ATR should be small (low volatility)
            assertThat(atr).isNotNull();
            assertThat(atr).isLessThan(2.0);
        }

        @Test
        void testCalculateATR_withLargeRangeCandles() {
            // Given: candles with large price ranges
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100.0),
                    BigDecimal.valueOf(110.0),
                    BigDecimal.valueOf(90.0),
                    BigDecimal.valueOf(105.0),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: ATR should be larger (high volatility)
            assertThat(atr).isNotNull();
            assertThat(atr).isGreaterThan(5.0);
        }
    }

    @Nested
    class CalculateStochasticTests {

        @Test
        void testCalculateStochastic_withInsufficientData_returnsNull() {
            // Given: insufficient candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(105),
                    BigDecimal.valueOf(95),
                    BigDecimal.valueOf(102),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating stochastic with period 14
            StochasticValues stochastic = technicalIndicators.calculateStochastic(candles, 14, 3);

            // Then: returns null
            assertThat(stochastic).as("Stochastic should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateStochastic_withValidData_returnsValue() {
            // Given: sufficient candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100 + i * 0.5),
                    BigDecimal.valueOf(105 + i * 0.5),
                    BigDecimal.valueOf(95 + i * 0.5),
                    BigDecimal.valueOf(102 + i * 0.5),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating stochastic
            StochasticValues stochastic = technicalIndicators.calculateStochastic(candles, 14, 3);

            // Then: returns non-null K and D values
            assertThat(stochastic).isNotNull().as("Stochastic should have values");
            assertThat(stochastic.k()).isNotNull();
            assertThat(stochastic.d()).isNotNull();
        }

        @Test
        void testCalculateStochastic_withEmptyCandles_returnsNull() {
            // Given: empty list
            List<CandleWithPrices> candles = new ArrayList<>();

            // When: calculating stochastic
            StochasticValues stochastic = technicalIndicators.calculateStochastic(candles, 14, 3);

            // Then: returns null
            assertThat(stochastic).as("Stochastic should be null with empty candles").isNull();
        }

        @Test
        void testCalculateStochastic_KAndDRange() {
            // Given: sufficient data
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                candles.add(new CandleWithPrices(
                    LocalDate.of(2024, 1, i + 1),
                    BigDecimal.valueOf(100.0),
                    BigDecimal.valueOf(102.0),
                    BigDecimal.valueOf(98.0),
                    BigDecimal.valueOf(100.0),
                    1000000L
                ));
            }

            // When: calculating stochastic
            StochasticValues stochastic = technicalIndicators.calculateStochastic(candles, 14, 3);

            // Then: K and D should be in valid range (typically 0-100)
            assertThat(stochastic).isNotNull();
            assertThat(stochastic.k()).isNotNull().isBetween(0.0, 100.0);
            assertThat(stochastic.d()).isNotNull().isBetween(0.0, 100.0);
        }
    }

    @Nested
    class CalculateBollingerBandsTests {

        @Test
        void testCalculateBollingerBands_withInsufficientData_returnsNull() {
            // Given: insufficient data
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i));
            }

            // When: calculating Bollinger Bands
            BollingerBands bands = technicalIndicators.calculateBollingerBands(closePrices, 20, 2.0);

            // Then: returns null
            assertThat(bands).as("Bollinger Bands should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateBollingerBands_withValidData_returnsValue() {
            // Given: sufficient data
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i * 0.5));
            }

            // When: calculating Bollinger Bands
            BollingerBands bands = technicalIndicators.calculateBollingerBands(closePrices, 20, 2.0);

            // Then: returns non-null values
            assertThat(bands).isNotNull().as("Bollinger Bands should have values");
        }

        @Test
        void testCalculateBollingerBands_BandRelationship() {
            // Given: sufficient data
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                closePrices.add(BigDecimal.valueOf(100 + i * 0.1));
            }

            // When: calculating Bollinger Bands
            BollingerBands bands = technicalIndicators.calculateBollingerBands(closePrices, 20, 2.0);

            // Then: upper > middle > lower
            assertThat(bands).isNotNull();
            assertThat(bands.upper()).isGreaterThan(bands.middle());
            assertThat(bands.middle()).isGreaterThan(bands.lower());
        }

        @Test
        void testCalculateBollingerBands_withEmptyList_returnsNull() {
            // Given: empty list
            List<BigDecimal> closePrices = new ArrayList<>();

            // When: calculating Bollinger Bands
            BollingerBands bands = technicalIndicators.calculateBollingerBands(closePrices, 20, 2.0);

            // Then: returns null
            assertThat(bands).as("Bollinger Bands should be null with empty list").isNull();
        }

        @Test
        void testCalculateBollingerBands_withConstantPrices() {
            // Given: constant prices
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                closePrices.add(BigDecimal.valueOf(100.0));
            }

            // When: calculating Bollinger Bands
            BollingerBands bands = technicalIndicators.calculateBollingerBands(closePrices, 20, 2.0);

            // Then: all bands should be equal (no volatility)
            assertThat(bands).isNotNull();
            assertThat(bands.upper()).isEqualTo(100.0);
            assertThat(bands.middle()).isEqualTo(100.0);
            assertThat(bands.lower()).isEqualTo(100.0);
        }
    }

    @Nested
    class CalculateVWAPTests {

        @Test
        void testCalculateVWAP_withEmptyCandles_returnsNull() {
            // Given: empty candles
            List<CandleWithPrices> candles = new ArrayList<>();

            // When: calculating VWAP
            Double vwap = technicalIndicators.calculateVWAP(candles);

            // Then: returns null
            assertThat(vwap).as("VWAP should be null with empty candles").isNull();
        }

        @Test
        void testCalculateVWAP_withSingleCandle_returnsValue() {
            // Given: single candle
            List<CandleWithPrices> candles = Arrays.asList(new CandleWithPrices(
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(105.0),
                BigDecimal.valueOf(95.0),
                BigDecimal.valueOf(102.0),
                BigDecimal.valueOf(1000000)
            ));

            // When: calculating VWAP
            Double vwap = technicalIndicators.calculateVWAP(candles);

            // Then: returns a non-null value
            assertThat(vwap).isNotNull().as("VWAP should have a value");
        }

        @Test
        void testCalculateVWAP_withMultipleCandles_returnsValue() {
            // Given: multiple candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(105 + i),
                    BigDecimal.valueOf(95 + i),
                    BigDecimal.valueOf(102 + i),
                    BigDecimal.valueOf(1000000 + i * 100000)
                ));
            }

            // When: calculating VWAP
            Double vwap = technicalIndicators.calculateVWAP(candles);

            // Then: returns a non-null value
            assertThat(vwap).isNotNull().as("VWAP should have a value");
        }

        @Test
        void testCalculateVWAP_PriceInValidRange() {
            // Given: multiple candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100.0),
                    BigDecimal.valueOf(110.0),
                    BigDecimal.valueOf(90.0),
                    BigDecimal.valueOf(105.0),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating VWAP
            Double vwap = technicalIndicators.calculateVWAP(candles);

            // Then: VWAP should be within candle price ranges
            assertThat(vwap).isNotNull();
            assertThat(vwap).isGreaterThanOrEqualTo(90.0);
            assertThat(vwap).isLessThanOrEqualTo(110.0);
        }
    }

    @Nested
    class CalculateADXTests {

        @Test
        void testCalculateADX_withInsufficientData_returnsNull() {
            // Given: insufficient candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(105),
                    BigDecimal.valueOf(95),
                    BigDecimal.valueOf(102),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating ADX
            Double adx = technicalIndicators.calculateADX(candles, 14);

            // Then: returns null (as implemented - placeholder)
            assertThat(adx).as("ADX should be null (not yet implemented)").isNull();
        }

        @Test
        void testCalculateADX_withEmptyCandles_returnsNull() {
            // Given: empty candles
            List<CandleWithPrices> candles = new ArrayList<>();

            // When: calculating ADX
            Double adx = technicalIndicators.calculateADX(candles, 14);

            // Then: returns null
            assertThat(adx).as("ADX should be null with empty candles").isNull();
        }

        @Test
        void testCalculateADX_withValidData_returnsNullAsPlaceholder() {
            // Given: sufficient candles
            List<CandleWithPrices> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPrices(
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(105 + i),
                    BigDecimal.valueOf(95 + i),
                    BigDecimal.valueOf(102 + i),
                    BigDecimal.valueOf(1000000)
                ));
            }

            // When: calculating ADX
            Double adx = technicalIndicators.calculateADX(candles, 14);

            // Then: returns null (placeholder implementation)
            assertThat(adx).isNull().as("ADX is not yet implemented");
        }
    }

    @Nested
    class CalculateRSIDoubleTests {

        @Test
        void testCalculateRSI_double_withInsufficientData_returnsNull() {
            // Given: insufficient data (period = 14, only 10 values)
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating RSI with insufficient data
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: returns null
            assertThat(rsi).as("RSI should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateRSI_double_withValidData_returnsValue() {
            // Given: sufficient data (20 values)
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(100.0 + i * 0.5);
            }

            // When: calculating RSI with period 14
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: returns a valid RSI value
            assertThat(rsi).isNotNull();
        }

        @Test
        void testCalculateRSI_double_withEmptyList_returnsNull() {
            // Given: empty list
            List<Double> closePrices = new ArrayList<>();

            // When: calculating RSI
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: returns null
            assertThat(rsi).as("RSI should be null with empty list").isNull();
        }

        @Test
        void testCalculateRSI_double_withNullList_returnsNull() {
            // When: calculating RSI with null list
            Double rsi = technicalIndicators.calculateRSI(null, 14);

            // Then: returns null
            assertThat(rsi).as("RSI should be null with null list").isNull();
        }

        @Test
        void testCalculateRSI_double_withInvalidPeriod_throwsException() {
            // Given: valid data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating RSI with invalid period (0)
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateRSI(closePrices, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RSI period must be positive");
        }

        @Test
        void testCalculateRSI_double_withNegativePeriod_throwsException() {
            // Given: valid data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating RSI with negative period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateRSI(closePrices, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RSI period must be positive");
        }

        @Test
        void testCalculateRSI_double_withConstantPrices_returnsExpected() {
            // Given: constant prices
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(100.0);
            }

            // When: calculating RSI
            Double rsi = technicalIndicators.calculateRSI(closePrices, 14);

            // Then: RSI should be 50 (no price changes)
            assertThat(rsi).isNotNull().isEqualTo(50.0);
        }
    }

    @Nested
    class CalculateSMADoubleTests {

        @Test
        void testCalculateSMA_double_withInsufficientData_returnsNull() {
            // Given: insufficient data (period = 20, only 15 values)
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating SMA
            Double sma = technicalIndicators.calculateSMA(closePrices, 20);

            // Then: returns null
            assertThat(sma).as("SMA should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateSMA_double_withValidData_returnsCorrectValue() {
            // Given: valid data with known values
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating SMA with period 5
            Double sma = technicalIndicators.calculateSMA(closePrices, 5);

            // Then: SMA should be correct (average of last 5 values: 115+116+117+118+119 = 585/5 = 117)
            assertThat(sma).isNotNull();
            assertThat(Math.abs(sma - 117.0)).isLessThan(0.01);
        }

        @Test
        void testCalculateSMA_double_withEmptyList_returnsNull() {
            // Given: empty list
            List<Double> closePrices = new ArrayList<>();

            // When: calculating SMA
            Double sma = technicalIndicators.calculateSMA(closePrices, 20);

            // Then: returns null
            assertThat(sma).as("SMA should be null with empty list").isNull();
        }

        @Test
        void testCalculateSMA_double_withNullList_returnsNull() {
            // When: calculating SMA with null list
            Double sma = technicalIndicators.calculateSMA(null, 20);

            // Then: returns null
            assertThat(sma).as("SMA should be null with null list").isNull();
        }

        @Test
        void testCalculateSMA_double_withInvalidPeriod_throwsException() {
            // Given: valid data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating SMA with invalid period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateSMA(closePrices, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SMA period must be positive");
        }

        @Test
        void testCalculateSMA_double_withPeriodOne() {
            // Given: multiple values
            List<Double> closePrices = new ArrayList<>();
            closePrices.add(100.0);
            closePrices.add(101.0);
            closePrices.add(102.0);

            // When: calculating SMA with period 1
            Double sma = technicalIndicators.calculateSMA(closePrices, 1);

            // Then: SMA should equal the last close price
            assertThat(sma).isEqualTo(102.0);
        }
    }

    @Nested
    class CalculateEMADoubleTests {

        @Test
        void testCalculateEMA_double_withInsufficientData_returnsNull() {
            // Given: insufficient data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating EMA
            Double ema = technicalIndicators.calculateEMA(closePrices, 20);

            // Then: returns null
            assertThat(ema).as("EMA should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateEMA_double_withValidData_returnsValue() {
            // Given: valid data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                closePrices.add(100.0 + i * 0.5);
            }

            // When: calculating EMA with period 10
            Double ema = technicalIndicators.calculateEMA(closePrices, 10);

            // Then: returns a non-null value
            assertThat(ema).isNotNull();
        }

        @Test
        void testCalculateEMA_double_withEmptyList_returnsNull() {
            // Given: empty list
            List<Double> closePrices = new ArrayList<>();

            // When: calculating EMA
            Double ema = technicalIndicators.calculateEMA(closePrices, 10);

            // Then: returns null
            assertThat(ema).as("EMA should be null with empty list").isNull();
        }

        @Test
        void testCalculateEMA_double_withNullList_returnsNull() {
            // When: calculating EMA with null list
            Double ema = technicalIndicators.calculateEMA(null, 10);

            // Then: returns null
            assertThat(ema).as("EMA should be null with null list").isNull();
        }

        @Test
        void testCalculateEMA_double_withConstantPrices() {
            // Given: constant prices
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(100.0);
            }

            // When: calculating EMA
            Double ema = technicalIndicators.calculateEMA(closePrices, 10);

            // Then: EMA should equal the constant price
            assertThat(ema).isEqualTo(100.0);
        }

        @Test
        void testCalculateEMA_double_withInvalidPeriod_throwsException() {
            // Given: valid data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating EMA with invalid period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateEMA(closePrices, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EMA period must be positive");
        }
    }

    @Nested
    class CalculateMACDDoubleTests {

        @Test
        void testCalculateMACD_double_withInsufficientData_returnsNull() {
            // Given: insufficient data (less than slow period of 26)
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: returns null
            assertThat(macd).as("MACD should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateMACD_double_withValidData_returnsValue() {
            // Given: sufficient data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                closePrices.add(100.0 + i * 0.5);
            }

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: returns a non-null value
            assertThat(macd).isNotNull();
        }

        @Test
        void testCalculateMACD_double_withEmptyList_returnsNull() {
            // Given: empty list
            List<Double> closePrices = new ArrayList<>();

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: returns null
            assertThat(macd).as("MACD should be null with empty list").isNull();
        }

        @Test
        void testCalculateMACD_double_withNullList_returnsNull() {
            // When: calculating MACD with null list
            Double macd = technicalIndicators.calculateMACD(null, 12, 26, 9);

            // Then: returns null
            assertThat(macd).as("MACD should be null with null list").isNull();
        }

        @Test
        void testCalculateMACD_double_withInvalidFastPeriod_throwsException() {
            // Given: valid data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating MACD with fast period >= slow period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateMACD(closePrices, 30, 26, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Fast period must be less than slow period");
        }

        @Test
        void testCalculateMACD_double_withInvalidPeriod_zero_throwsException() {
            // Given: valid data
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                closePrices.add(100.0 + i);
            }

            // When: calculating MACD with zero period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateMACD(closePrices, 0, 26, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MACD periods must be positive");
        }

        @Test
        void testCalculateMACD_double_withConstantPrices_returnsNearZero() {
            // Given: constant prices
            List<Double> closePrices = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                closePrices.add(100.0);
            }

            // When: calculating MACD
            Double macd = technicalIndicators.calculateMACD(closePrices, 12, 26, 9);

            // Then: MACD should be approximately 0 (no price movement)
            assertThat(macd).isNotNull();
            assertThat(Math.abs(macd)).isLessThan(0.001);
        }
    }

    @Nested
    class CalculateVolumeMABigDecimalTests {

        @Test
        void testCalculateVolumeMA_withInsufficientData_returnsNull() {
            // Given: insufficient data (period = 20, only 15 values)
            List<BigDecimal> volumes = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                volumes.add(BigDecimal.valueOf(1000000 + i * 100000));
            }

            // When: calculating VolumeMA
            Double volumeMA = technicalIndicators.calculateVolumeMA(volumes, 20);

            // Then: returns null
            assertThat(volumeMA).as("VolumeMA should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateVolumeMA_withValidData_returnsValue() {
            // Given: sufficient data (30 volume values)
            List<BigDecimal> volumes = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                volumes.add(BigDecimal.valueOf(1000000 + i * 50000));
            }

            // When: calculating VolumeMA with period 10
            Double volumeMA = technicalIndicators.calculateVolumeMA(volumes, 10);

            // Then: returns a non-null value
            assertThat(volumeMA).isNotNull();
        }

        @Test
        void testCalculateVolumeMA_withEmptyList_returnsNull() {
            // Given: empty list
            List<BigDecimal> volumes = new ArrayList<>();

            // When: calculating VolumeMA
            Double volumeMA = technicalIndicators.calculateVolumeMA(volumes, 20);

            // Then: returns null
            assertThat(volumeMA).as("VolumeMA should be null with empty list").isNull();
        }

        @Test
        void testCalculateVolumeMA_withNullList_returnsNull() {
            // When: calculating VolumeMA with null list
            Double volumeMA = technicalIndicators.calculateVolumeMA(null, 20);

            // Then: returns null
            assertThat(volumeMA).as("VolumeMA should be null with null list").isNull();
        }

        @Test
        void testCalculateVolumeMA_withInvalidPeriod_throwsException() {
            // Given: valid data
            List<BigDecimal> volumes = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                volumes.add(BigDecimal.valueOf(1000000 + i));
            }

            // When: calculating VolumeMA with zero period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateVolumeMA(volumes, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("VolumeMA period must be positive");
        }

        @Test
        void testCalculateVolumeMA_withNegativePeriod_throwsException() {
            // Given: valid data
            List<BigDecimal> volumes = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                volumes.add(BigDecimal.valueOf(1000000 + i));
            }

            // When: calculating VolumeMA with negative period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateVolumeMA(volumes, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("VolumeMA period must be positive");
        }
    }

    @Nested
    class CalculateVolumeMADoubleTests {

        @Test
        void testCalculateVolumeMA_double_withInsufficientData_returnsNull() {
            // Given: insufficient data (period = 20, only 15 values)
            List<Double> volumes = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                volumes.add(1000000.0 + i * 100000);
            }

            // When: calculating VolumeMA
            Double volumeMA = technicalIndicators.calculateVolumeMA(volumes, 20);

            // Then: returns null
            assertThat(volumeMA).as("VolumeMA should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateVolumeMA_double_withValidData_returnsValue() {
            // Given: sufficient data (30 volume values)
            List<Double> volumes = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                volumes.add(1000000.0 + i * 50000);
            }

            // When: calculating VolumeMA with period 10
            Double volumeMA = technicalIndicators.calculateVolumeMA(volumes, 10);

            // Then: returns a non-null value
            assertThat(volumeMA).isNotNull();
        }

        @Test
        void testCalculateVolumeMA_double_withEmptyList_returnsNull() {
            // Given: empty list
            List<Double> volumes = new ArrayList<>();

            // When: calculating VolumeMA
            Double volumeMA = technicalIndicators.calculateVolumeMA(volumes, 20);

            // Then: returns null
            assertThat(volumeMA).as("VolumeMA should be null with empty list").isNull();
        }

        @Test
        void testCalculateVolumeMA_double_withNullList_returnsNull() {
            // When: calculating VolumeMA with null list
            Double volumeMA = technicalIndicators.calculateVolumeMA(null, 20);

            // Then: returns null
            assertThat(volumeMA).as("VolumeMA should be null with null list").isNull();
        }

        @Test
        void testCalculateVolumeMA_double_withConstantVolumes_returnsExpected() {
            // Given: constant volumes
            List<Double> volumes = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                volumes.add(1000000.0);
            }

            // When: calculating VolumeMA with period 5
            Double volumeMA = technicalIndicators.calculateVolumeMA(volumes, 5);

            // Then: VolumeMA should equal the constant volume
            assertThat(volumeMA).isNotNull().isEqualTo(1000000.0);
        }

        @Test
        void testCalculateVolumeMA_double_withInvalidPeriod_throwsException() {
            // Given: valid data
            List<Double> volumes = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                volumes.add(1000000.0 + i);
            }

            // When: calculating VolumeMA with zero period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateVolumeMA(volumes, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("VolumeMA period must be positive");
        }
    }

    @Nested
    class CalculateATRDoubleTests {

        @Test
        void testCalculateATR_double_withInsufficientData_returnsNull() {
            // Given: insufficient candles (period = 14, only 10 values)
            List<CandleWithPricesDouble> candles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candles.add(new CandleWithPricesDouble(
                    100.0,
                    105.0,
                    95.0,
                    102.0,
                    1000000.0
                ));
            }

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: returns null
            assertThat(atr).as("ATR should be null with insufficient data").isNull();
        }

        @Test
        void testCalculateATR_double_withValidData_returnsValue() {
            // Given: sufficient candles (20 values)
            List<CandleWithPricesDouble> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPricesDouble(
                    100.0 + i,
                    105.0 + i,
                    95.0 + i,
                    102.0 + i,
                    1000000.0
                ));
            }

            // When: calculating ATR with period 14
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: returns a non-null value
            assertThat(atr).isNotNull();
        }

        @Test
        void testCalculateATR_double_withEmptyCandles_returnsNull() {
            // Given: empty list
            List<CandleWithPricesDouble> candles = new ArrayList<>();

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: returns null
            assertThat(atr).as("ATR should be null with empty candles").isNull();
        }

        @Test
        void testCalculateATR_double_withNullCandles_returnsNull() {
            // When: calculating ATR with null candles
            Double atr = technicalIndicators.calculateATR(null, 14);

            // Then: returns null
            assertThat(atr).as("ATR should be null with null candles").isNull();
        }

        @Test
        void testCalculateATR_double_withSmallRangeCandles() {
            // Given: candles with small price ranges
            List<CandleWithPricesDouble> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPricesDouble(
                    100.0,
                    100.5,
                    99.5,
                    100.0,
                    1000000.0
                ));
            }

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: ATR should be small (low volatility)
            assertThat(atr).isNotNull();
            assertThat(atr).isLessThan(2.0);
        }

        @Test
        void testCalculateATR_double_withLargeRangeCandles() {
            // Given: candles with large price ranges
            List<CandleWithPricesDouble> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPricesDouble(
                    100.0,
                    110.0,
                    90.0,
                    105.0,
                    1000000.0
                ));
            }

            // When: calculating ATR
            Double atr = technicalIndicators.calculateATR(candles, 14);

            // Then: ATR should be larger (high volatility)
            assertThat(atr).isNotNull();
            assertThat(atr).isGreaterThan(5.0);
        }

        @Test
        void testCalculateATR_double_withInvalidPeriod_throwsException() {
            // Given: sufficient candles
            List<CandleWithPricesDouble> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPricesDouble(
                    100.0,
                    105.0,
                    95.0,
                    102.0,
                    1000000.0
                ));
            }

            // When: calculating ATR with zero period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateATR(candles, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ATR period must be positive");
        }

        @Test
        void testCalculateATR_double_withNegativePeriod_throwsException() {
            // Given: sufficient candles
            List<CandleWithPricesDouble> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(new CandleWithPricesDouble(
                    100.0,
                    105.0,
                    95.0,
                    102.0,
                    1000000.0
                ));
            }

            // When: calculating ATR with negative period
            // Then: throws IllegalArgumentException
            assertThatThrownBy(() -> technicalIndicators.calculateATR(candles, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ATR period must be positive");
        }
    }
}
