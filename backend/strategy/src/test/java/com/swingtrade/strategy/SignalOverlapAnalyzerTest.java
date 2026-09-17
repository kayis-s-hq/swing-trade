package com.swingtrade.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@DisplayName("SignalOverlapAnalyzer")
class SignalOverlapAnalyzerTest {

    @Test
    @DisplayName("identical entry sets have Jaccard similarity 1.0")
    void identicalEntriesFullOverlap() {
        List<PortfolioTrade> a = List.of(trade("A", LocalDate.of(2024, 1, 2)), trade("B", LocalDate.of(2024, 1, 5)));
        List<PortfolioTrade> b = List.of(trade("A", LocalDate.of(2024, 1, 2)), trade("B", LocalDate.of(2024, 1, 5)));
        assertThat(SignalOverlapAnalyzer.entryJaccardSimilarity(a, b)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("disjoint entry sets have Jaccard similarity 0.0")
    void disjointEntriesNoOverlap() {
        List<PortfolioTrade> a = List.of(trade("A", LocalDate.of(2024, 1, 2)));
        List<PortfolioTrade> b = List.of(trade("B", LocalDate.of(2024, 1, 5)));
        assertThat(SignalOverlapAnalyzer.entryJaccardSimilarity(a, b)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("partial overlap computes intersection/union exactly")
    void partialOverlap() {
        List<PortfolioTrade> a = List.of(
            trade("A", LocalDate.of(2024, 1, 2)),
            trade("B", LocalDate.of(2024, 1, 5)));
        List<PortfolioTrade> b = List.of(
            trade("A", LocalDate.of(2024, 1, 2)),
            trade("C", LocalDate.of(2024, 1, 9)));
        // intersection={A/2024-01-02}, union={A/01-02,B/01-05,C/01-09} -> 1/3
        assertThat(SignalOverlapAnalyzer.entryJaccardSimilarity(a, b)).isCloseTo(1.0 / 3.0, offset(1e-9));
    }

    @Test
    @DisplayName("perfectly correlated equity curves have Pearson correlation 1.0")
    void perfectlyCorrelatedReturns() {
        List<DailyEquityPoint> a = equityCurve(new double[] {100, 101, 103, 102, 105});
        List<DailyEquityPoint> b = equityCurve(new double[] {200, 202, 206, 204, 210});
        assertThat(SignalOverlapAnalyzer.dailyReturnCorrelation(a, b)).isCloseTo(1.0, offset(1e-6));
    }

    @Test
    @DisplayName("inversely correlated equity curves have Pearson correlation -1.0")
    void inverselyCorrelatedReturns() {
        List<DailyEquityPoint> a = equityCurve(new double[] {100, 101, 103, 102, 105});
        List<DailyEquityPoint> b = equityCurve(new double[] {100, 99, 97, 98, 95});
        assertThat(SignalOverlapAnalyzer.dailyReturnCorrelation(a, b)).isCloseTo(-1.0, offset(1e-3));
    }

    private static List<DailyEquityPoint> equityCurve(double[] equity) {
        List<DailyEquityPoint> curve = new java.util.ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (double v : equity) {
            curve.add(new DailyEquityPoint(date, BigDecimal.valueOf(v)));
            date = date.plusDays(1);
        }
        return curve;
    }

    private static PortfolioTrade trade(String symbol, LocalDate entryDate) {
        return new PortfolioTrade(symbol, entryDate, entryDate.plusDays(3), BigDecimal.TEN, BigDecimal.valueOf(11),
            BigDecimal.valueOf(9), BigDecimal.valueOf(12), 10, ExitReason.TARGET_HIT, BigDecimal.ONE,
            BigDecimal.TEN, 10.0, 3, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
    }
}
