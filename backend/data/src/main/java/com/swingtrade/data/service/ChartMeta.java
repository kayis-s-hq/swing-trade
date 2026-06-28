package com.swingtrade.data.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Metadata from Yahoo Finance chart endpoint.
 * Extracted from every chart response at no extra cost.
 */
public record ChartMeta(
    String symbol,
    String exchange,
    String instrumentType,
    String currency,
    String longName,
    String shortName,
    BigDecimal regularMarketPrice,
    BigDecimal fiftyTwoWeekHigh,
    BigDecimal fiftyTwoWeekLow,
    BigDecimal previousClose,
    LocalDateTime regularMarketTime,
    int firstTradeDateEpoch,
    String timezone,
    int gmtoffsetSeconds
) {
    public static ChartMeta of(
        String symbol, String exchange, String instrumentType, String currency,
        String longName, String shortName,
        BigDecimal regularMarketPrice, BigDecimal fiftyTwoWeekHigh, BigDecimal fiftyTwoWeekLow,
        BigDecimal previousClose, LocalDateTime regularMarketTime,
        int firstTradeDateEpoch, String timezone, int gmtoffsetSeconds
    ) {
        return new ChartMeta(symbol, exchange, instrumentType, currency,
            longName, shortName, regularMarketPrice, fiftyTwoWeekHigh, fiftyTwoWeekLow,
            previousClose, regularMarketTime, firstTradeDateEpoch, timezone, gmtoffsetSeconds);
    }
}
