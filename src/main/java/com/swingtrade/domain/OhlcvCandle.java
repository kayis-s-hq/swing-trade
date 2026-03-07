package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a candlestick data point for a stock, containing Open, High, Low, Close, and Volume information.
 * This record captures the OHLCV data for a specific stock on a specific date.
 *
 * @param symbol    the stock symbol (e.g., "AAPL")
 * @param date      the date of the candlestick data
 * @param open      the opening price
 * @param high      the highest price during the period
 * @param low       the lowest price during the period
 * @param close     the closing price
 * @param volume    the trading volume
 * @param adjClose  the adjusted closing price (accounts for splits and dividends)
 */
public record OhlcvCandle(
    String symbol,
    LocalDate date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    Long volume,
    BigDecimal adjClose
) {
}
