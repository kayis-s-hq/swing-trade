package com.swingtrade.strategy;

import java.math.BigDecimal;

/**
 * The outcome of {@link SignalStrategy#evaluateExit} for a single bar: either "no exit" or an
 * exit at a given price with a reason drawn from the existing {@link ExitReason} enum, following
 * the precedence documented in {@link UniformExitEvaluator} (plan §3.3).
 */
public record ExitDecision(boolean exit, ExitReason reason, BigDecimal exitPrice, String detail) {
    public ExitDecision {
        if (exit && (reason == null || exitPrice == null)) {
            throw new IllegalArgumentException("An exiting decision must carry a reason and exitPrice");
        }
        if (!exit && (reason != null || exitPrice != null)) {
            throw new IllegalArgumentException("A non-exiting decision must not carry a reason or exitPrice");
        }
    }

    public static ExitDecision hold() {
        return new ExitDecision(false, null, null, "No exit condition met");
    }

    public static ExitDecision exit(ExitReason reason, BigDecimal exitPrice, String detail) {
        return new ExitDecision(true, reason, exitPrice, detail);
    }
}
