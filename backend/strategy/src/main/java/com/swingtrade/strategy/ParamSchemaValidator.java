package com.swingtrade.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a raw params map against a {@link ParamSchema} and a type's {@link CrossFieldRule}s
 * (plan §4.3):
 * <ul>
 *   <li>unknown keys are rejected</li>
 *   <li>each known key is type-checked (INT/DECIMAL/BOOL/ENUM) and range-checked (min/max)</li>
 *   <li>missing keys are filled with the schema's default, and returned explicitly in the
 *       resolved map (so a version is self-contained even if defaults change later)</li>
 *   <li>cross-field rules run last, against the fully-resolved map</li>
 * </ul>
 */
@Component
public class ParamSchemaValidator {

    /**
     * @param schema           the strategy type's declared params
     * @param crossFieldRules  the strategy type's cross-field rules (see
     *                         {@link SignalStrategy#crossFieldRules()})
     * @param rawParams        the caller-supplied params (may omit defaultable keys)
     */
    public ParamValidationResult validate(
        ParamSchema schema, List<CrossFieldRule> crossFieldRules, Map<String, Object> rawParams
    ) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> input = rawParams == null ? Map.of() : rawParams;

        Set<String> knownNames = schema.params().stream().map(ParamDef::name).collect(java.util.stream.Collectors.toSet());
        for (String key : input.keySet()) {
            if (!knownNames.contains(key)) {
                errors.add("Unknown param: " + key);
            }
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (ParamDef def : schema.params()) {
            Object rawValue = input.containsKey(def.name()) ? input.get(def.name()) : def.defaultValue();
            Object coerced = coerceAndValidate(def, rawValue, errors);
            resolved.put(def.name(), coerced);
        }

        if (!errors.isEmpty()) {
            return ParamValidationResult.failed(errors);
        }

        if (crossFieldRules != null) {
            for (CrossFieldRule rule : crossFieldRules) {
                if (!rule.test(resolved)) {
                    errors.add(rule.message());
                }
            }
        }

        if (!errors.isEmpty()) {
            return ParamValidationResult.failed(errors);
        }
        return ParamValidationResult.ok(resolved);
    }

    private Object coerceAndValidate(ParamDef def, Object rawValue, List<String> errors) {
        if (rawValue == null) {
            errors.add(def.name() + ": value is required");
            return null;
        }
        try {
            return switch (def.type()) {
                case INT -> validateNumeric(def, toInt(rawValue), errors);
                case DECIMAL -> validateNumeric(def, toDecimal(rawValue), errors);
                case BOOL -> toBoolean(rawValue);
                case ENUM -> rawValue.toString();
            };
        } catch (NumberFormatException e) {
            errors.add(def.name() + ": expected " + def.type() + ", got '" + rawValue + "'");
            return rawValue;
        }
    }

    private Integer validateNumeric(ParamDef def, Integer value, List<String> errors) {
        checkRange(def, BigDecimal.valueOf(value), errors);
        return value;
    }

    private BigDecimal validateNumeric(ParamDef def, BigDecimal value, List<String> errors) {
        checkRange(def, value, errors);
        return value;
    }

    private void checkRange(ParamDef def, BigDecimal value, List<String> errors) {
        if (def.min() != null && value.compareTo(def.min()) < 0) {
            errors.add(def.name() + ": " + value + " is below minimum " + def.min());
        }
        if (def.max() != null && value.compareTo(def.max()) > 0) {
            errors.add(def.name() + ": " + value + " is above maximum " + def.max());
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(value.toString());
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
