package com.swingtrade.api.service;

import com.swingtrade.api.service.SignalPipeline.VariantSignalOutcome;
import com.swingtrade.domain.Signal.SignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignalArbiterTest {

    private static VariantSignalOutcome outcome(String id, SignalType type, String confidence, boolean persisted) {
        return new VariantSignalOutcome(id, 1, type, false, persisted, new BigDecimal(confidence));
    }

    @Test
    void picksHighestConfidenceBuy() {
        var winner = SignalArbiter.pick(List.of(
            outcome("breakout-v1", SignalType.BUY, "0.60", true),
            outcome("squeeze-v1", SignalType.BUY, "0.85", true),
            outcome("pullback-v1", SignalType.BUY, "0.75", true)));
        assertThat(winner).get().extracting(VariantSignalOutcome::variantId).isEqualTo("squeeze-v1");
    }

    @Test
    void ignoresNonBuyAndUnpersistedOutcomes() {
        var winner = SignalArbiter.pick(List.of(
            outcome("a", SignalType.SELL, "0.99", true),
            outcome("b", SignalType.HOLD, "0.95", true),
            outcome("c", SignalType.BUY, "0.90", false),
            outcome("d", SignalType.BUY, "0.40", true)));
        assertThat(winner).get().extracting(VariantSignalOutcome::variantId).isEqualTo("d");
    }

    @Test
    void tieBreaksDeterministicallyOnVariantId() {
        var winner = SignalArbiter.pick(List.of(
            outcome("zeta", SignalType.BUY, "0.70", true),
            outcome("alpha", SignalType.BUY, "0.70", true)));
        assertThat(winner).get().extracting(VariantSignalOutcome::variantId).isEqualTo("alpha");
    }

    @Test
    void noEligibleBuyMeansNoWinner() {
        assertThat(SignalArbiter.pick(List.of(outcome("a", SignalType.HOLD, "0.5", true)))).isEmpty();
        assertThat(SignalArbiter.pick(List.of())).isEmpty();
    }
}
