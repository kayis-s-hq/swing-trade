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

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.store.CandleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("PriceActionSignalEngine")
@ExtendWith(MockitoExtension.class)
class PriceActionSignalEngineTest {

    @Mock
    private CandleStore candleStore;

    private PriceActionSignalEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PriceActionSignalEngine(candleStore, org.mockito.Mockito.mock(com.swingtrade.core.metrics.SignalMetrics.class));
    }

    @Nested
    class InputValidation {

        @Test
        void generateSignal_withNullSymbol_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> engine.generateSignal(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Symbol cannot be null or blank");
        }

        @Test
        void generateSignal_withBlankSymbol_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> engine.generateSignal("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Symbol cannot be null or blank");
        }

        @Test
        void generateSignal_withInsufficientCandles_throwsIllegalStateException() {
            List<OhlcvCandle> tooFew = buildTrendingCandles(10, 100.0, 0.0, 1_000_000L);
            when(candleStore.findTopBySymbolOrderByDateDesc("RELIANCE", 1000)).thenReturn(tooFew);

            assertThatThrownBy(() -> engine.generateSignal("RELIANCE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient candle history");
        }
    }

    @Nested
    class EntryRules {

        @Test
        @DisplayName("all rules satisfied -> BUY")
        void analyze_withAllConditionsMet_returnsBuySignal() {
            // A 2-up/1-down zigzag uptrend keeps RSI in the healthy 50-65 band (unlike a
            // monotonic rise, which pegs RSI near 100) while still holding price > EMA20 >
            // EMA50. The final candle adds a volume surge and a fresh high to satisfy the
            // remaining two rules.
            List<OhlcvCandle> candles = buildZigzagUptrendCandles(299, 100.0, 0.5, 0.75, 1_000_000L);
            OhlcvCandle lastCandle = candles.get(candles.size() - 1);
            candles.add(buildFinalCandle(lastCandle, 1.005, 2_000_000L));

            SignalResult result = engine.analyze("RELIANCE", candles);

            assertThat(result.symbol()).isEqualTo("RELIANCE");
            assertThat(result.type()).isEqualTo(SignalType.BUY);
            assertThat(result.rsi()).isBetween(50.0, 65.0);
            assertThat(result.reasoning()).startsWith("All entry rules passed");
        }

        @Test
        @DisplayName("RSI outside 50-65 -> HOLD")
        void analyze_withRsiOutOfRange_returnsHoldSignal() {
            // Flat/declining series pushes RSI well below 50.
            List<OhlcvCandle> candles = buildTrendingCandles(300, 100.0, -0.05, 1_000_000L);

            SignalResult result = engine.analyze("RELIANCE", candles);

            assertThat(result.type()).isEqualTo(SignalType.HOLD);
            assertThat(result.reasoning()).startsWith("Entry rules failed");
            assertThat(result.reasoning()).contains("RSI between 50-65");
        }

        @Test
        @DisplayName("volume below 1.5x average -> HOLD")
        void analyze_withoutVolumeSurge_returnsHoldSignal() {
            List<OhlcvCandle> candles = buildTrendingCandles(300, 100.0, 0.15, 1_000_000L);
            // No volume bump on the final candle, so the volume-surge rule should fail.

            SignalResult result = engine.analyze("RELIANCE", candles);

            assertThat(result.reasoning()).contains("Volume > 1.5x VolumeMA20");
        }

        @Test
        @DisplayName("price far from 52-week high -> HOLD")
        void analyze_withPriceFarFromWeeklyHigh_returnsHoldSignal() {
            List<OhlcvCandle> candles = buildTrendingCandles(300, 100.0, 0.15, 1_000_000L);
            // Crash the final candle well below the accumulated high.
            OhlcvCandle last = candles.get(candles.size() - 1);
            OhlcvCandle replaced = OhlcvCandle.of(last.symbol(), last.date(),
                BigDecimal.valueOf(50), BigDecimal.valueOf(51),
                BigDecimal.valueOf(49), BigDecimal.valueOf(50), last.volume());

            int idx = candles.indexOf(last);
            candles.set(idx, replaced);

            SignalResult result = engine.analyze("RELIANCE", candles);

            assertThat(result.type()).isEqualTo(SignalType.HOLD);
            assertThat(result.reasoning()).contains("Price within 3% of 52-week high");
        }
    }

    /**
     * Builds a chronologically-ordered (oldest first) synthetic candle series with a
     * constant daily drift percentage, used to control EMA/RSI/trend behavior deterministically.
     */
    private List<OhlcvCandle> buildTrendingCandles(int count, double startPrice, double dailyDriftPercent, long volume) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            double close = price * (1 + dailyDriftPercent / 100.0);
            double open = price;
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;

            OhlcvCandle candle = OhlcvCandle.of("RELIANCE", date,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume);

            candles.add(candle);
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    /**
     * Builds a chronologically-ordered zigzag uptrend: two up days followed by one down
     * day, repeating. This keeps Wilder's RSI oscillating around a mid-range steady state
     * (roughly 55-60 for the up/down magnitudes used in these tests) instead of pegging
     * near 100 like a monotonic rise would.
     */
    private List<OhlcvCandle> buildZigzagUptrendCandles(int count, double startPrice,
                                                               double upPercent, double downPercent, long volume) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            double changePercent = (i % 3 == 2) ? -downPercent : upPercent;
            double open = price;
            double close = price * (1 + changePercent / 100.0);
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;

            OhlcvCandle candle = OhlcvCandle.of("RELIANCE", date,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume);

            candles.add(candle);
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    /**
     * Builds a single final candle that closes {@code closeMultiplier}x above the previous
     * candle's close, on the given volume, dated the day after it.
     */
    private OhlcvCandle buildFinalCandle(OhlcvCandle previous, double closeMultiplier, long volume) {
        BigDecimal previousClose = previous.close();
        BigDecimal close = previousClose.multiply(BigDecimal.valueOf(closeMultiplier));
        BigDecimal high = close.multiply(BigDecimal.valueOf(1.001));
        BigDecimal low = previousClose.multiply(BigDecimal.valueOf(0.999));

        OhlcvCandle candle = OhlcvCandle.of("RELIANCE", previous.date().plusDays(1),
            previousClose, high, low, close, volume);
        return candle;
    }
}
