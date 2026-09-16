package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One daily mark-to-market equity observation (plan §6.1, fixes finding F10). */
public record DailyEquityPoint(LocalDate date, BigDecimal equity) {
    public DailyEquityPoint {
        if (date == null || equity == null) {
            throw new IllegalArgumentException("date and equity cannot be null");
        }
    }
}
