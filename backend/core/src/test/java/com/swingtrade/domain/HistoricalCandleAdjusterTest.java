package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalCandleAdjusterTest {
    private static final Instant RECORDED_AT = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void appliesPreEffectiveSplitAndCashToAnalyticalPricesAndVolume() {
        OhlcvCandle raw = OhlcvCandle.of("TCS", LocalDate.of(2024, 1, 1),
            bd("100"), bd("110"), bd("90"), bd("105"), 1_000L);
        CorporateAction action = new CorporateAction("TCS", LocalDate.of(2024, 1, 3), "SPLIT",
            bd("0.5"), null, "exchange", RECORDED_AT);

        OhlcvCandle adjusted = HistoricalCandleAdjuster.adjust(raw, List.of(action));

        assertThat(adjusted.open()).isEqualByComparingTo("50");
        assertThat(adjusted.high()).isEqualByComparingTo("55");
        assertThat(adjusted.close()).isEqualByComparingTo("52.5");
        assertThat(adjusted.volume()).isEqualTo(2_000L);
        assertThat(raw.open()).isEqualByComparingTo("100");
    }

    @Test
    void doesNotApplyActionOnItsEffectiveDate() {
        OhlcvCandle raw = OhlcvCandle.of("TCS", LocalDate.of(2024, 1, 3),
            bd("100"), bd("110"), bd("90"), bd("105"), 1_000L);
        CorporateAction action = new CorporateAction("TCS", LocalDate.of(2024, 1, 3), "SPLIT",
            bd("0.5"), null, "exchange", RECORDED_AT);

        assertThat(HistoricalCandleAdjuster.adjust(raw, List.of(action))).isEqualTo(raw);
    }

    @Test
    void failsClosedWhenCashMakesHistoricalPriceNonPositive() {
        OhlcvCandle raw = OhlcvCandle.of("TCS", LocalDate.of(2024, 1, 1),
            bd("10"), bd("11"), bd("9"), bd("10"), 1_000L);
        CorporateAction action = new CorporateAction("TCS", LocalDate.of(2024, 1, 2), "DIVIDEND",
            null, bd("10"), "exchange", RECORDED_AT);

        assertThatThrownBy(() -> HistoricalCandleAdjuster.adjust(raw, List.of(action)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-positive price");
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
