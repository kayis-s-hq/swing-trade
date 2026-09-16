package com.swingtrade.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Deterministic fixtures with hand-computed expected values (plan §6.7): every equity point and
 * trade below is chosen so the metric under test can be verified by hand, not just re-derived
 * from the same formula.
 */
@DisplayName("MetricsCalculator")
class MetricsCalculatorTest {

    @Test
    @DisplayName("dailyReturns computes simple period returns")
    void dailyReturnsExact() {
        double[] equity = {100.0, 110.0, 99.0, 99.0};
        double[] returns = MetricsCalculator.dailyReturns(equity);
        assertThat(returns[0]).isEqualTo(0.10, within(1e-12));
        assertThat(returns[1]).isEqualTo(-0.10, within(1e-12));
        assertThat(returns[2]).isEqualTo(0.0, within(1e-12));
    }

    @Test
    @DisplayName("mean and stdDev match hand-computed population statistics")
    void meanAndStdDevExact() {
        double[] values = {1.0, 2.0, 3.0, 4.0};
        // mean = 2.5; population variance = ((1.5^2)+(0.5^2)+(0.5^2)+(1.5^2))/4 = (2.25+0.25+0.25+2.25)/4 = 1.25
        assertThat(MetricsCalculator.mean(values)).isEqualTo(2.5, within(1e-12));
        assertThat(MetricsCalculator.stdDev(values)).isEqualTo(Math.sqrt(1.25), within(1e-12));
    }

    @Test
    @DisplayName("cagrPct matches hand-computed compound annual growth")
    void cagrExact() {
        // Doubling over exactly 2 years (730.5 days) -> CAGR = sqrt(2) - 1 = 41.42...%
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = start.plusDays(731); // 2 * 365.25 rounded
        double cagr = MetricsCalculator.cagrPct(100_000.0, 200_000.0, start, end);
        double years = 731 / 365.25;
        double expected = (Math.pow(2.0, 1.0 / years) - 1.0) * 100.0;
        assertThat(cagr).isEqualTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("wilsonScoreInterval matches the closed-form formula for 8/10 wins")
    void wilsonScoreIntervalExact() {
        double z = 1.959963984540054;
        double[] interval = MetricsCalculator.wilsonScoreInterval(8, 10, z);
        double p = 0.8;
        int n = 10;
        double z2 = z * z;
        double denom = 1 + z2 / n;
        double center = p + z2 / (2 * n);
        double margin = z * Math.sqrt((p * (1 - p) / n) + (z2 / (4.0 * n * n)));
        double expectedLow = (center - margin) / denom;
        double expectedHigh = (center + margin) / denom;
        assertThat(interval[0]).isEqualTo(expectedLow, within(1e-12));
        assertThat(interval[1]).isEqualTo(expectedHigh, within(1e-12));
        assertThat(interval[0]).isLessThan(0.8);
        assertThat(interval[1]).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("wilsonScoreInterval returns [0,1] for zero trades")
    void wilsonScoreIntervalNoTrades() {
        double[] interval = MetricsCalculator.wilsonScoreInterval(0, 0, 1.96);
        assertThat(interval).containsExactly(0.0, 1.0);
    }

    @Test
    @DisplayName("drawdownStats finds the exact max drawdown and its duration in days")
    void drawdownStatsExact() {
        // Peak 100k on day0, trough 80k on day3 (20% drawdown), recovers day5. Second, smaller
        // dip afterwards must not override the first (larger) drawdown.
        List<DailyEquityPoint> curve = List.of(
            point("2024-01-01", 100_000),
            point("2024-01-02", 95_000),
            point("2024-01-03", 85_000),
            point("2024-01-04", 80_000),
            point("2024-01-05", 90_000),
            point("2024-01-06", 101_000),
            point("2024-01-07", 98_000)
        );
        double[] equity = curve.stream().mapToDouble(p -> p.equity().doubleValue()).toArray();
        double[] returns = MetricsCalculator.dailyReturns(equity);
        PortfolioMetrics metrics = MetricsCalculator.compute(curve, List.of(), 6.5);
        assertThat(metrics.maxDrawdownPct()).isEqualTo(20.0, within(1e-9));
        // Duration is measured from the peak (01-01) to the last day still below that peak
        // before a new high is made (01-05, still 10% below peak) - 4 days, not just the day
        // the trough itself occurs (01-04, 3 days).
        assertThat(metrics.maxDrawdownDurationDays()).isEqualTo(4);
        assertThat(returns).hasSize(6);
    }

    @Test
    @DisplayName("sharpeRatio matches hand-computed annualised mean/stdev of excess returns")
    void sharpeRatioExact() {
        double[] returns = {0.01, -0.005, 0.02, 0.0, -0.01};
        double rf = 0.0;
        double mean = MetricsCalculator.mean(returns);
        double sd = MetricsCalculator.stdDev(returns);
        double expected = (mean / sd) * Math.sqrt(252.0);
        assertThat(MetricsCalculator.sharpeRatio(returns, rf)).isEqualTo(expected, within(1e-12));
    }

    @Test
    @DisplayName("compute() derives win rate, payoff ratio, profit factor and R-expectancy exactly from trades")
    void tradeMetricsExact() {
        List<DailyEquityPoint> curve = List.of(
            point("2024-01-01", 100_000),
            point("2024-01-02", 101_000),
            point("2024-01-03", 100_500)
        );
        // Trade 1: win of 1000 on risk-per-share 10 * qty 100 = 1000 risk -> R = 1.0
        PortfolioTrade win = trade("A", 100, 110, 90, 100, ExitReason.TARGET_HIT, "2024-01-01", "2024-01-02");
        // Trade 2: loss of 500 on risk-per-share 10 * qty 100 = 1000 risk -> R = -0.5
        PortfolioTrade loss = trade("B", 100, 95, 90, 100, ExitReason.STOP_LOSS, "2024-01-01", "2024-01-02");
        List<PortfolioTrade> trades = List.of(win, loss);

        PortfolioMetrics metrics = MetricsCalculator.compute(curve, trades, 6.5);

        assertThat(metrics.totalTrades()).isEqualTo(2);
        assertThat(metrics.winRatePct()).isEqualTo(50.0, within(1e-9));
        // avgWinPct = 1000/(100*100)*100 = 10%; avgLossPct = |−500/(100*100)*100| = 5%
        assertThat(metrics.avgWinPct()).isEqualTo(10.0, within(1e-9));
        assertThat(metrics.avgLossPct()).isEqualTo(5.0, within(1e-9));
        assertThat(metrics.payoffRatio()).isEqualTo(2.0, within(1e-9));
        // profitFactor = grossProfit(1000) / grossLoss(500) = 2.0
        assertThat(metrics.profitFactor()).isEqualTo(2.0, within(1e-9));
        // expectancyR = mean(1.0, -0.5) = 0.25
        assertThat(metrics.expectancyR()).isEqualTo(0.25, within(1e-9));
        // expectancyRupees = mean(1000, -500) = 250
        assertThat(metrics.expectancyRupees()).isEqualTo(250.0, within(1e-9));
    }

    @Test
    @DisplayName("bootstrapMeanCi is deterministic for a fixed seed and brackets the sample mean")
    void bootstrapMeanCiDeterministic() {
        double[] samples = {1.0, -0.5, 1.0, -0.5, 1.0, -0.5, 1.0, -0.5};
        double[] ci1 = MetricsCalculator.bootstrapMeanCi(samples, 500, 42L);
        double[] ci2 = MetricsCalculator.bootstrapMeanCi(samples, 500, 42L);
        assertThat(ci1).containsExactly(ci2);
        assertThat(ci1[0]).isLessThanOrEqualTo(ci1[1]);
        double mean = MetricsCalculator.mean(samples);
        assertThat(mean).isBetween(ci1[0] - 1e-9, ci1[1] + 1e-9);
    }

    @Test
    @DisplayName("perSymbolPnlContributionPct flags a >40% single-symbol contribution")
    void perSymbolContributionExact() {
        PortfolioTrade big = trade("BIG", 100, 150, 90, 100, ExitReason.TARGET_HIT, "2024-01-01", "2024-01-02");
        PortfolioTrade small = trade("SMALL", 100, 105, 90, 100, ExitReason.TARGET_HIT, "2024-01-01", "2024-01-02");
        // big pnl = 5000, small pnl = 500 -> total 5500; big share = 5000/5500 = 90.9%
        var contributions = MetricsCalculator.perSymbolPnlContributionPct(List.of(big, small));
        assertThat(contributions.get("BIG")).isGreaterThan(40.0);
    }

    private static DailyEquityPoint point(String date, long equity) {
        return new DailyEquityPoint(LocalDate.parse(date), BigDecimal.valueOf(equity));
    }

    private static PortfolioTrade trade(String symbol, double entry, double exit, double stop, int qty,
                                         ExitReason reason, String entryDate, String exitDate) {
        BigDecimal entryPrice = BigDecimal.valueOf(entry);
        BigDecimal exitPrice = BigDecimal.valueOf(exit);
        BigDecimal stopLoss = BigDecimal.valueOf(stop);
        BigDecimal risk = entryPrice.subtract(stopLoss);
        BigDecimal pnl = exitPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(qty));
        double entryValue = entry * qty;
        double pnlPct = pnl.doubleValue() / entryValue * 100.0;
        return new PortfolioTrade(symbol, LocalDate.parse(entryDate), LocalDate.parse(exitDate), entryPrice,
            exitPrice, stopLoss, entryPrice.add(risk.multiply(BigDecimal.valueOf(2))), qty, reason, risk, pnl,
            pnlPct, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
    }
}
