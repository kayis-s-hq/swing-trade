package com.swingtrade.domain.store;

import com.swingtrade.domain.BenchmarkCandleSeries;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Supplies persisted benchmark observations for historical calculations.
 * Implementations must not use a live market-data fallback for this operation.
 */
public interface BenchmarkDataAdapter {

    /**
     * Finds the NIFTY benchmark inside an explicitly bounded historical window.
     * Empty means benchmark data is unavailable and the benchmark must be
     * reported as unavailable by the caller.
     */
    Optional<BenchmarkCandleSeries> findNifty50(LocalDate from, LocalDate to);
}
