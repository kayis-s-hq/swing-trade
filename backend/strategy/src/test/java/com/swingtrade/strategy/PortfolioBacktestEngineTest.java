package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        assertThat(result.finalCapital()).isEqualTo(110.0);
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
        assertThat(result.finalCapital()).isEqualTo(110.0);
        assertThat(result.equityCurve()).extracting(PortfolioEquityPoint::date)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2),
                        LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4),
                        LocalDate.of(2024, 1, 5));
        PortfolioEquityPoint exitDay = result.equityCurve().get(3);
        assertThat(exitDay.settledCash()).isEqualTo(0.0);
        assertThat(exitDay.unsettledProceeds()).isEqualTo(110.0);
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
        assertThat(result.equityCurve().get(1).unsettledProceeds()).isEqualTo(110.0);
        assertThat(result.equityCurve().get(2).settledCash()).isEqualTo(0.0);
        assertThat(result.equityCurve().get(4).settledCash()).isEqualTo(110.0);
    }

    private static BacktestConfig config(double capital) {
        return new BacktestConfig(0, 0, .01, capital, 2, 2, 2, 20, false, 21);
    }

    private static BacktestResult result(String symbol, BacktestTrade trade) {
        return new BacktestResult(symbol, 1, trade.pnl() > 0 ? 1 : 0, trade.pnl() <= 0 ? 1 : 0,
                trade.pnl() > 0 ? 100 : 0, 0, 0, 0, 0, 0, 0, List.of(trade));
    }

    private static BacktestTrade trade(String symbol, LocalDate entryDate, LocalDate exitDate,
                                       double entryPrice, int quantity, double pnl) {
        BigDecimal entry = BigDecimal.valueOf(entryPrice);
        return new BacktestTrade(symbol, entryDate, exitDate, entry, entry,
                entry.subtract(BigDecimal.ONE), entry.add(BigDecimal.ONE), quantity,
                ExitReason.TIME_STOP, pnl, pnl, 1);
    }
}
