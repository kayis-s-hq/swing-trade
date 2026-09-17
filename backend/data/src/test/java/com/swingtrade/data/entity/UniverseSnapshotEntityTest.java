package com.swingtrade.data.entity;

import com.swingtrade.domain.UniverseSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UniverseSnapshotEntityTest {
    @Test
    void domainRoundTripPreservesPointInTimeMembership() {
        UniverseSnapshot original = new UniverseSnapshot("TCS", LocalDate.of(2024, 1, 2), "NSE", "INE467B01029",
            false, "nse-archive", Instant.parse("2024-01-03T10:15:30Z"));
        assertThat(UniverseSnapshotEntity.fromDomain(original).toDomain()).isEqualTo(original);
    }
}
