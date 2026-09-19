package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit exchange-provided daily price band for a symbol. */
public record PriceBand(
    String symbol,
    LocalDate date,
    BigDecimal lowerLimit,
    BigDecimal upperLimit
) {
    public PriceBand {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");
        if (date == null) throw new IllegalArgumentException("Date is required");
        if (lowerLimit == null || upperLimit == null
            || lowerLimit.signum() <= 0 || upperLimit.signum() <= 0
            || lowerLimit.compareTo(upperLimit) >= 0) {
            throw new IllegalArgumentException("Price band limits must be positive and ordered");
        }
    }
}
