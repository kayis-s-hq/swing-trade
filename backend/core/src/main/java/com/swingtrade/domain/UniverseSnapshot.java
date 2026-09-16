package com.swingtrade.domain;

import java.time.Instant;
import java.time.LocalDate;

/** A point-in-time observation of whether a symbol belonged to a tradable universe. */
public record UniverseSnapshot(
    String symbol,
    LocalDate snapshotDate,
    String exchange,
    String isin,
    boolean included,
    String source,
    Instant capturedAt
) {
    public UniverseSnapshot {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");
        if (snapshotDate == null) throw new IllegalArgumentException("Snapshot date is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Source is required");
        if (capturedAt == null) throw new IllegalArgumentException("Captured time is required");
    }
}
