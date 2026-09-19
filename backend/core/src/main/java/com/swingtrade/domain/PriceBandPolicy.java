package com.swingtrade.domain;

import java.math.BigDecimal;

/**
 * Conservative circuit-limit execution rules using explicit band data.
 *
 * <p>Long-only by design: the platform has no short-selling execution path anywhere
 * ({@code TradingStrategy}, {@code PaperTradingEngine}, {@code BacktestEngine}), matching
 * assumption A2 ("Long-only; no short signals") in
 * {@code docs/plans/2026-09-16-configurable-multi-strategy.md}. There is deliberately no
 * short-side counterpart to {@link #blocksLongEntry} / {@link #blocksLongExit} — a short
 * entry would be blocked at the lower band and a short exit deferred at the upper band, but
 * since no short position can ever be opened, that logic would be dead code. If short-selling
 * is ever added, this class must gain a mirror-image policy at that time; until then, treat
 * the absence of a short-side method as an intentional no-op, not an oversight.
 */
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
