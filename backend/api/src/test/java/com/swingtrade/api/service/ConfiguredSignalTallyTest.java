package com.swingtrade.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfiguredSignalTally")
class ConfiguredSignalTallyTest {

    @Test
    void allEvaluatedIsNotDegraded() {
        var tally = new ConfiguredSignalTally();
        tally.evaluated("breakout-v1", 1, true, new BigDecimal("0.8"));
        tally.evaluated("pullback-v1", 2, false, new BigDecimal("0.1"));

        assertThat(tally.degraded()).isFalse();
        assertThat(tally.summary()).isEqualTo("2 evaluated, 1 signal(s), 0 skipped, 0 error(s)");
        assertThat(tally.evaluatedVariantIds()).containsExactly("breakout-v1", "pullback-v1");
    }

    @Test
    void skippedVariantDegradesAndIsListedInDetails() {
        var tally = new ConfiguredSignalTally();
        tally.evaluated("breakout-v1", 1, false, BigDecimal.ZERO);
        tally.skipped("bogus-v1", 4, "unsupported strategy type BOGUS");

        assertThat(tally.degraded()).isTrue();
        assertThat(tally.skippedVariantIds()).containsExactly("bogus-v1");
        assertThat(tally.summary()).contains("1 skipped").contains("bogus-v1 skipped: unsupported strategy type BOGUS");
        assertThat(tally.reasonCode()).isEqualTo("STRATEGY_SKIPPED");
        assertThat(tally.detailsJson())
            .contains("\"variantId\":\"bogus-v1\"", "\"outcome\":\"SKIPPED\"", "\"version\":4")
            .contains("\"reason\":\"unsupported strategy type BOGUS\"")
            .contains("\"outcome\":\"EVALUATED\"");
    }

    @Test
    void errorDegradesWithErrorReasonCode() {
        var tally = new ConfiguredSignalTally();
        tally.error("x-v1", 1, "boom");

        assertThat(tally.degraded()).isTrue();
        assertThat(tally.reasonCode()).isEqualTo("STRATEGY_ERROR");
    }

    @Test
    void skipReasonsAreClassifiedIntoLowCardinalityCodes() {
        assertThat(ConfiguredSignalTally.skipReasonCode("unsupported strategy type PULLBACK")).isEqualTo("UNSUPPORTED_TYPE");
        assertThat(ConfiguredSignalTally.skipReasonCode("insufficient candle history: 3 candles")).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(ConfiguredSignalTally.skipReasonCode("invalid parameters for BREAKOUT: x")).isEqualTo("INVALID_PARAMS");
        assertThat(ConfiguredSignalTally.skipReasonCode("something else")).isEqualTo("OTHER");
    }
}
