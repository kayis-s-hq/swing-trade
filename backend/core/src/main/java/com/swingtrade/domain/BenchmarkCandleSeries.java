package com.swingtrade.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Persisted benchmark candles for a bounded historical evaluation window.
 *
 * <p>The dates describe the requested window; the candle list contains only
 * observations available in that window. Callers must treat an absent series
 * as unavailable rather than attempting to obtain a live replacement.</p>
 */
public record BenchmarkCandleSeries(
        String benchmark,
        LocalDate from,
        LocalDate to,
        List<OhlcvCandle> candles
) {
    public BenchmarkCandleSeries {
        if (benchmark == null || benchmark.isBlank()) {
            throw new IllegalArgumentException("Benchmark cannot be blank");
        }
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Benchmark window must be valid");
        }
        candles = candles == null ? List.of() : List.copyOf(candles);
    }
}
