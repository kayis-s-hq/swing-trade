package com.swingtrade.strategy;

import java.math.BigDecimal;

/**
 * The outcome of one overlay gate ({@code regimeGate}/{@code sentimentGate}) applied to a
 * {@link StrategyDecision} - plan §5.5. Recorded for every BUY evaluation, including blocked
 * ones, so "what gate prevented" is measurable; shaped to be serialised directly into the
 * {@code signals.gate_outcomes} JSONB column added by {@code V47__strategy_provenance.sql}
 * (wiring that column live is orchestrator work for a later phase - see {@link GateEvaluator}).
 *
 * @param gate    the gate's name, e.g. {@code "regimeGate"} or {@code "sentimentGate"}
 * @param blocked true if this gate blocked the BUY
 * @param reason  human-readable explanation
 * @param score   the underlying score/metric the gate evaluated, if any (e.g. sentiment
 *                confidence), for analytics; may be {@code null}
 */
public record GateOutcome(String gate, boolean blocked, String reason, BigDecimal score) {
    public GateOutcome {
        if (gate == null || gate.isBlank()) {
            throw new IllegalArgumentException("gate cannot be null or blank");
        }
    }

    public static GateOutcome pass(String gate, String reason) {
        return new GateOutcome(gate, false, reason, null);
    }

    public static GateOutcome block(String gate, String reason) {
        return new GateOutcome(gate, true, reason, null);
    }
}
