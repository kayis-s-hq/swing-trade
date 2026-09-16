package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyConfigHasherTest {

    private final StrategyConfigHasher hasher = new StrategyConfigHasher(new ObjectMapper());

    @Test
    void sameLogicalParamsHashIdenticallyRegardlessOfKeyOrder() {
        Map<String, Object> paramsA = new LinkedHashMap<>();
        paramsA.put("emaFast", 20);
        paramsA.put("emaSlow", 50);

        Map<String, Object> paramsB = new LinkedHashMap<>();
        paramsB.put("emaSlow", 50);
        paramsB.put("emaFast", 20);

        String hashA = hasher.hash("BREAKOUT", paramsA, Map.of("sentimentGate", true));
        String hashB = hasher.hash("BREAKOUT", paramsB, Map.of("sentimentGate", true));

        assertThat(hashA).isEqualTo(hashB);
        assertThat(hashA).hasSize(64);
    }

    @Test
    void differentParamsHashDifferently() {
        String hashA = hasher.hash("BREAKOUT", Map.of("emaFast", 20), Map.of());
        String hashB = hasher.hash("BREAKOUT", Map.of("emaFast", 21), Map.of());

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void differentStrategyTypeHashesDifferently() {
        String hashA = hasher.hash("BREAKOUT", Map.of("emaFast", 20), Map.of());
        String hashB = hasher.hash("PULLBACK", Map.of("emaFast", 20), Map.of());

        assertThat(hashA).isNotEqualTo(hashB);
    }
}
