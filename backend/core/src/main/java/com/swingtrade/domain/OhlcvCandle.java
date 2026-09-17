package com.swingtrade.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/**
 * Represents a candlestick data point for a stock, containing Open, High, Low, Close,
 * and Volume information. This is the fundamental unit of market data used throughout
 * the swing trading system for technical analysis.
 *
 * @param symbol    the stock symbol (e.g., "RELIANCE", "TCS")
 * @param date      the date of the candlestick data (trading day)
 * @param open      the opening price of the stock for the day
 * @param high      the highest price reached during the day
 * @param low       the lowest price reached during the day
 * @param close     the closing price of the stock for the day
 * @param volume    the total trading volume for the day
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
    /**
     * Returns an analytical candle on the adjusted-close price basis. Raw OHLC values are kept
     * unchanged in storage and for execution; this view prevents historical splits/bonuses from
     * creating artificial technical gaps or breakouts.
     */
    public OhlcvCandle adjustedForAnalysis() {
        if (close == null || close.signum() <= 0 || adjClose == null || adjClose.signum() <= 0
                || open == null || high == null || low == null) {
            return this;
        }
        BigDecimal factor = adjClose.divide(close, MathContext.DECIMAL128);
        return new OhlcvCandle(symbol, date,
                open.multiply(factor), high.multiply(factor), low.multiply(factor), adjClose, volume, adjClose);
    }
    /**
     * Creates a new OhlcvCandle with calculated values for analysis.
     *
     * @param symbol the stock symbol
     * @param date the trading date
     * @param open the opening price
     * @param high the highest price
     * @param low the lowest price
     * @param close the closing price
     * @param volume the trading volume
     * @return a new OhlcvCandle instance
     */
    public static OhlcvCandle of(
        String symbol,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
    ) {
        return new OhlcvCandle(symbol, date, open, high, low, close, volume, close);
    }

    /**
     * Returns the daily price range (high - low).
     *
     * @return the price range as a BigDecimal
     */
    public BigDecimal getRange() {
        return high.subtract(low);
    }

    /**
     * Returns the daily price change percentage from open to close.
     *
     * @return the change percentage as a BigDecimal (0-100 scale)
     */
    public BigDecimal getChangePercent() {
        if (open.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return close.subtract(open).multiply(BigDecimal.valueOf(100)).divide(open, 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Returns true if this is a bullish candle (close > open).
     *
     * @return true if bullish, false otherwise
     */
    public boolean isBullish() {
        return close.compareTo(open) > 0;
    }

    /**
     * Returns true if this is a bearish candle (close < open).
     *
     * @return true if bearish, false otherwise
     */
    public boolean isBearish() {
        return close.compareTo(open) < 0;
    }
}
