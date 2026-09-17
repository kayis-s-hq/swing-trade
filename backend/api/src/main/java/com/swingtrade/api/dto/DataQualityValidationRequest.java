package com.swingtrade.api.dto;

import java.time.LocalDate;

/** Request for validating persisted OHLCV data over a bounded date window. */
public record DataQualityValidationRequest(
        String symbol,
        LocalDate fromDate,
        LocalDate toDate
) {
    public DataQualityValidationRequest {
        symbol = symbol == null ? null : symbol.trim().toUpperCase();
    }
}
