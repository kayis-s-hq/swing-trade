package com.swingtrade.api.service;

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

    private static com.swingtrade.domain.ShadowClosedTrade trade(String pnl, java.time.LocalDate exit) {
        return new com.swingtrade.domain.ShadowClosedTrade("v", "SBIN", exit.minusDays(3), exit,
            new BigDecimal("100"), null, null, null, 10, "TARGET_HIT", new BigDecimal(pnl));
    }

    @Test
    void evidenceNeedsMinimumTradesAndIgnoresFutureExits() {
        var asOf = java.time.LocalDate.of(2026, 9, 10);
        var enough = java.util.stream.IntStream.range(0, 5)
            .mapToObj(i -> trade("50", java.time.LocalDate.of(2026, 9, 1).plusDays(i))).toList();
        assertThat(SignalArbiter.evidence(enough, asOf)).hasValue(5.0);
        assertThat(SignalArbiter.evidence(enough.subList(0, 4), asOf)).isEmpty();
        var withFuture = new java.util.ArrayList<>(enough.subList(0, 4));
        withFuture.add(trade("500", java.time.LocalDate.of(2026, 9, 20)));
        assertThat(SignalArbiter.evidence(withFuture, asOf)).isEmpty();
    }

    @Test
    void evidenceRankedPrefersProvenVariantOverHigherConfidence() {
        var candidates = List.of(
            new SignalArbiter.Candidate("flashy", new BigDecimal("0.90")),
            new SignalArbiter.Candidate("proven", new BigDecimal("0.60")));
        var evidence = java.util.Map.of("proven", 3.0, "flashy", -2.0);
        assertThat(SignalArbiter.pickVariant(candidates, ArbitrationRule.HIGHEST_CONFIDENCE, evidence))
            .hasValue("flashy");
        assertThat(SignalArbiter.pickVariant(candidates, ArbitrationRule.EVIDENCE_RANKED, evidence))
            .hasValue("proven");
    }

    @Test
    void evidenceRankedTreatsUnknownAsNeutralAndFallsBackToConfidence() {
        var candidates = List.of(
            new SignalArbiter.Candidate("a", new BigDecimal("0.55")),
            new SignalArbiter.Candidate("b", new BigDecimal("0.80")));
        assertThat(SignalArbiter.pickVariant(candidates, ArbitrationRule.EVIDENCE_RANKED, java.util.Map.of()))
            .hasValue("b");
        assertThat(SignalArbiter.pickVariant(candidates, ArbitrationRule.EVIDENCE_RANKED,
            java.util.Map.of("a", 1.0))).hasValue("a");
    }
}
