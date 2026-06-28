package com.swingtrade.data.service;

import java.math.BigDecimal;

/**
 * Immutable DTO representing instrument details for a stock.
 * Contains metadata about exchange listings, lot size, etc.
 */
public record InstrumentDetails(
    String symbol,
    String name,
    String exchangeSegment,
    String instrumentType,
    Integer lotSize,
    BigDecimal tickSize,
    String isin
) {
    /**
     * Creates instrument details.
     *
     * @param symbol the stock symbol
     * @param name the company name
     * @param exchangeSegment exchange segment (NSE_EQ, BSE_EQ)
     * @param instrumentType instrument type
     * @param lotSize trading lot size
     * @param tickSize minimum price tick
     * @param isin ISIN code
     * @return instrument details
     */
    public static InstrumentDetails of(String symbol, String name, String exchangeSegment,
                                        String instrumentType, Integer lotSize,
                                        BigDecimal tickSize, String isin) {
        return new InstrumentDetails(symbol, name, exchangeSegment, instrumentType,
                                     lotSize, tickSize, isin);
    }
}
