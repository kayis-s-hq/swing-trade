package com.swingtrade.domain.store;

import com.swingtrade.domain.UniverseSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UniverseSnapshotStore {
    Optional<UniverseSnapshot> findBySymbolAndDate(String symbol, LocalDate snapshotDate);

    /** Returns the latest recorded membership observation available on or before the date. */
    Optional<UniverseSnapshot> findLatestBySymbolAndDateOnOrBefore(String symbol, LocalDate date);

    List<UniverseSnapshot> findByDate(LocalDate snapshotDate);

    void save(UniverseSnapshot snapshot);
}
