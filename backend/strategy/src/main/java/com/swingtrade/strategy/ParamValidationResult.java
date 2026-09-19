package com.swingtrade.strategy;

import java.util.List;
import java.util.Map;

/**
 * Result of {@link ParamSchemaValidator#validate}: either a list of errors, or the resolved
 * params map with defaults filled in for any keys the caller omitted (plan §4.3: "missing keys
 * filled with defaults at create time and stored explicitly").
 */
public record ParamValidationResult(boolean valid, List<String> errors, Map<String, Object> resolvedParams) {

    public static ParamValidationResult ok(Map<String, Object> resolvedParams) {
        return new ParamValidationResult(true, List.of(), Map.copyOf(resolvedParams));
    }

    public static ParamValidationResult failed(List<String> errors) {
        return new ParamValidationResult(false, List.copyOf(errors), Map.of());
    }
}
