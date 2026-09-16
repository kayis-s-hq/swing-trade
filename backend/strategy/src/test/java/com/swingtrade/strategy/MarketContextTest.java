package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MarketContext")
class MarketContextTest {

    private static final String SYMBOL = "TESTCO";

    private List<OhlcvCandle> buildCandles(int count) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = 100.0;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            double changePct = (i % 3 == 2) ? -0.5 : 0.6;
            double open = price;
            double close = price * (1 + changePct / 100.0);
            double high = Math.max(open, close) * 1.002;
            double low = Math.min(open, close) * 0.998;
            candles.add(OhlcvCandle.of(SYMBOL, date, BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), 1_000_000L + (i * 1000L)));
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    @Nested
    @DisplayName("no-look-ahead guard")
    class LookAheadGuard {

        @Test
        void view_readingBarAfterBound_throws() {
            MarketContext ctx = MarketContext.of(SYMBOL, buildCandles(60));
            MarketContext.View view = ctx.view(30);

            assertThat(view.close(30)).isNotNull();

            assertThatThrownBy(() -> view.close(31))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Look-ahead");

            assertThatThrownBy(() -> view.ema(20, 31))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Look-ahead");

            assertThatThrownBy(() -> view.rsi(14, 45))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Look-ahead");

            assertThatThrownBy(() -> view.atr(14, 59))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Look-ahead");

            assertThatThrownBy(() -> view.high(31))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Look-ahead");
        }

        @Test
        void view_readingNegativeIndex_throwsIllegalArgument() {
            MarketContext ctx = MarketContext.of(SYMBOL, buildCandles(60));
            MarketContext.View view = ctx.view(30);

            assertThatThrownBy(() -> view.close(-1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void context_viewOutOfRange_throwsIndexOutOfBounds() {
            MarketContext ctx = MarketContext.of(SYMBOL, buildCandles(10));

            assertThatThrownBy(() -> ctx.view(10)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> ctx.view(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        }

        @Test
        void view_defaultAccessorsUseBarIndexItself() {
            MarketContext ctx = MarketContext.of(SYMBOL, buildCandles(60));
            MarketContext.View view = ctx.view(40);

            assertThat(view.close()).isEqualTo(view.close(40));
            assertThat(view.ema(20)).isEqualTo(view.ema(20, 40));
        }
    }

    @Nested
    @DisplayName("indicator cache")
    class IndicatorCache {

        @Test
        void twelveVariants_requestingSameIndicator_computeItOnce() {
            MarketContext ctx = MarketContext.of(SYMBOL, buildCandles(300));
            int barIndex = 250;

            for (int variant = 0; variant < 12; variant++) {
                MarketContext.View view = ctx.view(barIndex);
                assertThat(view.ema(20)).isNotNull();
                assertThat(view.rsi(14)).isNotNull();
            }

            // Only 2 distinct (indicator, period) pairs were ever requested (ema20, rsi14),
            // regardless of 12 separate "variants" and 12 separate View instances asking for
            // them - proves the indicator is computed once and shared (plan §3.5).
            assertThat(ctx.cachedIndicatorCount()).isEqualTo(2);
        }

        @Test
        void differentPeriods_areCachedSeparately() {
            MarketContext ctx = MarketContext.of(SYMBOL, buildCandles(300));
            MarketContext.View view = ctx.view(250);

            view.ema(20);
            view.ema(20);
            view.ema(50);
            view.rsi(14);

            assertThat(ctx.cachedIndicatorCount()).isEqualTo(3);
        }

        @Test
        void sameIndicatorAndPeriod_returnsSameComputedValue_acrossViews() {
            MarketContext ctx = MarketContext.of(SYMBOL, buildCandles(300));

            BigDecimal first = ctx.view(200).ema(20);
            BigDecimal second = ctx.view(200).ema(20);

            assertThat(first).isEqualTo(second);
        }
    }
}
