package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkComparisonTest {

    @Test
    void buyAndHoldCalculatesExcessReturnInPercentagePoints() {
        BenchmarkComparison comparison = BenchmarkComparison.buyAndHold(18.5, 12.0);

        assertEquals(BenchmarkComparison.BUY_AND_HOLD, comparison.benchmarkName());
        assertEquals(18.5, comparison.strategyReturnPct());
        assertEquals(12.0, comparison.benchmarkReturnPct());
        assertEquals(6.5, comparison.excessReturnPct());
    }

    @Test
    void blankBenchmarkNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BenchmarkComparison(" ", 1.0, 2.0, -1.0));
    }
}
