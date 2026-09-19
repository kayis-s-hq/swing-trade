package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqueezeStrategyTest {

    private static final String SYMBOL = "SQTEST";
    private final SqueezeStrategy strategy = new SqueezeStrategy();

    private Map<String, Object> baseParams() {
        Map<String, Object> values = new HashMap<>();
        values.put("bbPeriod", 20);
        values.put("trendEma", 50);
        values.put("squeezeDefinition", "BB_PCTILE");
        values.put("squeezeLookback", 60);
        values.put("squeezePctile", BigDecimal.valueOf(20));
        values.put("squeezeRecency", 5);
        values.put("volumeMaPeriod", 20);
        values.put("volMult", BigDecimal.valueOf(1.5));
        values.put("entryScoreThreshold", BigDecimal.valueOf(0.75));
        values.put("atrPeriod", 14);
        values.put("atrStopMult", BigDecimal.valueOf(2));
        values.put("rewardRisk", BigDecimal.valueOf(2));
        values.put("maxHoldDays", 20);
        values.put("trailAtrMult", BigDecimal.ZERO);
        return values;
    }

    /** Uptrend, then a long flat/low-volatility consolidation (the squeeze), then a breakout bar
     * with a volume surge. */
    private List<OhlcvCandle> buildFixture() {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = 100.0;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 70; i++) {
            double close = price * 1.003;
            candles.add(bar(date, price, close, 1.001, 0.999, 1_000_000L));
            price = close;
            date = date.plusDays(1);
        }
        // Tight consolidation: near-zero daily range/change for many bars -> BB width shrinks.
        for (int i = 0; i < 40; i++) {
            double close = price * (1 + (i % 2 == 0 ? 0.0005 : -0.0005));
            candles.add(bar(date, price, close, 1.0005, 0.9995, 400_000L));
            price = close;
            date = date.plusDays(1);
        }
        // Breakout bar: big up move with a volume surge.
        double breakoutClose = price * 1.06;
        candles.add(bar(date, price, breakoutClose, 1.001, 0.999, 5_000_000L));
        return candles;
    }

    private OhlcvCandle bar(LocalDate date, double open, double close, double highMult, double lowMult, long volume) {
        double high = Math.max(open, close) * highMult;
        double low = Math.min(open, close) * lowMult;
        return OhlcvCandle.of(SYMBOL, date, BigDecimal.valueOf(open), BigDecimal.valueOf(high),
            BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume);
    }

    @Test
    void squeezedRule_passesAfterConsolidation() {
        List<OhlcvCandle> candles = buildFixture();
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        int lastBar = candles.size() - 1;
        StrategyDecision decision = strategy.evaluateEntry(ctx, lastBar, StrategyParamsView.of(baseParams()));
        assertThat(ruleOutcome(decision, "squeezed").passed()).isTrue();
    }

    @Test
    void expansionAndVolumeSurge_fireOnBreakoutBar() {
        List<OhlcvCandle> candles = buildFixture();
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        int lastBar = candles.size() - 1;
        StrategyDecision decision = strategy.evaluateEntry(ctx, lastBar, StrategyParamsView.of(baseParams()));
        assertThat(ruleOutcome(decision, "expansion").passed()).isTrue();
        assertThat(ruleOutcome(decision, "volumeSurge").passed()).isTrue();
        assertThat(ruleOutcome(decision, "trendAligned").passed()).isTrue();
    }

    @Test
    void bbInKcDefinition_alsoDetectsSqueeze() {
        List<OhlcvCandle> candles = buildFixture();
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        int lastBar = candles.size() - 1;
        Map<String, Object> params = baseParams();
        params.put("squeezeDefinition", "BB_IN_KC");
        StrategyDecision decision = strategy.evaluateEntry(ctx, lastBar, StrategyParamsView.of(params));
        assertThat(ruleOutcome(decision, "squeezed").passed()).isTrue();
    }

    @Test
    void noSqueeze_whenVolatilityNeverContracts() {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = 100.0;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 130; i++) {
            double changePct = (i % 2 == 0) ? 0.02 : -0.015;
            double close = price * (1 + changePct);
            candles.add(bar(date, price, close, 1.01, 0.99, 1_000_000L));
            price = close;
            date = date.plusDays(1);
        }
        MarketContext ctx = MarketContext.of(SYMBOL, candles);
        int lastBar = candles.size() - 1;
        StrategyDecision decision = strategy.evaluateEntry(ctx, lastBar, StrategyParamsView.of(baseParams()));
        assertThat(ruleOutcome(decision, "squeezed").passed()).isFalse();
    }

    private RuleOutcome ruleOutcome(StrategyDecision decision, String key) {
        return decision.rules().stream().filter(r -> r.key().equals(key)).findFirst()
            .orElseThrow(() -> new AssertionError("No rule outcome for " + key));
    }
}
