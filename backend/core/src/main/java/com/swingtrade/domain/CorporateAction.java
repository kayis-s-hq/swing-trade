package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** An explicitly sourced corporate action; no adjustment is inferred from candles. */
public record CorporateAction(
    String symbol,
    LocalDate effectiveDate,
    String actionType,
    BigDecimal adjustmentFactor,
    BigDecimal cashAmount,
    String source,
    Instant recordedAt
) {
    public CorporateAction {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");
        if (effectiveDate == null) throw new IllegalArgumentException("Effective date is required");
        if (actionType == null || actionType.isBlank()) throw new IllegalArgumentException("Action type is required");
        if (adjustmentFactor == null && cashAmount == null) {
            throw new IllegalArgumentException("An adjustment factor or cash amount is required");
        }
        if (adjustmentFactor != null && adjustmentFactor.signum() <= 0) {
            throw new IllegalArgumentException("Adjustment factor must be positive");
        }
        if (cashAmount != null && cashAmount.signum() < 0) {
            throw new IllegalArgumentException("Cash amount cannot be negative");
        }
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Source is required");
        if (recordedAt == null) throw new IllegalArgumentException("Recorded time is required");
    }
}
