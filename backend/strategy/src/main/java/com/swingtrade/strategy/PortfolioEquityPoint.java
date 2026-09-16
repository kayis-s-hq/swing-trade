package com.swingtrade.strategy;

import java.time.LocalDate;

/** A marked portfolio value at a date processed by the portfolio simulator. */
public record PortfolioEquityPoint(LocalDate date, double equity) {
}
