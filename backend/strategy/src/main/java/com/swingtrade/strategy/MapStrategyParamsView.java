package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.util.Map;

/** {@link StrategyParamsView} backed by an immutable copy of a plain map. */
final class MapStrategyParamsView implements StrategyParamsView {

    private final Map<String, Object> values;

    MapStrategyParamsView(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    @Override
    public int getInt(String name) {
        return ((Number) require(name)).intValue();
    }

    @Override
    public BigDecimal getDecimal(String name) {
        Object value = require(name);
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    @Override
    public boolean getBoolean(String name) {
        return (Boolean) require(name);
    }

    @Override
    public String getString(String name) {
        return String.valueOf(require(name));
    }

    private Object require(String name) {
        Object value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing strategy param: " + name);
        }
        return value;
    }
}
