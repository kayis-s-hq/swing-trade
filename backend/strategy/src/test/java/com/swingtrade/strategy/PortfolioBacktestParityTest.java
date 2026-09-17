package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan §6.7 parity test: {@link LegacyPriceActionAdapter#evaluateEntry} called directly against
 * the same market data on date D must produce the same decision as
 * {@link PortfolioBacktestEngine}'s decision at bar D for that date. The engine never
 * re-implements entry/exit logic - it calls {@link SignalStrategy#evaluateEntry}/{@code
 * evaluateExit} through the exact same {@link MarketContext.View}-bounded SPI a direct caller
 * would use - so this test proves that wiring end-to-end for one concrete fixture rather than by
 * code inspection alone.
 *
 * <p>This is a direct-call comparison, not an end-to-end job test: the live SIGNAL-stage
 * orchestrator wiring that would run {@link LegacyPriceActionAdapter} against production data is
 * Phase 5 scope (plan §7), not yet built. A true orchestrator-level parity check (live signal on
 * date D vs. backtest decision at bar D) is deferred to that phase.
 */
@DisplayName("Portfolio backtest engine <-> direct SignalStrategy call parity")
class PortfolioBacktestParityTest {

    private static final String SYMBOL = "SYM";

    private final LegacyPriceActionAdapter strategy = new LegacyPriceActionAdapter(new PriceActionStrategy());
    private final StrategyParamsView params = LegacyPriceActionAdapter.defaultParams();

    @Test
    @DisplayName("the engine's fill matches a direct evaluateEntry() BUY at the decision bar, with the same stop")
    void engineEntryMatchesDirectEvaluateEntry() {
        // A 2-up/1-down zigzag uptrend (RSI stays mid-band, price above both EMAs) followed by a
        // single volume+proximity "bump" bar - the only bar meeting all 4 entry rules - then one
        // more bar so the engine has an open bar to fill the entry on (plan §6.1: fill on open
        // T+1). Deterministic (not randomised), same technique as BacktestEngineTest's
        // buildEntrySetupCandles, so exactly one BUY decision is guaranteed.
        List<OhlcvCandle> candles = buildEntrySetupCandles();
        Map<String, List<OhlcvCandle>> candlesBySymbol = Map.of(SYMBOL, candles);

        PortfolioBacktestConfig config = PortfolioBacktestConfig.defaults(BigDecimal.valueOf(1_000_000));
        LocalDate windowStart = candles.get(0).date().plusDays(200);
        LocalDate end = candles.get(candles.size() - 1).date();

        PortfolioBacktestResult result = new PortfolioBacktestEngine()
            .run(candlesBySymbol, strategy, params, config, windowStart, end, 6.5);

        assertThat(result.trades()).hasSize(1);
        PortfolioTrade trade = result.trades().get(0);

        // Independently rebuild the same MarketContext and re-evaluate entry directly at the bar
        // immediately preceding the fill date (signal on close T, fill on open T+1 - see
        // PortfolioBacktestEngine's class doc) - fully independent of the engine's internals.
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        Map<LocalDate, Integer> indexByDate = new HashMap<>();
        for (int i = 0; i < candles.size(); i++) {
            indexByDate.put(candles.get(i).date(), i);
        }
        int fillIndex = indexByDate.get(trade.entryDate());
        int decisionIndex = fillIndex - 1;

        StrategyDecision direct = strategy.evaluateEntry(ctx, decisionIndex, params);

        assertThat(direct.type()).isEqualTo(com.swingtrade.domain.Signal.SignalType.BUY);
        assertThat(direct.suggestedStop()).isEqualByComparingTo(trade.stopLoss());
    }

    private List<OhlcvCandle> buildEntrySetupCandles() {
        List<OhlcvCandle> candles = buildZigzagUptrendCandles(299, 100.0, 0.5, 0.75, 1_000_000L);
        OhlcvCandle lastZigzag = candles.get(candles.size() - 1);
        OhlcvCandle bump = buildFinalCandle(lastZigzag, 1.005, 2_000_000L);
        candles.add(bump);

        BigDecimal bumpClose = bump.close();
        appendCandle(candles, bumpClose, bumpClose.multiply(BigDecimal.valueOf(1.001)),
            bumpClose.multiply(BigDecimal.valueOf(0.999)), bumpClose, 1_000_000L);
        return candles;
    }

    private List<OhlcvCandle> buildZigzagUptrendCandles(int count, double startPrice, double upPercent,
                                                          double downPercent, long volume) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2023, 1, 2);
        for (int i = 0; i < count; i++) {
            double changePercent = (i % 3 == 2) ? -downPercent : upPercent;
            double open = price;
            double close = price * (1 + changePercent / 100.0);
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;
            candles.add(OhlcvCandle.of(SYMBOL, date, bd(open), bd(high), bd(low), bd(close), volume));
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    private OhlcvCandle buildFinalCandle(OhlcvCandle previous, double closeMultiplier, long volume) {
        BigDecimal previousClose = previous.close();
        BigDecimal open = previousClose;
        BigDecimal close = previousClose.multiply(BigDecimal.valueOf(closeMultiplier));
        BigDecimal high = close.multiply(BigDecimal.valueOf(1.001));
        BigDecimal low = open.multiply(BigDecimal.valueOf(0.999));
        return OhlcvCandle.of(SYMBOL, previous.date().plusDays(1), open, high, low, close, volume);
    }

    private void appendCandle(List<OhlcvCandle> candles, BigDecimal open, BigDecimal high, BigDecimal low,
                               BigDecimal close, long volume) {
        OhlcvCandle previous = candles.get(candles.size() - 1);
        candles.add(OhlcvCandle.of(SYMBOL, previous.date().plusDays(1), open, high, low, close, volume));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
