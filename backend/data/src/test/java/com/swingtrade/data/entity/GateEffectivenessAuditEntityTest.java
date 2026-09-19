package com.swingtrade.data.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GateEffectivenessAuditEntityTest {
    @Test
    void preservesDecisionIdentityAndVerdict() {
        var entity = new GateEffectivenessAuditEntity("TCS", LocalDate.of(2026, 8, 3),
            "SENTIMENT", "SUPPRESS", "negative sentiment", null);

        assertThat(entity.getSymbol()).isEqualTo("TCS");
        assertThat(entity.getSignalDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(entity.getGateName()).isEqualTo("SENTIMENT");
        assertThat(entity.getVerdict()).isEqualTo("SUPPRESS");
        assertThat(entity.getStrategy()).isEqualTo("DEFAULT");
    }

    @Test
    void preservesExplicitStrategy() {
        var entity = new GateEffectivenessAuditEntity("TCS", LocalDate.of(2026, 8, 3),
            "SENTIMENT", "ALLOW", null, null, "PRICE_ACTION");

        assertThat(entity.getStrategy()).isEqualTo("PRICE_ACTION");
    }
}
