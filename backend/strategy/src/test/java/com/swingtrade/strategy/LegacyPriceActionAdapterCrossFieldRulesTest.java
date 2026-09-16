package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class LegacyPriceActionAdapterCrossFieldRulesTest {

    private final LegacyPriceActionAdapter adapter = new LegacyPriceActionAdapter(mock(PriceActionStrategy.class));

    @Test
    void defaultParamsSatisfyCrossFieldRules() {
        ParamSchemaValidator validator = new ParamSchemaValidator();
        Map<String, Object> defaults = adapter.paramSchema().params().stream()
            .collect(java.util.stream.Collectors.toMap(ParamDef::name, ParamDef::defaultValue));

        ParamValidationResult result = validator.validate(adapter.paramSchema(), adapter.crossFieldRules(), defaults);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsEmaFastGreaterThanOrEqualToEmaSlow() {
        List<CrossFieldRule> rules = adapter.crossFieldRules();
        boolean emaRulePasses = rules.stream()
            .filter(r -> r.message().contains("emaFast"))
            .allMatch(r -> r.test(Map.of("emaFast", 60, "emaSlow", 20, "rsiMin", 50, "rsiMax", 65)));

        assertThat(emaRulePasses).isFalse();
    }

    @Test
    void rejectsRsiMinGreaterThanOrEqualToRsiMax() {
        List<CrossFieldRule> rules = adapter.crossFieldRules();
        boolean rsiRulePasses = rules.stream()
            .filter(r -> r.message().contains("rsiMin"))
            .allMatch(r -> r.test(Map.of("emaFast", 20, "emaSlow", 50, "rsiMin", 70, "rsiMax", 65)));

        assertThat(rsiRulePasses).isFalse();
    }
}
