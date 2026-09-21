package com.swingtrade.api.service;

import com.swingtrade.domain.Signal;

import java.math.BigDecimal;

/**
 * Outcome of evaluating one configured strategy variant for one symbol: it was
 * {@link Kind#EVALUATED} (with a persisted {@code signal}, or {@code null} when the strategy
 * decided not to enter), {@link Kind#SKIPPED} with a reason (unresolvable type, insufficient
 * history) or {@link Kind#ERROR}. Lets the orchestrator report every variant's fate instead of
 * silently dropping skipped ones.
 *
 * @param signal the persisted signal, or {@code null} when nothing was persisted
 * @param score  the strategy's entry score in [0,1] when evaluated, else {@code null}
 * @param detail human-readable reasoning or skip/error reason
 */
public record ConfiguredEvaluation(Kind kind, Signal signal, BigDecimal score, String detail) {

    public enum Kind { EVALUATED, SKIPPED, ERROR }

    public static ConfiguredEvaluation evaluated(Signal signal, BigDecimal score, String detail) {
        return new ConfiguredEvaluation(Kind.EVALUATED, signal, score, detail);
    }

    public static ConfiguredEvaluation skipped(String reason) {
        return new ConfiguredEvaluation(Kind.SKIPPED, null, null, reason);
    }

    public static ConfiguredEvaluation error(String message) {
        return new ConfiguredEvaluation(Kind.ERROR, null, null, message);
    }
}
