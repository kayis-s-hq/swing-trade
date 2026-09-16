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

/**
 * Plan §6.7: no-look-ahead property test at the portfolio-engine level, a basic end-to-end run,
 * and a data-quality-gate exclusion check. The parity requirement ("live SIGNAL-stage decision ==
 * LegacyPriceActionAdapter.evaluateEntry called directly == backtest decision at bar D") is
 * covered directly by {@code LegacyPriceActionAdapterGoldenParityTest} plus the fact that this
 * engine calls {@code strategy.evaluateEntry}/{@code evaluateExit} through the exact same
 * {@link SignalStrategy} SPI with no separate re-implementation of entry/exit rules; there is no
 * end-to-end orchestrator in this phase to run the live SIGNAL stage against, so full
 * orchestrator-level parity is deferred to Phase 5 per the plan.
 */
@DisplayName("PortfolioBacktestEngine")
class PortfolioBacktestEngineTest {

    private final LegacyPriceActionAdapter strategy =
        new LegacyPriceActionAdapter(new PriceActionStrategy());
    private final StrategyParamsView params = LegacyPriceActionAdapter.defaultParams();

    @Test
    @DisplayName("truncating future candles never changes a past entry/exit decision (no look-ahead)")
    void noLookAheadProperty() {
        List<OhlcvCandle> full = buildZigzagWithBump("SYM", 400, LocalDate.of(2023, 1, 2));

        MarketContext fullCtx = MarketContext.of("SYM", full);
        int probeBar = 350;

        StrategyDecision decisionFromFull = strategy.evaluateEntry(fullCtx, probeBar, params);

        // Truncate everything after the probe bar - the decision at probeBar must be identical,
        // proving MarketContext.view()'s no-look-ahead guard actually holds end-to-end through a
        // real SignalStrategy evaluation, not just at the accessor level (which
        // MarketContextTest already covers).
        List<OhlcvCandle> truncated = full.subList(0, probeBar + 1);
        MarketContext truncatedCtx = MarketContext.of("SYM", truncated);
        StrategyDecision decisionFromTruncated = strategy.evaluateEntry(truncatedCtx, probeBar, params);

        assertThat(decisionFromTruncated.type()).isEqualTo(decisionFromFull.type());
        assertThat(decisionFromTruncated.score()).isEqualByComparingTo(decisionFromFull.score());
        assertThat(decisionFromTruncated.suggestedStop()).isEqualTo(decisionFromFull.suggestedStop());
        assertThat(decisionFromTruncated.suggestedTarget()).isEqualTo(decisionFromFull.suggestedTarget());
    }

    @Test
    @DisplayName("full portfolio run produces a daily equity curve covering the whole window and consistent trades")
    void basicPortfolioRun() {
        LocalDate windowStart = LocalDate.of(2023, 1, 2).plusDays(120);
        List<OhlcvCandle> symbolA = buildZigzagWithBump("A", 400, LocalDate.of(2023, 1, 2));
        List<OhlcvCandle> symbolB = buildZigzagWithBump("B", 400, LocalDate.of(2023, 1, 2));
        Map<String, List<OhlcvCandle>> candlesBySymbol = Map.of("A", symbolA, "B", symbolB);

        PortfolioBacktestConfig config = PortfolioBacktestConfig.defaults(BigDecimal.valueOf(1_000_000));
        LocalDate end = symbolA.get(symbolA.size() - 1).date();

        PortfolioBacktestResult result = new PortfolioBacktestEngine()
            .run(candlesBySymbol, strategy, params, config, windowStart, end, 6.5);

        assertThat(result.equityCurve()).isNotEmpty();
        assertThat(result.equityCurve().get(0).date()).isEqualTo(windowStart);
        assertThat(result.excludedSymbols()).isEmpty();
        // Every trade's exit date must be on/after its entry date and quantities must be positive.
        for (PortfolioTrade trade : result.trades()) {
            assertThat(trade.exitDate()).isAfterOrEqualTo(trade.entryDate());
            assertThat(trade.quantity()).isPositive();
        }
        // Metrics must be computable without throwing and produce a finite Sharpe.
        assertThat(result.metrics()).isNotNull();
        assertThat(Double.isNaN(result.metrics().sharpeRatio())).isFalse();
    }

    @Test
    @DisplayName("a symbol failing the data-quality gate is excluded and logged with a reason, not silently dropped")
    void excludesBadQualitySymbol() {
        LocalDate start = LocalDate.of(2023, 1, 2);
        List<OhlcvCandle> good = buildZigzagWithBump("GOOD", 400, start);
        List<OhlcvCandle> bad = new ArrayList<>(buildZigzagWithBump("BAD", 400, start));
        OhlcvCandle victim = bad.get(200);
        bad.set(200, new OhlcvCandle(victim.symbol(), victim.date(), victim.open(), victim.high(), victim.low(),
            BigDecimal.ZERO, victim.volume(), victim.adjClose()));

        Map<String, List<OhlcvCandle>> candlesBySymbol = Map.of("GOOD", good, "BAD", bad);
        PortfolioBacktestConfig config = PortfolioBacktestConfig.defaults(BigDecimal.valueOf(1_000_000));
        LocalDate windowStart = start.plusDays(120);
        LocalDate end = good.get(good.size() - 1).date();

        PortfolioBacktestResult result = new PortfolioBacktestEngine()
            .run(candlesBySymbol, strategy, params, config, windowStart, end, 6.5);

        assertThat(result.excludedSymbols()).hasSize(1);
        assertThat(result.excludedSymbols().get(0).symbol()).isEqualTo("BAD");
        assertThat(result.excludedSymbols().get(0).reason()).contains("Zero or negative price");
        assertThat(result.trades()).allMatch(t -> t.symbol().equals("GOOD"));
    }

    /**
     * A gentle zigzag uptrend (keeps RSI mid-band, price above both EMAs) with periodic volume
     * bumps near the rolling high, so the fixture produces at least one clean entry setup
     * without needing to reproduce the golden-parity fixture's exact tuning.
     */
    private static List<OhlcvCandle> buildZigzagWithBump(String symbol, int bars, LocalDate start) {
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
