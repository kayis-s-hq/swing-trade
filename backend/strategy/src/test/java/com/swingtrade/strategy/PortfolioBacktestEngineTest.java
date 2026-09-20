package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.BenchmarkCandleSeries;
import com.swingtrade.domain.BenchmarkComparison;
import com.swingtrade.domain.CorrelationExposureLimit;
import com.swingtrade.domain.PortfolioExposurePolicy;
import com.swingtrade.domain.SectorExposureLimit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioBacktestEngineTest {

    private final PortfolioBacktestEngine engine = new PortfolioBacktestEngine();

    @Test
    void simulateSharesCapitalAndRejectsAnUnaffordableOverlappingEntry() {
        BacktestTrade held = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 5), 60, 1, 10);
        BacktestTrade rejected = trade("BBB", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 6), 60, 1, 10);

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", held), result("BBB", rejected)),
                config(100), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 6));

        assertThat(result.totalTrades()).isEqualTo(1);
        assertThat(result.rejectedTrades()).isEqualTo(1);
        assertThat(result.trades()).extracting(BacktestTrade::symbol).containsExactly("AAA");
        assertThat(result.finalCapital()).isEqualByComparingTo("110.0");
        assertThat(result.equityCurve()).extracting(PortfolioEquityPoint::date)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2),
                        LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4),
                        LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 6));
    }

    @Test
    void sameDayExitDoesNotMakeProceedsAvailableBeforeT1Settlement() {
        BacktestTrade first = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4), 100, 1, 10);
        BacktestTrade second = trade("BBB", LocalDate.of(2024, 1, 4), LocalDate.of(2024, 1, 5), 100, 1, 10);

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", first), result("BBB", second)),
                config(100), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5));

        assertThat(result.totalTrades()).isEqualTo(1);
        assertThat(result.rejectedTrades()).isEqualTo(1);
        assertThat(result.finalCapital()).isEqualByComparingTo("110.0");
        assertThat(result.equityCurve()).extracting(PortfolioEquityPoint::date)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2),
                        LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4),
                        LocalDate.of(2024, 1, 5));
        PortfolioEquityPoint exitDay = result.equityCurve().get(3);
        assertThat(exitDay.settledCash()).isEqualByComparingTo("0.0");
        assertThat(exitDay.unsettledProceeds()).isEqualByComparingTo("110.0");
    }

    @Test
    void invalidPortfolioInputsAreRejected() {
        assertThatThrownBy(() -> engine.simulate(List.of(), config(100),
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void FridayExitSettlesOnMondayAndDailyCurveExposesCashTransition() {
        BacktestTrade trade = trade("AAA", LocalDate.of(2024, 1, 4), LocalDate.of(2024, 1, 5),
                100, 1, 10);

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", trade)), config(100),
                LocalDate.of(2024, 1, 4), LocalDate.of(2024, 1, 8));

        assertThat(result.equityCurve()).hasSize(5);
        assertThat(result.equityCurve().get(1).unsettledProceeds()).isEqualByComparingTo("110.0");
        assertThat(result.equityCurve().get(2).settledCash()).isEqualByComparingTo("0.0");
        assertThat(result.equityCurve().get(4).settledCash()).isEqualByComparingTo("110.0");
    }

    @Test
    void marksOpenPositionAtEachTradingCandleCloseAndSettlesOnNextTradingCandle() {
        BacktestTrade held = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4),
                100, 1, 10);
        Map<String, List<OhlcvCandle>> candles = Map.of("AAA", List.of(
                candle("AAA", LocalDate.of(2024, 1, 2), 100),
                candle("AAA", LocalDate.of(2024, 1, 3), 120),
                candle("AAA", LocalDate.of(2024, 1, 4), 110),
                candle("AAA", LocalDate.of(2024, 1, 5), 90)));

        PortfolioBacktestResult result = engine.simulate(List.of(result("AAA", held)), config(100),
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 5), candles);

        assertThat(result.equityCurve()).extracting(PortfolioEquityPoint::date)
                .containsExactly(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3),
                        LocalDate.of(2024, 1, 4), LocalDate.of(2024, 1, 5));
        assertThat(result.equityCurve().get(1).positionMarketValue()).isEqualByComparingTo("120.0");
        assertThat(result.equityCurve().get(1).equity()).isEqualByComparingTo("120.0");
        assertThat(result.equityCurve().get(2).unsettledProceeds()).isEqualByComparingTo("110.0");
        assertThat(result.equityCurve().get(3).settledCash()).isEqualByComparingTo("110.0");
    }

    @Test
    void reportsPersistedNiftyPriceBenchmarkAndExcessReturn() {
        BacktestTrade held = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4), 100, 1, 10);
        Map<String, List<OhlcvCandle>> candles = Map.of("AAA", List.of(
                candle("AAA", LocalDate.of(2024, 1, 2), 100),
                candle("AAA", LocalDate.of(2024, 1, 3), 110),
                candle("AAA", LocalDate.of(2024, 1, 4), 110)));
        BenchmarkCandleSeries nifty = new BenchmarkCandleSeries("NIFTY50",
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4), List.of(
                candle("NIFTY50", LocalDate.of(2024, 1, 2), 100),
                candle("NIFTY50", LocalDate.of(2024, 1, 4), 105)));

        PortfolioBacktestResult result = engine.simulate(List.of(result("AAA", held)), config(100),
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4), candles, Map.of(), Optional.of(nifty));

        assertThat(result.benchmarkComparison().benchmarkName()).isEqualTo(BenchmarkComparison.NIFTY50_PRICE);
        assertThat(result.benchmarkComparison().benchmarkReturnPct()).isEqualTo(5.0);
        assertThat(result.benchmarkComparison().excessReturnPct()).isEqualTo(5.0);
    }

    @Test
    void appliesBreakevenAndTrailingStopBeforeScheduledExit() {
        BacktestTrade candidate = new BacktestTrade("AAA", LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 5), BigDecimal.valueOf(100), BigDecimal.valueOf(120),
                BigDecimal.valueOf(90), BigDecimal.valueOf(125), 1, ExitReason.TIME_STOP, BigDecimal.valueOf(20), BigDecimal.valueOf(20), 3);
        Map<String, List<OhlcvCandle>> candles = Map.of("AAA", List.of(
                candle("AAA", LocalDate.of(2024, 1, 2), 100),
                candle("AAA", LocalDate.of(2024, 1, 3), 110),
                candle("AAA", LocalDate.of(2024, 1, 4), 100, 100),
                candle("AAA", LocalDate.of(2024, 1, 5), 120)));

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", candidate)),
                new BacktestConfig(0, 0, .01, 100, 2, 2, 2, 20, false, 21,
                        new TrailingBreakevenPolicy(1.0, .05)),
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 5), candles);

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.exitDate()).isEqualTo(LocalDate.of(2024, 1, 4));
            assertThat(trade.exitReason()).isEqualTo(ExitReason.TRAILING_STOP);
            assertThat(trade.exitPrice()).isEqualByComparingTo("104.5");
        });
        assertThat(result.finalCapital()).isEqualByComparingTo("104.5");
    }

    @Test
    void rejectsEntryWhenSectorPositionLimitIsReachedAndRecordsReason() {
        BacktestTrade first = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 5), 40, 1, 0);
        BacktestTrade second = trade("BBB", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 5), 40, 1, 0);
        BacktestConfig config = new BacktestConfig(0, 0, .01, 100, 5, 2, 2, 20, false, 21,
                com.swingtrade.domain.RiskManagementPolicy.none(), PortfolioExposurePolicy.limits(
                        new SectorExposureLimit(1, 1.0), new CorrelationExposureLimit(1.0, 2)));

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", first), result("BBB", second)), config,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5), Map.of(),
                Map.of("AAA", "IT", "BBB", "IT"));

        assertThat(result.totalTrades()).isEqualTo(1);
        assertThat(result.rejectionReasons()).containsExactly("SECTOR_POSITION_LIMIT");
    }

    @Test
    void rejectsEntryWhenCorrelationExceedsLimitWithStableReason() {
        BacktestTrade first = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 5), 40, 1, 0);
        BacktestTrade second = trade("BBB", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 5), 40, 1, 0);
        List<OhlcvCandle> prices = List.of(
                candle("AAA", LocalDate.of(2024, 1, 1), 100), candle("AAA", LocalDate.of(2024, 1, 2), 101),
                candle("AAA", LocalDate.of(2024, 1, 3), 102), candle("AAA", LocalDate.of(2024, 1, 4), 103),
                candle("AAA", LocalDate.of(2024, 1, 5), 104));
        List<OhlcvCandle> matchingPrices = prices.stream()
                .map(candle -> candle("BBB", candle.date(), candle.close().doubleValue())).toList();
        BacktestConfig config = new BacktestConfig(0, 0, .01, 100, 5, 2, 2, 20, false, 21,
                com.swingtrade.domain.RiskManagementPolicy.none(), PortfolioExposurePolicy.limits(
                        new SectorExposureLimit(5, 1.0), new CorrelationExposureLimit(.7, 4)));

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", first), result("BBB", second)), config,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5),
                Map.of("AAA", prices, "BBB", matchingPrices), Map.of("AAA", "IT", "BBB", "FINANCE"));

        assertThat(result.rejectionReasons()).containsExactly("CORRELATION_LIMIT");
    }

    @Test
    void attributesResultToTheSuppliedStrategyVariantId() {
        BacktestTrade held = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4), 100, 1, 10);

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", held)), config(100),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 4),
                Map.of(), Map.of(), Optional.empty(), "rsi2-mean-reversion-v3");

        assertThat(result.strategyVariantId()).isEqualTo("rsi2-mean-reversion-v3");
    }

    @Test
    void strategyVariantIdDefaultsToNullWhenNotSupplied() {
        BacktestTrade held = trade("AAA", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4), 100, 1, 10);

        PortfolioBacktestResult result = engine.simulate(
                List.of(result("AAA", held)), config(100),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 4));

        assertThat(result.strategyVariantId()).isNull();
    }

    private static BacktestConfig config(double capital) {
        return new BacktestConfig(0, 0, .01, capital, 2, 2, 2, 20, false, 21);
    }

    private static BacktestResult result(String symbol, BacktestTrade trade) {
        return new BacktestResult(symbol, 1, trade.pnl().signum() > 0 ? 1 : 0, trade.pnl().signum() <= 0 ? 1 : 0,
                trade.pnl().signum() > 0 ? 100 : 0, 0, 0, 0, 0, 0, 0, List.of(trade));
    }

    private static BacktestTrade trade(String symbol, LocalDate entryDate, LocalDate exitDate,
                                       double entryPrice, int quantity, double pnl) {
        BigDecimal entry = BigDecimal.valueOf(entryPrice);
        return new BacktestTrade(symbol, entryDate, exitDate, entry, entry,
                entry.subtract(BigDecimal.ONE), entry.add(BigDecimal.ONE), quantity,
                ExitReason.TIME_STOP, BigDecimal.valueOf(pnl), BigDecimal.valueOf(pnl), 1);
    }

    private static OhlcvCandle candle(String symbol, LocalDate date, double close) {
        return candle(symbol, date, close, close);
    }

    private static OhlcvCandle candle(String symbol, LocalDate date, double close, double low) {
        BigDecimal price = BigDecimal.valueOf(close);
        return OhlcvCandle.of(symbol, date, price, BigDecimal.valueOf(Math.max(close, low)),
                BigDecimal.valueOf(Math.min(close, low)), price, 1L);
    }
}
