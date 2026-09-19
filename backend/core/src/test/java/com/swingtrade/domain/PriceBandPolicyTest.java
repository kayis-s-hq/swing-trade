package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PriceBandPolicyTest {
    private static final PriceBand BAND = new PriceBand("TCS", LocalDate.of(2026, 1, 2),
        bd("90"), bd("110"));

    @Test
    void upperBandBlocksLongEntryAtOrAboveLimit() {
        assertThat(PriceBandPolicy.blocksLongEntry(BAND, bd("110"))).isTrue();
        assertThat(PriceBandPolicy.blocksLongEntry(BAND, bd("109.99"))).isFalse();
    }

    @Test
    void lowerBandBlocksLongExitOnlyWhenCandleClosesAtOrBelowLimit() {
        assertThat(PriceBandPolicy.blocksLongExit(BAND, candle(bd("90")))).isTrue();
        assertThat(PriceBandPolicy.blocksLongExit(BAND, candle(bd("90.01")))).isFalse();
    }

    @Test
    void missingBandDoesNotBlockExecution() {
        assertThat(PriceBandPolicy.blocksLongEntry(null, bd("110"))).isFalse();
        assertThat(PriceBandPolicy.blocksLongExit(null, candle(bd("1")))).isFalse();
    }

    @Test
    void hasNoShortSideCounterpartByDesign() {
        // The platform has no short-selling execution path (see class Javadoc and
        // docs/plans/2026-09-16-configurable-multi-strategy.md assumption A2: long-only,
        // no short signals). This asserts that decision stays visible and intentional:
        // a mirror-image blocksShortEntry/blocksShortExit must not silently reappear
        // without someone also re-reading this test and the class Javadoc.
        java.util.Set<String> methodNames = java.util.Arrays.stream(PriceBandPolicy.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(methodNames).contains("blocksLongEntry", "blocksLongExit");
        assertThat(methodNames).noneMatch(name -> name.toLowerCase().contains("short"));
    }

    private static OhlcvCandle candle(BigDecimal close) {
        return OhlcvCandle.of("TCS", BAND.date(), close, close, close, close, 1_000L);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
