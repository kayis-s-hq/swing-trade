package com.swingtrade.data.entity;

import com.swingtrade.domain.PriceBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PriceBandEntityTest {
    @Test
    void domainRoundTripPreservesExplicitBand() {
        PriceBand original = new PriceBand("TCS", LocalDate.of(2026, 1, 2),
            new BigDecimal("90.00"), new BigDecimal("110.00"));

        PriceBand restored = PriceBandEntity.fromDomain(original).toDomain();

        assertThat(restored).isEqualTo(original);
    }
}
