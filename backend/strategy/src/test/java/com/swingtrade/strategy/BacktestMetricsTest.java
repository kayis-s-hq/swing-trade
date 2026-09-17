package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;

import com.swingtrade.domain.BenchmarkComparison;
import java.math.BigDecimal;
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

    @Test
    void buyAndHoldUsesPriceReturnAndReportsExcessReturn() {
        BenchmarkComparison comparison = BacktestMetrics.buyAndHoldComparison(
                18.0, BigDecimal.valueOf(100), BigDecimal.valueOf(125));

        assertEquals(25.0, comparison.benchmarkReturnPct(), 1e-9);
        assertEquals(-7.0, comparison.excessReturnPct(), 1e-9);
    }

    @Test
    void buyAndHoldIsUnavailableForInvalidPrices() {
        BenchmarkComparison comparison = BacktestMetrics.buyAndHoldComparison(
                18.0, BigDecimal.ZERO, BigDecimal.valueOf(125));

        assertEquals(BenchmarkComparison.BUY_AND_HOLD, comparison.benchmarkName());
        assertEquals(0.0, comparison.benchmarkReturnPct());
        assertEquals(0.0, comparison.excessReturnPct());
    }
}
