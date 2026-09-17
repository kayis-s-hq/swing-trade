package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WalkForwardRunner")
class WalkForwardRunnerTest {

    private final LegacyPriceActionAdapter strategy = new LegacyPriceActionAdapter(new PriceActionStrategy());
    private final StrategyParamsView params = LegacyPriceActionAdapter.defaultParams();

    @Test
    @DisplayName("rolls train/test/step folds and concatenates their OOS equity curves")
    void rollsFoldsAndConcatenatesEquity() {
        LocalDate start = LocalDate.of(2020, 1, 2);
        List<OhlcvCandle> a = buildZigzag("A", 1100, start);
        List<OhlcvCandle> b = buildZigzag("B", 1100, start);
        Map<String, List<OhlcvCandle>> candles = Map.of("A", a, "B", b);

        LocalDate rangeEnd = a.get(a.size() - 1).date();
        PortfolioBacktestConfig backtestConfig = PortfolioBacktestConfig.defaults(BigDecimal.valueOf(1_000_000));
        // No hold-out here so the test can use the whole synthetic range.
        WalkForwardConfig wfConfig = new WalkForwardConfig(12, 3, 3, 0, false);

        WalkForwardResult result = new WalkForwardRunner()
            .run(candles, strategy, params, backtestConfig, wfConfig, start, rangeEnd, 6.5);

        assertThat(result.folds()).isNotEmpty();
        for (int i = 1; i < result.folds().size(); i++) {
            assertThat(result.folds().get(i).testStart()).isAfter(result.folds().get(i - 1).testStart());
        }
        assertThat(result.concatenatedEquityCurve()).isNotEmpty();
        assertThat(Double.isNaN(result.sharpeStdDev())).isFalse();
    }

    @Test
    @DisplayName("locked hold-out excludes the reserved trailing months from any fold")
    void holdoutLockedByDefault() {
        LocalDate start = LocalDate.of(2020, 1, 2);
        List<OhlcvCandle> a = buildZigzag("A", 1100, start);
        Map<String, List<OhlcvCandle>> candles = Map.of("A", a);
        LocalDate rangeEnd = a.get(a.size() - 1).date();
        PortfolioBacktestConfig backtestConfig = PortfolioBacktestConfig.defaults(BigDecimal.valueOf(1_000_000));
        WalkForwardConfig wfConfig = new WalkForwardConfig(12, 3, 3, 6, false);

        WalkForwardResult result = new WalkForwardRunner()
            .run(candles, strategy, params, backtestConfig, wfConfig, start, rangeEnd, 6.5);

        LocalDate holdoutStart = rangeEnd.minusMonths(6).plusDays(1);
        assertThat(result.holdoutUnlocked()).isFalse();
        for (WalkForwardFold fold : result.folds()) {
            assertThat(fold.testEnd()).isBefore(holdoutStart);
        }
    }

    @Test
    @DisplayName("unlocking the hold-out is reported on the result")
    void holdoutUnlockIsReported() {
        LocalDate start = LocalDate.of(2020, 1, 2);
        List<OhlcvCandle> a = buildZigzag("A", 1100, start);
        Map<String, List<OhlcvCandle>> candles = Map.of("A", a);
        LocalDate rangeEnd = a.get(a.size() - 1).date();
        PortfolioBacktestConfig backtestConfig = PortfolioBacktestConfig.defaults(BigDecimal.valueOf(1_000_000));
        WalkForwardConfig wfConfig = new WalkForwardConfig(12, 3, 3, 6, true);

        WalkForwardResult result = new WalkForwardRunner()
            .run(candles, strategy, params, backtestConfig, wfConfig, start, rangeEnd, 6.5);

        assertThat(result.holdoutUnlocked()).isTrue();
    }

    @Test
    @DisplayName("isUnstable flags a fold whose MaxDD exceeds 2x the median fold MaxDD")
    void flagsUnstableFolds() {
        assertThat(WalkForwardRunner.isUnstable(new double[] {5.0, 5.0, 5.0})).isFalse();
        assertThat(WalkForwardRunner.isUnstable(new double[] {5.0, 5.0, 12.0})).isTrue();
        assertThat(WalkForwardRunner.isUnstable(new double[] {5.0})).isFalse();
    }

    private static List<OhlcvCandle> buildZigzag(String symbol, int bars, LocalDate start) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = 100.0;
        LocalDate date = start;
        for (int i = 0; i < bars; i++) {
            boolean up = i % 3 != 0;
            double changePct = up ? 0.006 : -0.004;
            double open = price;
            price = price * (1 + changePct);
            double close = price;
            double high = Math.max(open, close) * 1.002;
            double low = Math.min(open, close) * 0.998;
            long volume = (i % 20 == 0) ? 3_000_000L : 800_000L;
            if (date.getDayOfWeek().getValue() >= 6) {
                date = date.plusDays(2);
            }
            candles.add(OhlcvCandle.of(symbol, date, bd(open), bd(high), bd(low), bd(close), volume));
            date = date.plusDays(1);
        }
        return candles;
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
