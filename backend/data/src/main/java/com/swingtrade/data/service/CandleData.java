package com.swingtrade.data.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable DTO representing OHLCV candle data.
 * Used for transferring market data between layers.
 */
public record CandleData(
    String symbol,
    LocalDate date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume,
    BigDecimal adjClose
) {
    /**
     * Creates a candle with normalized values.
     *
     * @param symbol the stock symbol
     * @param date the trading date
     * @param open open price
     * @param high high price
     * @param low low price
     * @param close close price
     * @param volume trading volume
     * @param adjClose adjusted close price
     * @return candle data
     */
    public static CandleData of(String symbol, LocalDate date,
                                 BigDecimal open, BigDecimal high, BigDecimal low,
                                 BigDecimal close, long volume) {
        return new CandleData(symbol, date, open, high, low, close, volume, close);
    }

    /**
     * Creates a candle with adjusted close.
     */
    public static CandleData of(String symbol, LocalDate date,
                                 BigDecimal open, BigDecimal high, BigDecimal low,
                                 BigDecimal close, long volume, BigDecimal adjClose) {
        return new CandleData(symbol, date, open, high, low, close, volume, adjClose);
    }
}
