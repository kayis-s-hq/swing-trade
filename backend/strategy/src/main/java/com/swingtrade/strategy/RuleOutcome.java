package com.swingtrade.strategy;

import java.math.BigDecimal;

/**
 * The result of evaluating one named rule within a {@link StrategyDecision}.
 *
 * <p>Deviation from plan draft §3.1: the plan's sketch of this record omits a {@code mandatory}
 * flag, but §3.2 requires "all rules flagged mandatory=true pass" for a BUY - that flag has to
 * live somewhere, and this record is the natural place, so it is added here as a fifth
 * component.
 *
 * @param key      stable rule identifier (e.g. {@code "trendAligned"})
 * @param passed   whether the rule held for the evaluated bar
 * @param weight   this rule's contribution to {@link StrategyDecision#score()}
 * @param mandatory whether this rule must pass regardless of score for a BUY to fire
 * @param detail   human-readable explanation, e.g. for signal reasoning text
 */
public record RuleOutcome(String key, boolean passed, BigDecimal weight, boolean mandatory, String detail) {
    public RuleOutcome {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Rule key cannot be null or blank");
        }
        if (weight == null || weight.signum() < 0) {
            throw new IllegalArgumentException("Rule weight must be non-negative");
        }
    }
}
