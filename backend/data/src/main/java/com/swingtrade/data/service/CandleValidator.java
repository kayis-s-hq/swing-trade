package com.swingtrade.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Validates OHLCV candle data for quality and consistency.
 * Rejects candles with impossible price relationships or invalid values.
 */
public class CandleValidator {

    private static final Logger logger = LoggerFactory.getLogger(CandleValidator.class);

    private CandleValidator() {}

    public static boolean isValid(CandleData candle) {
        return isValid(candle, true);
    }

    /**
     * Validates a candle's OHLCV values.
     *
     * @param candle the candle to validate
     * @param log whether to log rejected candles
     * @return true if valid, false otherwise
     */
    public static boolean isValid(CandleData candle, boolean log) {
        if (candle.open() == null || candle.high() == null || candle.low() == null
            || candle.close() == null || candle.volume() <= 0) {
            if (log) logger.warn("Rejected candle {} {}: null/zero OHLCV", candle.symbol(), candle.date());
            return false;
        }

        if (candle.open().compareTo(BigDecimal.ZERO) <= 0
            || candle.high().compareTo(BigDecimal.ZERO) <= 0
            || candle.low().compareTo(BigDecimal.ZERO) <= 0
            || candle.close().compareTo(BigDecimal.ZERO) <= 0) {
            if (log) logger.warn("Rejected candle {} {}: non-positive price", candle.symbol(), candle.date());
            return false;
        }

        if (candle.high().compareTo(candle.low()) < 0) {
            if (log) logger.warn("Rejected candle {} {} high < low: H={} L={}",
                candle.symbol(), candle.date(), candle.high(), candle.low());
            return false;
        }

        if (candle.high().compareTo(candle.open()) < 0 || candle.high().compareTo(candle.close()) < 0) {
            if (log) logger.warn("Rejected candle {} {} high < open/close: H={} O={} C={}",
                candle.symbol(), candle.date(), candle.high(), candle.open(), candle.close());
            return false;
        }

        if (candle.low().compareTo(candle.open()) > 0 || candle.low().compareTo(candle.close()) > 0) {
            if (log) logger.warn("Rejected candle {} {} low > open/close: L={} O={} C={}",
                candle.symbol(), candle.date(), candle.low(), candle.open(), candle.close());
            return false;
        }

        return true;
    }
}
