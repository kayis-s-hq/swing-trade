package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ParamSchema/cross-field validation for the new PULLBACK and SQUEEZE strategy types (plan §5.2/§5.3). */
class PullbackAndSqueezeParamValidationTest {

    private final ParamSchemaValidator validator = new ParamSchemaValidator();

    @Test
    void pullback_defaultsResolveCleanly() {
        PullbackStrategy strategy = new PullbackStrategy();
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), Map.of());
        assertThat(result.valid()).isTrue();
        assertThat(result.resolvedParams()).containsEntry("trendEma", 50).containsEntry("pullbackEma", 20);
    }

    @Test
    void pullback_rejectsPullbackEmaNotLessThanTrendEma() {
        PullbackStrategy strategy = new PullbackStrategy();
        Map<String, Object> params = new HashMap<>();
        params.put("pullbackEma", 60);
        params.put("trendEma", 50);
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), params);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("pullbackEma must be less than trendEma"));
    }

    @Test
    void pullback_rejectsRsiDipNotLessThanRsiTrigger() {
        PullbackStrategy strategy = new PullbackStrategy();
        Map<String, Object> params = new HashMap<>();
        params.put("rsiDip", BigDecimal.valueOf(50));
        params.put("rsiTrigger", BigDecimal.valueOf(45));
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), params);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void squeeze_defaultsResolveCleanly() {
        SqueezeStrategy strategy = new SqueezeStrategy();
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), Map.of());
        assertThat(result.valid()).isTrue();
        assertThat(result.resolvedParams()).containsEntry("squeezeDefinition", "BB_PCTILE");
    }

    @Test
    void squeeze_rejectsUnknownSqueezeDefinition() {
        SqueezeStrategy strategy = new SqueezeStrategy();
        Map<String, Object> params = new HashMap<>();
        params.put("squeezeDefinition", "NOT_A_MODE");
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), params);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void squeeze_rejectsPctileOutOfRange() {
        SqueezeStrategy strategy = new SqueezeStrategy();
        Map<String, Object> params = new HashMap<>();
        params.put("squeezePctile", BigDecimal.valueOf(150));
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), params);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void squeeze_rejectsRecencyGreaterThanLookback() {
        SqueezeStrategy strategy = new SqueezeStrategy();
        Map<String, Object> params = new HashMap<>();
        params.put("squeezeLookback", 10);
        params.put("squeezeRecency", 20);
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), params);
        assertThat(result.valid()).isFalse();
    }
}
