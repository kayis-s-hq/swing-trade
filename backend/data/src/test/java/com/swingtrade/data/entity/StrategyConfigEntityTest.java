package com.swingtrade.data.entity;

import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyConfigEntityTest {
    @Test
    void roundTripsDomainConfiguration() {
        StrategyConfig original = StrategyConfig.create("PULLBACK_B", 2, "PULLBACK",
            Map.of("rsiMin", 55), Map.of("sentimentGate", false), StrategyConfig.Mode.BACKTEST_ONLY,
            new BigDecimal("250000"), false, "experiment", LocalDateTime.of(2026, 9, 16, 10, 0));

        StrategyConfig restored = StrategyConfigEntity.fromDomain(original).toDomain();

        assertThat(restored).isEqualTo(original);
    }
}
