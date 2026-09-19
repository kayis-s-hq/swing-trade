package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniverseSnapshotTest {
    @Test
    void requiresSourceAndDate() {
        assertThatThrownBy(() -> new UniverseSnapshot("TCS", null, "NSE", null, true, "NSE", Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UniverseSnapshot("TCS", LocalDate.now(), "NSE", null, true, "", Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
