package com.swingtrade.strategy;

import java.math.BigDecimal;

/**
 * Describes a single configurable parameter of a {@link SignalStrategy}: its type, allowed
 * range, default value and grouping/description for UI form generation (dashboard/config work
 * lands in a later phase; this record is the contract those consumers will read).
 *
 * @param name         the parameter key, as read via {@link StrategyParamsView}
 * @param type         the parameter's primitive type
 * @param min          inclusive lower bound (numeric types only; {@code null} if not applicable)
 * @param max          inclusive upper bound (numeric types only; {@code null} if not applicable)
 * @param defaultValue the default value used when a config omits this key
 * @param description  human-readable explanation shown in the UI
 * @param group        UI grouping label (e.g. "trend", "exits")
 */
public record ParamDef(
    String name,
    ParamType type,
    BigDecimal min,
    BigDecimal max,
    Object defaultValue,
    String description,
    String group
) {
    public ParamDef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Param name cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Param type cannot be null");
        }
    }
}
