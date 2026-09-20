package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A marked portfolio value at a date processed by the portfolio simulator; amounts are at {@link FinancialScale#MONEY_SCALE}. */
public record PortfolioEquityPoint(
        LocalDate date,
        BigDecimal equity,
        BigDecimal settledCash,
        BigDecimal unsettledProceeds,
        BigDecimal positionMarketValue
) {
    public PortfolioEquityPoint {
        equity = FinancialScale.money(equity);
        settledCash = FinancialScale.money(settledCash);
        unsettledProceeds = FinancialScale.money(unsettledProceeds);
        positionMarketValue = FinancialScale.money(positionMarketValue);
    }

    /** Source-compatible constructor for callers that only provide a total equity value. */
    public PortfolioEquityPoint(LocalDate date, BigDecimal equity) {
        this(date, equity, equity, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
