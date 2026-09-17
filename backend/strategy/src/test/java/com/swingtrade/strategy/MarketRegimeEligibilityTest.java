package com.swingtrade.strategy;

import com.swingtrade.domain.MarketRegime;
import com.swingtrade.domain.MarketRegimeAssessment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarketRegimeEligibilityTest {

    private static final Indicators ENTRY = new Indicators(
        new BigDecimal("110"), new BigDecimal("105"), new BigDecimal("100"),
        new BigDecimal("55"), new BigDecimal("200"), new BigDecimal("100"),
        new BigDecimal("110"));
    private static final Indicators PULLBACK_ENTRY = new Indicators(
        new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("95"),
        new BigDecimal("50"), new BigDecimal("110"), new BigDecimal("100"),
        new BigDecimal("105"));

    @Test
    void defaultStrategyDoesNotChangeWhenRegimeAssessmentIsAbsent() {
        assertThat(new PriceActionStrategy().isEntryEligible(ENTRY, null)).isTrue();
    }

    @Test
    void configuredStrategyFailsClosedWithoutAnEligibleAssessment() {
        TradingStrategy strategy = new PullbackInUptrendStrategy();

        assertThat(strategy.regimeFilterEnabled()).isTrue();
        assertThat(strategy.isEntryEligible(PULLBACK_ENTRY, null)).isFalse();
        assertThat(strategy.isEntryEligible(PULLBACK_ENTRY,
            MarketRegimeAssessment.unavailable("INDEX_DATA_UNAVAILABLE"))).isFalse();
    }

    @Test
    void configuredStrategyAcceptsAnEligibleAllowedRegime() {
        TradingStrategy strategy = new PullbackInUptrendStrategy();
        var assessment = new MarketRegimeAssessment(
            MarketRegime.BULLISH, true, LocalDate.of(2026, 9, 16), "INDEX_ABOVE_200_DAY_AVERAGE");

        assertThat(strategy.isEntryEligible(PULLBACK_ENTRY, assessment)).isTrue();
    }
}
