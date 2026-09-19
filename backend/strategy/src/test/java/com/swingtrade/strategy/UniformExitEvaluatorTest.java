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

@DisplayName("UniformExitEvaluator")
class UniformExitEvaluatorTest {

    private static final String SYMBOL = "TESTCO";

    private StrategyParamsView params(BigDecimal trailAtrMult, int maxHoldDays) {
        Map<String, Object> values = new HashMap<>();
        values.put("trailAtrMult", trailAtrMult);
        values.put("atrPeriod", 14);
        values.put("maxHoldDays", maxHoldDays);
        return StrategyParamsView.of(values);
    }

    /** Flat-priced warm-up candles, then one final bar built to the given OHLC. */
    private MarketContext contextWithFinalBar(double open, double high, double low, double close) {
        List<OhlcvCandle> candles = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 30; i++) {
            candles.add(OhlcvCandle.of(SYMBOL, date, BigDecimal.valueOf(100), BigDecimal.valueOf(101),
                BigDecimal.valueOf(99), BigDecimal.valueOf(100), 1_000_000L));
            date = date.plusDays(1);
        }
        candles.add(OhlcvCandle.of(SYMBOL, date, BigDecimal.valueOf(open), BigDecimal.valueOf(high),
            BigDecimal.valueOf(low), BigDecimal.valueOf(close), 1_000_000L));
        return MarketContext.of(SYMBOL, candles);
    }

    private OpenPosition positionAt(int entryIndex, BigDecimal entryPrice, BigDecimal stop, BigDecimal target) {
        return OpenPosition.open(entryIndex, LocalDate.of(2024, 1, 1), entryPrice, stop, target, 10);
    }

    @Test
    void stopLossBreached_takesPrecedenceOverEverythingElse() {
        // Low breaches stop AND high reaches target on the same bar -> STOP_LOSS wins.
        MarketContext ctx = contextWithFinalBar(100, 130, 80, 100);
        int lastBar = ctx.barCount() - 1;
        OpenPosition position = positionAt(0, BigDecimal.valueOf(100), BigDecimal.valueOf(90), BigDecimal.valueOf(120));

        ExitDecision decision = UniformExitEvaluator.evaluate(
            ctx.view(lastBar), position, params(BigDecimal.ZERO, 100), true);

        assertThat(decision.exit()).isTrue();
        assertThat(decision.reason()).isEqualTo(ExitReason.STOP_LOSS);
    }

    @Test
    void targetHit_whenStopNotBreached() {
        MarketContext ctx = contextWithFinalBar(100, 130, 95, 100);
        int lastBar = ctx.barCount() - 1;
        OpenPosition position = positionAt(0, BigDecimal.valueOf(100), BigDecimal.valueOf(90), BigDecimal.valueOf(120));

        ExitDecision decision = UniformExitEvaluator.evaluate(
            ctx.view(lastBar), position, params(BigDecimal.ZERO, 100), true);

        assertThat(decision.exit()).isTrue();
        assertThat(decision.reason()).isEqualTo(ExitReason.TARGET_HIT);
    }

    @Test
    void signalExit_whenNeitherStopNorTargetHit() {
        MarketContext ctx = contextWithFinalBar(100, 105, 95, 100);
        int lastBar = ctx.barCount() - 1;
        OpenPosition position = positionAt(0, BigDecimal.valueOf(100), BigDecimal.valueOf(90), BigDecimal.valueOf(120));

        ExitDecision decision = UniformExitEvaluator.evaluate(
            ctx.view(lastBar), position, params(BigDecimal.ZERO, 100), true);

        assertThat(decision.exit()).isTrue();
        assertThat(decision.reason()).isEqualTo(ExitReason.SIGNAL_EXIT);
    }

    @Test
    void trailingStop_firesWhenEnabledAndCloseBreachesIt() {
        // ATR14 on a flat 100/101/99/100 series is small; a close that has dropped from a
        // higher high-water mark by more than a tiny ATR-multiple should breach the trail.
        MarketContext ctx = contextWithFinalBar(95, 96, 94, 95);
        int lastBar = ctx.barCount() - 1;
        OpenPosition position = positionAt(0, BigDecimal.valueOf(90), BigDecimal.valueOf(70), BigDecimal.valueOf(200))
            .advanceHighWaterMark(BigDecimal.valueOf(100));

        ExitDecision decision = UniformExitEvaluator.evaluate(
            ctx.view(lastBar), position, params(BigDecimal.valueOf(0.5), 100), false);

        assertThat(decision.exit()).isTrue();
        assertThat(decision.reason()).isEqualTo(ExitReason.TRAILING_STOP);
    }

    @Test
    void trailingStop_disabledWhenMultiplierIsZero() {
        MarketContext ctx = contextWithFinalBar(95, 96, 94, 95);
        int lastBar = ctx.barCount() - 1;
        OpenPosition position = positionAt(0, BigDecimal.valueOf(90), BigDecimal.valueOf(70), BigDecimal.valueOf(200))
            .advanceHighWaterMark(BigDecimal.valueOf(100));

        ExitDecision decision = UniformExitEvaluator.evaluate(
            ctx.view(lastBar), position, params(BigDecimal.ZERO, 100), false);

        assertThat(decision.exit()).isFalse();
    }

    @Test
    void timeStop_firesWhenMaxHoldDaysReached() {
        MarketContext ctx = contextWithFinalBar(100, 101, 99, 100);
        int lastBar = ctx.barCount() - 1;
        OpenPosition position = positionAt(0, BigDecimal.valueOf(100), BigDecimal.valueOf(70), BigDecimal.valueOf(200));

        ExitDecision decision = UniformExitEvaluator.evaluate(
            ctx.view(lastBar), position, params(BigDecimal.ZERO, lastBar), false);

        assertThat(decision.exit()).isTrue();
        assertThat(decision.reason()).isEqualTo(ExitReason.TIME_STOP);
    }

    @Test
    void hold_whenNoExitConditionMet() {
        MarketContext ctx = contextWithFinalBar(100, 101, 99, 100);
        int lastBar = ctx.barCount() - 1;
        OpenPosition position = positionAt(0, BigDecimal.valueOf(100), BigDecimal.valueOf(70), BigDecimal.valueOf(200));

        ExitDecision decision = UniformExitEvaluator.evaluate(
            ctx.view(lastBar), position, params(BigDecimal.ZERO, 1000), false);

        assertThat(decision.exit()).isFalse();
        assertThat(decision.reason()).isNull();
    }
}
