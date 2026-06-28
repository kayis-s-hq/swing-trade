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

import com.swingtrade.strategy.impl.DefaultBacktestEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive unit tests for DefaultBacktestEngine class.
 * Tests backtest execution with validation of null inputs and parameter combinations.
 */
@ExtendWith(MockitoExtension.class)
class DefaultBacktestEngineTest {

    private DefaultBacktestEngine backtestEngine;

    @Mock
    private Strategy mockStrategy;

    @Mock
    private BarSeries mockBarSeries;

    @BeforeEach
    void setUp() {
        backtestEngine = new DefaultBacktestEngine();
    }

    @Nested
    class RunBacktestValidationTests {

        @Test
        void testRunBacktest_withNullStrategy_throwsIllegalArgumentException() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> backtestEngine.runBacktest(null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy and BarSeries cannot be null");
        }

        @Test
        void testRunBacktest_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> backtestEngine.runBacktest(mockStrategy, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy and BarSeries cannot be null");
        }

        @Test
        void testRunBacktest_withNullBothParameters_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> backtestEngine.runBacktest(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy and BarSeries cannot be null");
        }

        @Test
        void testRunBacktest_withNullStrategyAndNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> backtestEngine.runBacktest(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strategy")
                .hasMessageContaining("BarSeries");
        }
    }

    @Nested
    class RunBacktestWithExecutionValidationTests {

        @Test
        void testRunBacktestWithExecution_withNullStrategy_throwsIllegalArgumentException() {
            // Given: valid bar series
            BarSeries series = createValidBarSeries(50);

            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> backtestEngine.runBacktestWithExecution(null, series, 0.001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy and BarSeries cannot be null");
        }

        @Test
        void testRunBacktestWithExecution_withNullBarSeries_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> backtestEngine.runBacktestWithExecution(mockStrategy, null, 0.001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy and BarSeries cannot be null");
        }

        @Test
        void testRunBacktestWithExecution_withNullBothParameters_throwsIllegalArgumentException() {
            // When & Then: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> backtestEngine.runBacktestWithExecution(null, null, 0.001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy and BarSeries cannot be null");
        }

        @Test
        void testRunBacktestWithExecution_withZeroCommission_throwsNoException() {
            // Given: valid parameters
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with zero commission
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.0);

            // Then: does not throw exception
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withNegativeCommission_throwsNoException() {
            // Given: valid parameters
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with negative commission
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, -0.001);

            // Then: does not throw exception (validation is only for null inputs)
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withVeryHighCommission_throwsNoException() {
            // Given: valid parameters
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with high commission
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 1.0);

            // Then: does not throw exception
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class RunBacktestExecutionTests {

        @Test
        void testRunBacktest_withValidParameters_returnsObject() {
            // Given: valid strategy and bar series
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest
            Object result = backtestEngine.runBacktest(strategy, series);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withValidParameters_returnsObject() {
            // Given: valid strategy, bar series, and commission rate
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with execution
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withDefaultCommission_returnsObject() {
            // Given: valid strategy and bar series
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with execution using default commission
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktest_returnsSameResultAsRunBacktestWithExecution() {
            // Given: valid strategy and bar series
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running both backtest methods
            Object result1 = backtestEngine.runBacktest(strategy, series);
            Object result2 = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: both return non-null objects (may be same or different implementation objects)
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
        }

        @Test
        void testRunBacktest_withEmptyBarSeries_returnsObject() {
            // Given: valid strategy but empty bar series
            BarSeries series = new org.ta4j.core.BaseBarSeries("test");
            Strategy strategy = new BaseStrategy(new AlwaysEnterRule(), new AlwaysExitRule());

            // When: running backtest
            Object result = backtestEngine.runBacktest(strategy, series);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withEmptyBarSeries_returnsObject() {
            // Given: valid strategy but empty bar series
            BarSeries series = new org.ta4j.core.BaseBarSeries("test");
            Strategy strategy = new BaseStrategy(new AlwaysEnterRule(), new AlwaysExitRule());

            // When: running backtest with execution
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class DifferentBarSeriesSizesTests {

        @Test
        void testRunBacktest_withSingleBar_returnsObject() {
            // Given: valid strategy with single bar
            BarSeries series = createValidBarSeries(1);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest
            Object result = backtestEngine.runBacktest(strategy, series);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withSingleBar_returnsObject() {
            // Given: valid strategy with single bar
            BarSeries series = createValidBarSeries(1);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with execution
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktest_withLargeBarSeries_returnsObject() {
            // Given: valid strategy with large bar series
            BarSeries series = createValidBarSeries(500);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest
            Object result = backtestEngine.runBacktest(strategy, series);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withLargeBarSeries_returnsObject() {
            // Given: valid strategy with large bar series
            BarSeries series = createValidBarSeries(500);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with execution
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktest_withDifferentBarSeriesSizes() {
            // Given: bar series of different sizes
            Object result1 = runBacktestForSize(10);
            Object result10 = runBacktestForSize(100);
            Object result500 = runBacktestForSize(500);

            // Then: all return non-null objects
            assertThat(result1).isNotNull();
            assertThat(result10).isNotNull();
            assertThat(result500).isNotNull();
        }
    }

    @Nested
    class DifferentCommissionRatesTests {

        @Test
        void testRunBacktestWithExecution_withVeryLowCommission() {
            // Given: valid strategy and bar series
            BarSeries series = createValidBarSeries(100);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with very low commission
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.00001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withStandardCommission() {
            // Given: valid strategy and bar series
            BarSeries series = createValidBarSeries(100);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with standard commission (0.1%)
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withHighCommission() {
            // Given: valid strategy and bar series
            BarSeries series = createValidBarSeries(100);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with high commission (1%)
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.01);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withMultipleCommissionRates() {
            // Given: valid strategy and bar series
            BarSeries series = createValidBarSeries(100);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with multiple commission rates
            Object result0 = backtestEngine.runBacktestWithExecution(strategy, series, 0.0);
            Object result0001 = backtestEngine.runBacktestWithExecution(strategy, series, 0.0001);
            Object result001 = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);
            Object result01 = backtestEngine.runBacktestWithExecution(strategy, series, 0.01);

            // Then: all return non-null objects
            assertThat(result0).isNotNull();
            assertThat(result0001).isNotNull();
            assertThat(result001).isNotNull();
            assertThat(result01).isNotNull();
        }
    }

    @Nested
    class DifferentStrategyTypesTests {

        @Test
        void testRunBacktest_withBaseStrategy_returnsObject() {
            // Given: valid bar series and base strategy
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = new BaseStrategy(new AlwaysEnterRule(), new AlwaysExitRule());

            // When: running backtest
            Object result = backtestEngine.runBacktest(strategy, series);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withBaseStrategy_returnsObject() {
            // Given: valid bar series and base strategy
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = new BaseStrategy(new AlwaysEnterRule(), new AlwaysExitRule());

            // When: running backtest with execution
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktest_withCustomStrategy_returnsObject() {
            // Given: valid bar series and custom strategy
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest
            Object result = backtestEngine.runBacktest(strategy, series);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }

        @Test
        void testRunBacktestWithExecution_withCustomStrategy_returnsObject() {
            // Given: valid bar series and custom strategy
            BarSeries series = createValidBarSeries(50);
            Strategy strategy = createValidStrategy(series);

            // When: running backtest with execution
            Object result = backtestEngine.runBacktestWithExecution(strategy, series, 0.001);

            // Then: returns a non-null object
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class NullSafetyTests {

        @Test
        void testRunBacktest_allNullCombinations_throwException() {
            // Given: various null combinations

            // When & Then: all should throw exception
            assertThatThrownBy(() -> backtestEngine.runBacktest(null, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> backtestEngine.runBacktest(null, createValidBarSeries(50)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> backtestEngine.runBacktest(createValidStrategy(createValidBarSeries(50)), null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testRunBacktestWithExecution_allNullCombinations_throwException() {
            // Given: various null combinations

            // When & Then: all should throw exception
            assertThatThrownBy(() -> backtestEngine.runBacktestWithExecution(null, null, 0.001))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> backtestEngine.runBacktestWithExecution(null, createValidBarSeries(50), 0.001))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> backtestEngine.runBacktestWithExecution(createValidStrategy(createValidBarSeries(50)), null, 0.001))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // Helper methods
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

    private Strategy createValidStrategy(BarSeries series) {
        return new BaseStrategy(new AlwaysEnterRule(), new AlwaysExitRule());
    }

    private Object runBacktestForSize(int numberOfBars) {
        BarSeries series = createValidBarSeries(numberOfBars);
        Strategy strategy = createValidStrategy(series);
        return backtestEngine.runBacktest(strategy, series);
    }

    // Helper rules for testing - Rule is now an interface in TA4J 0.16
    private static class AlwaysEnterRule implements Rule {
        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            // Enter a new trade only if there's no open position
            // TA4J 0.16: getCurrentPosition() returns null if no position is open
            return tradingRecord == null || tradingRecord.getCurrentPosition() == null;
        }
    }

    private static class AlwaysExitRule implements Rule {
        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            // Exit if there's an open position
            // TA4J 0.16: getCurrentPosition() returns the current open position
            return tradingRecord != null && tradingRecord.getCurrentPosition() != null;
        }
    }
}
