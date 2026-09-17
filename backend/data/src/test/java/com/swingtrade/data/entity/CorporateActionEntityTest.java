package com.swingtrade.data.entity;

import com.swingtrade.domain.CorporateAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CorporateActionEntityTest {
    @Test
    void domainRoundTripPreservesExplicitAction() {
        CorporateAction original = new CorporateAction("TCS", LocalDate.of(2024, 2, 1), "SPLIT",
            new BigDecimal("0.5"), null, "exchange", Instant.parse("2024-02-02T10:15:30Z"));
        assertThat(CorporateActionEntity.fromDomain(original).toDomain()).isEqualTo(original);
    }
}
