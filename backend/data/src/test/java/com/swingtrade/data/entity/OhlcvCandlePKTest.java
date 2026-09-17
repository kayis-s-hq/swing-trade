package com.swingtrade.data.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcvCandlePKTest {
    @Test
    void usesSymbolAndDateForIdentity() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        OhlcvCandlePK first = new OhlcvCandlePK("TCS", date);
        OhlcvCandlePK same = new OhlcvCandlePK("TCS", date);
        OhlcvCandlePK different = new OhlcvCandlePK("INFY", date);

        assertThat(first).isEqualTo(same);
        assertThat(first).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(different);
        assertThat(first).isNotEqualTo(null);
        assertThat(first.getSymbol()).isEqualTo("TCS");
        assertThat(first.getDate()).isEqualTo(date);
        first.setSymbol("INFY");
        first.setDate(date.plusDays(1));
        assertThat(first.getSymbol()).isEqualTo("INFY");
        assertThat(first.getDate()).isEqualTo(date.plusDays(1));
    }
}
