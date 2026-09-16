package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParamSchemaValidatorTest {

    private final ParamSchemaValidator validator = new ParamSchemaValidator();

    private ParamSchema schema() {
        return new ParamSchema(List.of(
            new ParamDef("emaFast", ParamType.INT, BigDecimal.valueOf(5), BigDecimal.valueOf(100), 20, "fast", "trend"),
            new ParamDef("emaSlow", ParamType.INT, BigDecimal.valueOf(20), BigDecimal.valueOf(250), 50, "slow", "trend"),
            new ParamDef("highProximity", ParamType.DECIMAL, BigDecimal.valueOf(0.85), BigDecimal.ONE,
                BigDecimal.valueOf(0.97), "proximity", "trend"),
            new ParamDef("sentimentGate", ParamType.BOOL, null, null, false, "gate", "overlay"),
            new ParamDef("mode", ParamType.ENUM, null, null, "STRICT", "mode", "scoring")
        ));
    }

    @Test
    void fillsMissingKeysWithDefaults() {
        ParamValidationResult result = validator.validate(schema(), List.of(), Map.of("emaFast", 10));

        assertThat(result.valid()).isTrue();
        assertThat(result.resolvedParams())
            .containsEntry("emaFast", 10)
            .containsEntry("emaSlow", 50)
            .containsEntry("sentimentGate", false)
            .containsEntry("mode", "STRICT");
    }

    @Test
    void rejectsUnknownKeys() {
        ParamValidationResult result = validator.validate(schema(), List.of(), Map.of("bogusKey", 1));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Unknown param: bogusKey"));
    }

    @Test
    void rejectsOutOfRangeValues() {
        ParamValidationResult result = validator.validate(schema(), List.of(), Map.of("emaFast", 1000));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("emaFast") && e.contains("maximum"));
    }

    @Test
    void appliesCrossFieldRules() {
        List<CrossFieldRule> rules = List.of(
            new CrossFieldRule("emaFast must be less than emaSlow", p ->
                ((Number) p.get("emaFast")).intValue() < ((Number) p.get("emaSlow")).intValue())
        );

        ParamValidationResult result = validator.validate(schema(), rules, Map.of("emaFast", 60, "emaSlow", 50));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("emaFast must be less than emaSlow");
    }

    @Test
    void acceptsValidCrossFieldParams() {
        List<CrossFieldRule> rules = List.of(
            new CrossFieldRule("emaFast must be less than emaSlow", p ->
                ((Number) p.get("emaFast")).intValue() < ((Number) p.get("emaSlow")).intValue())
        );

        ParamValidationResult result = validator.validate(schema(), rules, Map.of("emaFast", 20, "emaSlow", 50));

        assertThat(result.valid()).isTrue();
    }
}
