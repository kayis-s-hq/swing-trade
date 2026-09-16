package com.swingtrade.domain.store;

import com.swingtrade.domain.PriceBand;

import java.time.LocalDate;
import java.util.Optional;

/** Persistence port for explicitly supplied exchange price bands. */
public interface PriceBandStore {
    Optional<PriceBand> findBySymbolAndDate(String symbol, LocalDate date);

    void save(PriceBand priceBand);
}
