package com.swingtrade.domain;

import java.math.BigDecimal;

/** Conservative circuit-limit execution rules using explicit band data. */
public final class PriceBandPolicy {
    private PriceBandPolicy() {}

    /** A long entry at the upper band is treated as locked and is not filled. */
    public static boolean blocksLongEntry(PriceBand band, BigDecimal openPrice) {
        return band != null && openPrice != null
            && openPrice.compareTo(band.upperLimit()) >= 0;
    }

    /** A long exit is deferred when the candle closes at or below its lower band. */
    public static boolean blocksLongExit(PriceBand band, OhlcvCandle candle) {
        return band != null && candle != null && candle.close() != null
            && candle.close().compareTo(band.lowerLimit()) <= 0;
    }
}
