package com.swingtrade.strategy;

import java.util.Map;
import java.util.function.Predicate;

/**
 * A validation rule spanning more than one param, e.g. {@code emaFast < emaSlow} (plan §4.3).
 *
 * @param message human-readable error returned when {@code rule} fails
 * @param rule    predicate over the resolved (defaults-filled) params map; must not throw for
 *                any value that already passed each param's own type/min/max check
 */
public record CrossFieldRule(String message, Predicate<Map<String, Object>> rule) {
    public CrossFieldRule {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be null or blank");
        }
        if (rule == null) {
            throw new IllegalArgumentException("rule cannot be null");
        }
    }

    /** True if params satisfy this rule. */
    public boolean test(Map<String, Object> params) {
        return rule.test(params);
    }
}
