package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyConfigTest {
    @Test
    void createCalculatesHashAndDefensivelyCopiesMaps() {
        Map<String, Object> params = new HashMap<>();
        params.put("emaFast", 20);
        StrategyConfig config = StrategyConfig.create("BREAKOUT_STRICT", 1, "BREAKOUT", params,
            Map.of("sentimentGate", true), StrategyConfig.Mode.SHADOW,
            new BigDecimal("500000"), true, null, LocalDateTime.of(2026, 9, 16, 12, 0));

        params.put("emaFast", 99);

        assertThat(config.params()).containsEntry("emaFast", 20);
        assertThat(config.paramsHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThatThrownBy(() -> config.params().put("x", true))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidVersionModeCapitalAndHash() {
        assertThatThrownBy(() -> new StrategyConfig(null, "A", 0, "BREAKOUT", Map.of(), Map.of(),
            "bad", StrategyConfig.Mode.OFF, BigDecimal.ONE, false, null, LocalDateTime.now()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StrategyConfig.create("A", 1, "BREAKOUT", Map.of(), Map.of(),
            StrategyConfig.Mode.OFF, new BigDecimal("-1"), false, null, LocalDateTime.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
