package com.swingtrade.strategy;

import com.swingtrade.domain.MarketRegime;
import com.swingtrade.domain.MarketRegimeAssessment;
import com.swingtrade.domain.RelativeStrengthAssessment;
import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeStrengthMomentumStrategyTest {

    private static final MarketRegimeAssessment BULLISH =
        new MarketRegimeAssessment(MarketRegime.BULLISH, true, LocalDate.of(2026, 9, 16),
            "INDEX_ABOVE_200_DAY_AVERAGE");

    private static Indicators indicators(String price, String ema20, String ema50,
                                         String rsi, String volume, String volumeMa,
                                         String weeklyHigh) {
        return new Indicators(new BigDecimal(price), new BigDecimal(ema20), new BigDecimal(ema50),
            new BigDecimal(rsi), new BigDecimal(volume), new BigDecimal(volumeMa),
            new BigDecimal(weeklyHigh));
    }

    private static RelativeStrengthAssessment rs(double excessReturnPct) {
        return new RelativeStrengthAssessment(excessReturnPct > 0, 10.0, 10.0 - excessReturnPct,
            excessReturnPct, LocalDate.of(2026, 9, 16), "TEST");
    }

    @Test
    void nameIsStable() {
        assertThat(new RelativeStrengthMomentumStrategy().name()).isEqualTo("RS_NIFTY_MOMENTUM");
    }

    @Test
    void requiresCloseAboveEma50AsMandatoryTrendFilter() {
        var strategy = new RelativeStrengthMomentumStrategy();

        assertThat(strategy.trendAligned(indicators("110", "105", "100", "55", "100", "100", "120"))).isTrue();
        assertThat(strategy.trendAligned(indicators("95", "105", "100", "55", "100", "100", "120"))).isFalse();
        assertThat(strategy.isEntrySignal(indicators("95", "105", "100", "55", "100", "100", "120"))).isFalse();
    }

    @Test
    void entryEligibleRequiresRelativeStrengthAboveThresholdAndTrendAndRegime() {
        var strategy = new RelativeStrengthMomentumStrategy();
        Indicators aboveEma50 = indicators("110", "105", "100", "55", "100", "100", "120");

        // Trend passes, RS positive, bullish regime -> eligible.
        assertThat(strategy.isEntryEligible(aboveEma50, BULLISH, rs(5.0))).isTrue();

        // RS exactly at the default threshold (0) still qualifies (threshold is inclusive);
        // below it does not.
        assertThat(strategy.isEntryEligible(aboveEma50, BULLISH, rs(0.0))).isTrue();
        assertThat(strategy.isEntryEligible(aboveEma50, BULLISH, rs(-1.0))).isFalse();

        // Missing RS data fails closed.
        assertThat(strategy.isEntryEligible(aboveEma50, BULLISH, null)).isFalse();

        // Trend filter still mandatory even with strong RS.
        Indicators belowEma50 = indicators("95", "105", "100", "55", "100", "100", "120");
        assertThat(strategy.isEntryEligible(belowEma50, BULLISH, rs(10.0))).isFalse();

        // Regime gate is on for this strategy: no assessment fails closed.
        assertThat(strategy.isEntryEligible(aboveEma50, null, rs(10.0))).isFalse();
    }

    @Test
    void configurableRsThresholdRaisesTheOutperformanceBar() {
        var strategy = new RelativeStrengthMomentumStrategy(new BigDecimal("5"), false,
            RelativeStrengthMomentumStrategy.DEFAULT_HIGH_PROXIMITY_PCT);
        Indicators aboveEma50 = indicators("110", "105", "100", "55", "100", "100", "120");

        assertThat(strategy.isEntryEligible(aboveEma50, BULLISH, rs(4.9))).isFalse();
        assertThat(strategy.isEntryEligible(aboveEma50, BULLISH, rs(5.0))).isTrue();
    }

    @Test
    void optionalHighProximityCheckWhenEnabled() {
        var strategy = new RelativeStrengthMomentumStrategy(BigDecimal.ZERO, true, new BigDecimal("0.90"));

        // price 108 is 90% of weeklyHigh 120 -> passes proximity.
        Indicators nearHigh = indicators("108", "105", "100", "55", "100", "100", "120");
        assertThat(strategy.isEntrySignal(nearHigh)).isTrue();

        // price 100 is below the 90% proximity bar -> fails even though trend passes.
        Indicators farFromHigh = indicators("100.5", "95", "95", "55", "100", "100", "120");
        assertThat(strategy.isEntrySignal(farFromHigh)).isFalse();
    }

    @Test
    void fromParametersReadsConfiguredThresholdAndProximity() {
        var strategy = RelativeStrengthMomentumStrategy.fromParameters(
            Map.of("rsThresholdPct", "8", "requireHighProximity", "true", "highProximityPct", "0.85"));

        Indicators i = indicators("109", "105", "100", "55", "100", "100", "120");
        assertThat(strategy.isEntryEligible(i, BULLISH, rs(9.0))).isTrue();
        assertThat(strategy.isEntryEligible(i, BULLISH, rs(7.0))).isFalse();
    }

    @Test
    void registryFindsAndResolvesThisStrategy() {
        var priceAction = new PriceActionStrategy();
        var registry = new StrategyRegistry(java.util.List.of(priceAction,
            new RelativeStrengthMomentumStrategy()), priceAction);

        assertThat(registry.find(RelativeStrengthMomentumStrategy.NAME)).isPresent();

        var config = StrategyConfig.create("RS_A", 1, RelativeStrengthMomentumStrategy.NAME,
            Map.of("rsThresholdPct", "6"), Map.of(),
            StrategyConfig.Mode.SHADOW, new BigDecimal("100000"), true, null, LocalDateTime.now());

        var resolved = registry.resolve(config);
        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow()).isInstanceOf(RelativeStrengthMomentumStrategy.class);
    }
}
