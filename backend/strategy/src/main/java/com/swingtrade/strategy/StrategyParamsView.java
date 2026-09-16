package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Read-only, typed access to a resolved set of strategy parameter values (one immutable config
 * version's params, per plan §4). Kept intentionally minimal in Phase 1 - no validation against
 * a {@link ParamSchema} yet; that lands with the config persistence/validation work in Phase 2.
 */
public interface StrategyParamsView {

    int getInt(String name);

    BigDecimal getDecimal(String name);

    boolean getBoolean(String name);

    String getString(String name);

    /** Builds a view backed by a plain map, e.g. a strategy's hardcoded defaults for tests. */
    static StrategyParamsView of(Map<String, Object> values) {
        return new MapStrategyParamsView(values);
    }
}
