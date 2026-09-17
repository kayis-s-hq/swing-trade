package com.swingtrade.strategy;

import java.time.LocalDate;

/** A marked portfolio value at a date processed by the portfolio simulator. */
public record PortfolioEquityPoint(
        LocalDate date,
        double equity,
        double settledCash,
        double unsettledProceeds,
        double positionMarketValue
) {
    /** Source-compatible constructor for callers that only provide a total equity value. */
    public PortfolioEquityPoint(LocalDate date, double equity) {
        this(date, equity, equity, 0.0, 0.0);
    }
}
