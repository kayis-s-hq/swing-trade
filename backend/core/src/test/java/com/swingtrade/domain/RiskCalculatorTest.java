package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskCalculatorTest {

    @Test
    void atrIncludesOvernightGapInTrueRange() {
        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            candles.add(OhlcvCandle.of("TEST", LocalDate.of(2026, 1, 1).plusDays(i),
                bd("100"), bd("101"), bd("99"), bd("100"), 1_000L));
        }
        candles.add(OhlcvCandle.of("TEST", LocalDate.of(2026, 1, 15),
            bd("109"), bd("110"), bd("109"), bd("109"), 1_000L));

        BigDecimal atr = RiskCalculator.calculateATR(candles);

        assertThat(atr).isEqualByComparingTo(bd("2.57142857"));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
