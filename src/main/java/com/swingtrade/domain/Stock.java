package com.swingtrade.domain;

/**
 * Represents a stock entity with essential identifying and descriptive information.
 * This record captures fundamental stock attributes used for tracking and analysis.
 *
 * @param symbol    the unique identifier for the stock (e.g., "AAPL")
 * @param exchange  the stock exchange where the stock is traded (e.g., "NASDAQ")
 * @param name      the full company name (e.g., "Apple Inc.")
 * @param sector    the industry sector the company belongs to (e.g., "Technology")
 */
public record Stock(
    String symbol,
    String exchange,
    String name,
    String sector
) {
}
