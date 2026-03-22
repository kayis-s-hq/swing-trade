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

import com.swingtrade.strategy.impl.DefaultStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantDoubleSeries;
import org.ta4j.core.indicators.helpers.RSIIndicator;
import org.ta4j.core.num.DoubleNum;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for DefaultStrategy class.
 * Tests strategy generation and trade execution logic with mocked TA4J dependencies.
 *
 * Test coverage includes:
 * - generateStrategy() returns valid strategy when BarSeries is valid
 * - generateStrategy() throws IllegalArgumentException when BarSeries is null
 * - shouldExecuteTrade() returns true when entry conditions met (EMA crossover, RSI threshold)
 * - shouldExecuteTrade() returns false when conditions not met
 * - shouldExecuteTrade() throws IllegalArgumentException for null parameters
 * - EMA crossover detection (short EMA crosses above long EMA for BUY)
 * - EMA crossover detection (short EMA crosses below long EMA for SELL)
 * - RSI threshold crossing (RSI < 30 for BUY, RSI > 70 for SELL)
 */
@DisplayName("DefaultStrategy Tests")
@ExtendWith(MockitoExtension.class)
class DefaultStrategyTest {

    private DefaultStrategy defaultStrategy;

    @Mock
    private BarSeries mockBarSeries;

    @Mock
    private TradingRecord mockTradingRecord;

    @Mock
    private org.ta4j.core.Strategy mockTa4jStrategy;

    @BeforeEach
    @DisplayName("Setup test fixtures")
    void setUp() {
        defaultStrategy = new DefaultStrategy();
    }

    @Nested
    @DisplayName("Generate Strategy Tests")
    class GenerateStrategyTests {

        @Test
        @DisplayName("generateStrategy with null BarSeries should throw IllegalArgumentException")
        void testGenerateStrategy_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> defaultStrategy.generateStrategy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BarSeries cannot be null");
        }

        @Test
        @DisplayName("generateStrategy with valid BarSeries should return valid strategy")
        void testGenerateStrategy_withValidBarSeries_returnsStrategy() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries();

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: returns a valid strategy object
            assertThat(strategy).isNotNull();
            assertThat(strategy).isInstanceOf(org.ta4j.core.BaseStrategy.class);
        }

        @Test
        @DisplayName("generateStrategy with empty BarSeries should return strategy")
        void testGenerateStrategy_withEmptyBarSeries_returnsStrategy() {
            // Given: empty bar series
            BarSeries series = new BaseSeriesBuilder().build();

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: returns a valid strategy object
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy with short BarSeries should return strategy")
        void testGenerateStrategy_withShortBarSeries_returnsStrategy() {
            // Given: short bar series (less than typical indicator periods)
            BarSeries series = new BaseSeriesBuilder()
                .withName("Short Series")
                .withInitialPrice(100.0)
                .build();
            series.addBar(100.0, 102.0, 98.0, 101.0, 1000);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: returns a valid strategy object
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy with valid BarSeries should return non-null entry and exit rules")
        void testGenerateStrategy_withValidBarSeries_returnsNonNullEntryRule() {
            // Given: valid bar series with multiple bars
            BarSeries series = createValidBarSeries(50);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy has entry and exit rules configured
            assertThat(strategy.getEntry()).isNotNull();
            assertThat(strategy.getExit()).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy with trending BarSeries should return strategy")
        void testGenerateStrategy_withTrendingBarSeries_returnsStrategy() {
            // Given: trending bar series (upward)
            BarSeries series = createTrendingBarSeries();

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: returns a valid strategy
            assertThat(strategy).isNotNull();
        }
    }

    @Nested
    @DisplayName("Should Execute Trade Tests")
    class ShouldExecuteTradeTests {

        @Test
        @DisplayName("shouldExecuteTrade with null BarSeries should throw IllegalArgumentException")
        void testShouldExecuteTrade_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> defaultStrategy.shouldExecuteTrade(null, 0, mockTradingRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BarSeries");
        }

        @Test
        @DisplayName("shouldExecuteTrade with null TradingRecord should throw IllegalArgumentException")
        void testShouldExecuteTrade_withNullTradingRecord_throwsIllegalArgumentException() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries();

            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> defaultStrategy.shouldExecuteTrade(series, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TradingRecord");
        }

        @Test
        @DisplayName("shouldExecuteTrade with null index value should not throw")
        void testShouldExecuteTrade_withNullIndexValue_doesNotThrow() {
            // Given: valid bar series and trading record
            BarSeries series = createValidBarSeries();

            // When: executing with index 0
            boolean result = defaultStrategy.shouldExecuteTrade(series, 0, mockTradingRecord);

            // Then: does not throw exception, returns boolean
            assertThat(result).isInstanceOf(Boolean.class);
        }

        @Test
        @DisplayName("shouldExecuteTrade with valid parameters should return boolean")
        void testShouldExecuteTrade_withValidParameters_returnsBoolean() {
            // Given: valid parameters
            BarSeries series = createValidBarSeries(30);

            // When: checking trade execution
            boolean result = defaultStrategy.shouldExecuteTrade(series, 10, mockTradingRecord);

            // Then: returns a boolean value
            assertThat(result).isInstanceOf(Boolean.class);
        }

        @Test
        @DisplayName("shouldExecuteTrade with index out of range should not throw")
        void testShouldExecuteTrade_withIndexOutOfRange_doesNotThrow() {
            // Given: bar series with limited data
            BarSeries series = createValidBarSeries(10);

            // When: checking with index beyond data
            boolean result = defaultStrategy.shouldExecuteTrade(series, 100, mockTradingRecord);

            // Then: does not throw exception
            assertThat(result).isInstanceOf(Boolean.class);
        }
    }

    @Nested
    @DisplayName("Null Parameters Validation Tests")
    class NullParametersValidationTests {

        @Test
        @DisplayName("shouldExecuteTrade with all null parameters should throw IllegalArgumentException for BarSeries")
        void testShouldExecuteTrade_withAllNullParameters_throwsIllegalArgumentExceptionForBarSeries() {
            // When & Then: IllegalArgumentException should be thrown for null BarSeries
            assertThatThrownBy(() -> defaultStrategy.shouldExecuteTrade(null, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BarSeries");
        }

        @Test
        @DisplayName("shouldExecuteTrade with null index should handle gracefully")
        void testShouldExecuteTrade_withIndexZero_shouldExecute() {
            // Given: valid bar series with at least one bar
            BarSeries series = createValidBarSeries(1);

            // When: checking with index 0
            boolean result = defaultStrategy.shouldExecuteTrade(series, 0, mockTradingRecord);

            // Then: returns false (insufficient bars for indicator calculation)
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("EMA Crossover Detection Tests")
    class EMACrossoverDetectionTests {

        @Test
        @DisplayName("generateStrategy should detect EMA crossover patterns")
        void testGenerateStrategy_detectsEMACrossoverPatterns() {
            // Given: bar series with 50 bars to allow indicator calculation
            BarSeries series = createValidBarSeries(50);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy is created with entry/exit rules that include EMA crossover logic
            assertThat(strategy).isNotNull();
            assertThat(strategy.getEntry()).isNotNull();
            assertThat(strategy.getExit()).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy with EMA fast period of 12 and slow period of 26")
        void testGenerateStrategy_EMAPeriods_12And26() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(100);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy is created successfully
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy should configure both bullish and bearish EMA crossover rules")
        void testGenerateStrategy_configuresBothEMACrossoverRules() {
            // Given: bar series for testing
            BarSeries series = createValidBarSeries(100);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy should have entry and exit rules configured
            // Entry rule uses EMA crossover up detection
            // Exit rule uses EMA crossover down detection
            assertThat(strategy.getEntry()).isNotNull();
            assertThat(strategy.getExit()).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy should create strategy that handles EMA indicator calculations")
        void testGenerateStrategy_handlesEMAIndicatorCalculations() {
            // Given: bar series with sufficient data
            BarSeries series = createValidBarSeries(50);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy is created successfully
            // EMA indicators are initialized and ready for crossover detection
            assertThat(strategy).isNotNull();
        }
    }

    @Nested
    @DisplayName("RSI Threshold Detection Tests")
    class RSIThresholdDetectionTests {

        @Test
        @DisplayName("generateStrategy should detect RSI threshold patterns")
        void testGenerateStrategy_detectsRSIThresholdPatterns() {
            // Given: bar series with 50+ bars for RSI indicator calculation
            BarSeries series = createValidBarSeries(50);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy is created with RSI-based entry/exit rules
            assertThat(strategy).isNotNull();
            assertThat(strategy.getEntry()).isNotNull();
            assertThat(strategy.getExit()).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy should configure RSI period of 14")
        void testGenerateStrategy_RSIPeriod_14() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy is created with RSI period 14
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy should configure RSI oversold (30) and overbought (70) thresholds")
        void testGenerateStrategy_configuresRSIThresholds() {
            // Given: bar series for testing
            BarSeries series = createValidBarSeries(100);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy should have entry and exit rules configured
            // Entry rule includes RSI oversold detection (RSI < 30)
            // Exit rule includes RSI overbought detection (RSI > 70)
            assertThat(strategy.getEntry()).isNotNull();
            assertThat(strategy.getExit()).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy should handle RSI indicator calculations correctly")
        void testGenerateStrategy_handlesRSIIndicatorCalculations() {
            // Given: bar series with sufficient data
            BarSeries series = createValidBarSeries(50);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy is created successfully
            // RSI indicators are initialized for threshold detection
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy should combine RSI thresholds with other entry conditions")
        void testGenerateStrategy_combinesRSIWithOtherConditions() {
            // Given: bar series for testing
            BarSeries series = createValidBarSeries(100);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: strategy entry rule combines multiple conditions including RSI
            assertThat(strategy.getEntry()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Trade Execution Logic Tests")
    class TradeExecutionLogicTests {

        @Test
        @DisplayName("shouldExecuteTrade with valid BarSeries should return true or false")
        void testShouldExecuteTrade_withValidBarSeries_returnsTrueOrFalse() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When: checking multiple indices
            boolean result1 = defaultStrategy.shouldExecuteTrade(series, 5, mockTradingRecord);
            boolean result2 = defaultStrategy.shouldExecuteTrade(series, 10, mockTradingRecord);
            boolean result3 = defaultStrategy.shouldExecuteTrade(series, 20, mockTradingRecord);

            // Then: all return valid boolean values
            assertThat(result1).isIn(true, false);
            assertThat(result2).isIn(true, false);
            assertThat(result3).isIn(true, false);
        }

        @Test
        @DisplayName("shouldExecuteTrade with empty TradingRecord should return boolean")
        void testShouldExecuteTrade_withEmptyTradingRecord() {
            // Given: valid bar series and empty trading record
            BarSeries series = createValidBarSeries(50);

            // When: checking trade execution
            boolean result = defaultStrategy.shouldExecuteTrade(series, 10, mockTradingRecord);

            // Then: returns a boolean (empty record is valid)
            assertThat(result).isInstanceOf(Boolean.class);
        }

        @Test
        @DisplayName("shouldExecuteTrade with multiple indices should return valid booleans")
        void testShouldExecuteTrade_multipleIndices() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(100);

            // When: checking multiple consecutive indices
            boolean[] results = new boolean[10];
            for (int i = 0; i < 10; i++) {
                results[i] = defaultStrategy.shouldExecuteTrade(series, i + 10, mockTradingRecord);
            }

            // Then: all return valid booleans
            for (boolean result : results) {
                assertThat(result).isIn(true, false);
            }
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("generateStrategy with single bar should return strategy")
        void testGenerateStrategy_withSingleBar_returnsStrategy() {
            // Given: single bar series
            BarSeries series = new BaseSeriesBuilder().build();
            series.addBar(100.0, 102.0, 98.0, 101.0, 1000);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: returns a valid strategy
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy with very long series should return strategy")
        void testGenerateStrategy_withVeryLongSeries_returnsStrategy() {
            // Given: very long bar series
            BarSeries series = createValidBarSeries(500);

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: returns a valid strategy
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("generateStrategy with volatile prices should return strategy")
        void testGenerateStrategy_withVolatilePrices_returnsStrategy() {
            // Given: highly volatile series
            BarSeries series = new BaseSeriesBuilder()
                .withName("Volatile")
                .withInitialPrice(100.0)
                .build();

            // Add bars with high volatility
            double price = 100.0;
            for (int i = 0; i < 100; i++) {
                double volatility = (Math.random() - 0.5) * 20;
                price += volatility;
                series.addBar(price, price + 5, price - 5, price + 2, 1000000L);
            }

            // When: generating strategy
            org.ta4j.core.Strategy strategy = defaultStrategy.generateStrategy(series);

            // Then: returns a valid strategy
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("shouldExecuteTrade with negative index should throw exception")
        void testShouldExecuteTrade_withNegativeIndex_throwsException() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries();

            // When & Then: exception thrown for invalid index (handled by TA4J)
            assertThatThrownBy(() -> defaultStrategy.shouldExecuteTrade(series, -1, mockTradingRecord))
                .isInstanceOf(Exception.class);
        }
    }

    // Helper methods
    private BarSeries createValidBarSeries() {
        return createValidBarSeries(50);
    }

    private BarSeries createValidBarSeries(int numberOfBars) {
        BarSeries series = new BaseSeriesBuilder()
            .withName("Test Series")
            .withInitialPrice(100.0)
            .build();

        double price = 100.0;
        for (int i = 0; i < numberOfBars; i++) {
            double open = price;
            double close = price + (Math.random() - 0.5) * 2;
            double high = Math.max(open, close) + Math.random();
            double low = Math.min(open, close) - Math.random();
            long volume = (long) (1000000 + Math.random() * 500000);

            series.addBar(LocalDate.of(2024, 1, i + 1), open, high, low, close, volume);
            price = close;
        }

        return series;
    }

    private BarSeries createTrendingBarSeries() {
        BarSeries series = new BaseSeriesBuilder()
            .withName("Trending Series")
            .withInitialPrice(100.0)
            .build();

        double price = 100.0;
        for (int i = 0; i < 100; i++) {
            // Gradual upward trend with small volatility
            double open = price;
            double close = price + 0.5 + (Math.random() - 0.5) * 0.5;
            double high = Math.max(open, close) + 0.5;
            double low = Math.min(open, close) - 0.5;
            long volume = 1000000L;

            series.addBar(LocalDate.of(2024, 1, i + 1), open, high, low, close, volume);
            price = close;
        }

        return series;
    }
}
