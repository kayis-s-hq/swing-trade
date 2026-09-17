package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal.SignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PullbackStrategyTest {

    private static final String SYMBOL = "PBTEST";
    private final PullbackStrategy strategy = new PullbackStrategy();

    private StrategyParamsView defaultParams() {
        Map<String, Object> values = new HashMap<>();
        values.put("trendEma", 50);
        values.put("pullbackEma", 20);
        values.put("touchPct", BigDecimal.valueOf(0.02));
        values.put("touchLookback", 8);
        values.put("rsiPeriod", 14);
        values.put("rsiDip", BigDecimal.valueOf(40));
        values.put("rsiTrigger", BigDecimal.valueOf(45));
        values.put("slopeLookback", 10);
        values.put("requireConfirmation", false);
        values.put("entryScoreThreshold", BigDecimal.valueOf(0.5));
        values.put("atrPeriod", 14);
        values.put("atrStopMult", BigDecimal.valueOf(2));
        values.put("rewardRisk", BigDecimal.valueOf(2));
        values.put("maxHoldDays", 20);
        values.put("trailAtrMult", BigDecimal.ZERO);
        return StrategyParamsView.of(values);
    }

    /** Steady uptrend (0.4%/day) long enough to warm up EMA50 with a positive slope, then a
     * multi-day dip back toward EMA20 (driving RSI down), then a sharp bounce. */
    private List<OhlcvCandle> buildFixture() {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = 100.0;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 90; i++) {
            double close = price * 1.004;
            candles.add(bar(date, price, close));
            price = close;
            date = date.plusDays(1);
        }
        // Dip: several down days to push RSI down and bring price near EMA20.
        for (int i = 0; i < 6; i++) {
            double close = price * 0.985;
            candles.add(bar(date, price, close));
            price = close;
            date = date.plusDays(1);
        }
        // Bounce day.
        double bounceClose = price * 1.02;
        candles.add(bar(date, price, bounceClose));
        return candles;
    }

    private OhlcvCandle bar(LocalDate date, double open, double close) {
        double high = Math.max(open, close) * 1.002;
        double low = Math.min(open, close) * 0.998;
        return OhlcvCandle.of(SYMBOL, date, BigDecimal.valueOf(open), BigDecimal.valueOf(high),
            BigDecimal.valueOf(low), BigDecimal.valueOf(close), 1_000_000L);
    }

    @Test
    void trendUpRule_passesInSteadyUptrend() {
        List<OhlcvCandle> candles = buildFixture();
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        // Bar 60: well into the uptrend, before the dip - trendUp must hold.
        StrategyDecision decision = strategy.evaluateEntry(ctx, 60, defaultParams());
        RuleOutcome trendUp = ruleOutcome(decision, "trendUp");
        assertThat(trendUp.passed()).isTrue();
    }

    @Test
    void touchedPullbackEmaAndRsiRecovered_fireOnBounceBar() {
        List<OhlcvCandle> candles = buildFixture();
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        int lastBar = candles.size() - 1;
        StrategyDecision decision = strategy.evaluateEntry(ctx, lastBar, defaultParams());

        RuleOutcome touched = ruleOutcome(decision, "touchedPullbackEma");
        RuleOutcome rsiRecovered = ruleOutcome(decision, "rsiRecovered");
        // The multi-day dip followed by a sharp bounce must trigger at least one of the two
        // non-mandatory pullback rules within the touchLookback window ending at the bounce bar.
        assertThat(touched.passed() || rsiRecovered.passed()).isTrue();
    }

    @Test
    void trendUpRule_failsInDowntrend() {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = 100.0;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 70; i++) {
            double close = price * 0.996;
            candles.add(bar(date, price, close));
            price = close;
            date = date.plusDays(1);
        }
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        StrategyDecision decision = strategy.evaluateEntry(ctx, candles.size() - 1, defaultParams());
        assertThat(decision.type()).isEqualTo(SignalType.HOLD);
        assertThat(ruleOutcome(decision, "trendUp").passed()).isFalse();
    }

    @Test
    void suggestedStop_isMinOfSwingLowAndAtrStop() {
        List<OhlcvCandle> candles = buildFixture();
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        int lastBar = candles.size() - 1;
        Map<String, Object> values = new HashMap<>();
        values.put("trendEma", 50);
        values.put("pullbackEma", 20);
        values.put("touchPct", BigDecimal.valueOf(0.02));
        values.put("touchLookback", 8);
        values.put("rsiPeriod", 14);
        values.put("rsiDip", BigDecimal.valueOf(40));
        values.put("rsiTrigger", BigDecimal.valueOf(45));
        values.put("slopeLookback", 10);
        values.put("requireConfirmation", false);
        values.put("entryScoreThreshold", BigDecimal.ZERO);
        values.put("atrPeriod", 14);
        values.put("atrStopMult", BigDecimal.valueOf(2));
        values.put("rewardRisk", BigDecimal.valueOf(2));
        values.put("maxHoldDays", 20);
        values.put("trailAtrMult", BigDecimal.ZERO);
        StrategyParamsView lenientParams = StrategyParamsView.of(values);

        StrategyDecision decision = strategy.evaluateEntry(ctx, lastBar, lenientParams);
        if (decision.type() == SignalType.BUY) {
            MarketContext.View view = ctx.view(lastBar);
            BigDecimal swingLow = view.lowestLow(8);
            BigDecimal atrStop = view.close().subtract(view.atr(14).multiply(BigDecimal.valueOf(2)));
            assertThat(decision.suggestedStop()).isEqualTo(swingLow.min(atrStop));
        }
    }

    private RuleOutcome ruleOutcome(StrategyDecision decision, String key) {
        return decision.rules().stream().filter(r -> r.key().equals(key)).findFirst()
            .orElseThrow(() -> new AssertionError("No rule outcome for " + key));
    }
}
