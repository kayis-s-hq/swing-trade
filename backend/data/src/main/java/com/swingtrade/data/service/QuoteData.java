package com.swingtrade.data.service;

import java.math.BigDecimal;

/**
 * Real-time quote data. Source depends on implementation:
 * YahooFinanceClient — derived from chart endpoint meta field (v7/quote is dead).
 * FyersServiceClient — from Fyers v3 GetStockQuotes API.
 */
public record QuoteData(
    String symbol,
    String shortName,
    String longName,
    BigDecimal regularMarketPrice,
    BigDecimal regularMarketChange,
    BigDecimal regularMarketChangePercent,
    BigDecimal regularMarketDayHigh,
    BigDecimal regularMarketDayLow,
    BigDecimal regularMarketPreviousClose,
    BigDecimal fiftyTwoWeekHigh,
    BigDecimal fiftyTwoWeekLow,
    Long regularMarketVolume,
    String currency,
    String marketState,
    BigDecimal fiftyDayAverage,
    BigDecimal twoHundredDayAverage
) {
    public static QuoteData of(
        String symbol, String shortName, String longName,
        BigDecimal regularMarketPrice, BigDecimal regularMarketChange,
        BigDecimal regularMarketChangePercent,
        BigDecimal regularMarketDayHigh, BigDecimal regularMarketDayLow,
        BigDecimal regularMarketPreviousClose,
        BigDecimal fiftyTwoWeekHigh, BigDecimal fiftyTwoWeekLow,
        Long regularMarketVolume, String currency,
        String marketState, BigDecimal fiftyDayAverage,
        BigDecimal twoHundredDayAverage
    ) {
        return new QuoteData(
            symbol, shortName, longName,
            regularMarketPrice, regularMarketChange, regularMarketChangePercent,
            regularMarketDayHigh, regularMarketDayLow, regularMarketPreviousClose,
            fiftyTwoWeekHigh, fiftyTwoWeekLow, regularMarketVolume, currency,
            marketState, fiftyDayAverage, twoHundredDayAverage
        );
    }
}
