package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktestMetricsTest {

    @Test
    void cagrAnnualizesReturnUsingCalendarDates() {
        double cagr = BacktestMetrics.cagrPct(100.0, 121.0,
                LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1));

        assertEquals(10.0, cagr, 0.02);
    }

    @Test
    void sortinoUsesNegativeReturnsForDownsideDeviation() {
        double expected = ((0.10 - (5.0 / 110.0) + (10.0 / 105.0)) / 3.0)
                / Math.sqrt(Math.pow(5.0 / 110.0, 2) / 3.0) * Math.sqrt(252.0);

        assertEquals(expected, BacktestMetrics.sortinoRatio(List.of(100.0, 110.0, 105.0, 115.0)), 1e-9);
    }

    @Test
    void sortinoWithNoDownsideReturnsZero() {
        assertEquals(0.0, BacktestMetrics.sortinoRatio(List.of(100.0, 101.0, 102.0)));
    }

    @Test
    void calmarDividesCagrByDrawdown() {
        assertEquals(0.5, BacktestMetrics.calmarRatio(10.0, 20.0));
        assertEquals(0.0, BacktestMetrics.calmarRatio(10.0, 0.0));
    }
}
