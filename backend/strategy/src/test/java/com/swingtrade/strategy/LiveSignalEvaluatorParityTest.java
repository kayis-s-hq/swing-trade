package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden parity (remediation plan Step 1.4, design principle 6): the live path evaluates a
 * {@link SignalStrategy} at the last bar of the candles it has, the backtest path evaluates the
 * same strategy at bar {@code i} of the full series. For every bar the two must agree exactly.
 */
@DisplayName("Live last-bar evaluation equals backtest evaluation at the same bar")
class LiveSignalEvaluatorParityTest {

    private static final String SYMBOL = "PARITY";

    private final StrategyTypeRegistry typeRegistry = new StrategyTypeRegistry(List.of(
        new LegacyPriceActionAdapter(new PriceActionStrategy()), new PullbackStrategy(), new SqueezeStrategy()));
    private final StrategyResolver resolver = new StrategyResolver(typeRegistry,
        new StrategyRegistry(List.of(new PriceActionStrategy()), new PriceActionStrategy()),
        new ParamSchemaValidator());

    @Test
    void liveDecisionEqualsBacktestDecisionAtEveryBar_forEverySignalStrategyType() {
        List<OhlcvCandle> candles = fixture();
        int buys = 0;
        int evaluatedBars = 0;

        for (SignalStrategy strategy : typeRegistry.all()) {
            var resolved = (ResolvedStrategy.Signal) resolver.resolve(config(strategy.type()));
            MarketContext fullContext = MarketContext.of(SYMBOL, candles);
            int warmup = strategy.warmupBars(resolved.params());

            for (int i = warmup; i < candles.size(); i++) {
                StrategyDecision backtest = strategy.evaluateEntry(fullContext, i, resolved.params());
                Optional<LiveSignalEvaluator.EntryEvaluation> live =
                    LiveSignalEvaluator.evaluateEntry(SYMBOL, candles.subList(0, i + 1), resolved);

                assertThat(live).as("%s bar %d", strategy.type(), i).isPresent();
                StrategyDecision liveDecision = live.get().decision();
                assertThat(liveDecision.type()).as("%s bar %d type", strategy.type(), i).isEqualTo(backtest.type());
                assertThat(liveDecision.score()).as("%s bar %d score", strategy.type(), i)
                    .isEqualByComparingTo(backtest.score());
                assertThat(liveDecision.reasoning()).as("%s bar %d reasoning", strategy.type(), i)
                    .isEqualTo(backtest.reasoning());
                assertThat(live.get().date()).isEqualTo(candles.get(i).date());
                evaluatedBars++;
                if (backtest.type() == SignalType.BUY) buys++;
            }
        }

        assertThat(evaluatedBars).isPositive();
        assertThat(buys).as("fixture must exercise at least one BUY across the strategy types").isPositive();
    }

    @Test
    void liveExitDecisionEqualsBacktestExitDecisionAtEveryBar() {
        List<OhlcvCandle> candles = fixture();
        var resolved = (ResolvedStrategy.Signal) resolver.resolve(config("PULLBACK"));
        SignalStrategy strategy = resolved.strategy();
        MarketContext fullContext = MarketContext.of(SYMBOL, candles);
        int entryIndex = 120;
        OhlcvCandle entryBar = candles.get(entryIndex);
        BigDecimal entry = entryBar.close();

        int exits = 0;
        for (int i = entryIndex + 1; i < candles.size(); i++) {
            BigDecimal hwm = candles.subList(entryIndex, i + 1).stream()
                .map(OhlcvCandle::close).reduce(entry, BigDecimal::max);
            OpenPosition position = new OpenPosition(entryIndex, entryBar.date(), entry,
                entry.multiply(new BigDecimal("0.80")), entry.multiply(new BigDecimal("1.40")), 10, hwm);
            ExitDecision backtest = strategy.evaluateExit(fullContext, i, position, resolved.params());
            Optional<ExitDecision> live = LiveSignalEvaluator.evaluateExit(SYMBOL, candles.subList(0, i + 1),
                resolved, entryBar.date(), entry, position.stopLoss(), position.target(), 10);

            assertThat(live).as("bar %d", i).isPresent();
            assertThat(live.get().exit()).as("bar %d exit", i).isEqualTo(backtest.exit());
            assertThat(live.get().reason()).as("bar %d reason", i).isEqualTo(backtest.reason());
            if (backtest.exit()) {
                exits++;
                break;
            }
        }
        assertThat(exits).as("fixture must eventually trigger an exit").isPositive();
    }

    @Test
    void insufficientHistoryYieldsNoEvaluation() {
        var resolved = (ResolvedStrategy.Signal) resolver.resolve(config("BREAKOUT"));

        assertThat(LiveSignalEvaluator.evaluateEntry(SYMBOL, fixture().subList(0, 30), resolved)).isEmpty();
    }

    private static StrategyConfig config(String type) {
        return StrategyConfig.create(type.toLowerCase() + "-v1", 1, type, Map.of(), Map.of(),
            StrategyConfig.Mode.SHADOW, BigDecimal.valueOf(100000), true, null, LocalDateTime.now());
    }

    /** Deterministic trending series with two superimposed cycles and a volume pulse. */
    private static List<OhlcvCandle> fixture() {
        List<OhlcvCandle> candles = new ArrayList<>();
        LocalDate date = LocalDate.of(2023, 1, 1);
        double previousClose = 100;
        for (int i = 0; i < 420; i++) {
            double close = 100 + 0.12 * i + 6 * Math.sin(i / 9.0) + 2.5 * Math.sin(i / 3.3);
            double open = previousClose;
            double high = Math.max(open, close) * 1.004;
            double low = Math.min(open, close) * 0.996;
            long volume = 1_000_000L + (long) (600_000 * Math.max(0, Math.sin(i / 5.0)));
            candles.add(OhlcvCandle.of(SYMBOL, date, BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume));
            previousClose = close;
            date = date.plusDays(1);
        }
        return candles;
    }
}
