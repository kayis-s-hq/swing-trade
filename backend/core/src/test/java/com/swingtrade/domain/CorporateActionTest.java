package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorporateActionTest {
    @Test
    void requiresAnExplicitAdjustmentOrCashValue() {
        assertThatThrownBy(() -> new CorporateAction("TCS", LocalDate.now(), "SPLIT", null, null,
            "exchange", Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorporateAction("TCS", LocalDate.now(), "SPLIT", BigDecimal.ZERO, null,
            "exchange", Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
