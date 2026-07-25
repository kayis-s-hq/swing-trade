package com.swingtrade.domain.store;

import com.swingtrade.domain.OhlcvCandle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CandleStore {

    Optional<OhlcvCandle> findBySymbolAndDate(String symbol, LocalDate date);

    List<OhlcvCandle> findBySymbol(String symbol);

    List<OhlcvCandle> findBySymbolAndDateRange(String symbol, LocalDate from, LocalDate to);

    List<OhlcvCandle> findTopBySymbolOrderByDateDesc(String symbol, int limit);

    Optional<OhlcvCandle> findLatestBySymbol(String symbol);

    void save(OhlcvCandle candle);

    boolean existsBySymbolAndDate(String symbol, LocalDate date);

    List<String> findAllDistinctSymbols();

    Optional<OhlcvCandle> findEarliestBySymbol(String symbol);

    long countBySymbol(String symbol);

    void deleteBySymbol(String symbol);

    Optional<OhlcvCandle> findLatestBySymbolBeforeDate(String symbol, LocalDate date);

    Optional<OhlcvCandle> findFirstBySymbolAndDateAfterOrderByDateAsc(String symbol, LocalDate date);

    Optional<OhlcvCandle> findNthBySymbolAndDateAfterOrderByDateAsc(String symbol, LocalDate after, int n);

    List<OhlcvCandle> findAllBySymbolOrderByDateDesc(String symbol);

    /**
     * Finds up to N candles before a date in ascending order.
     * Used for computing SMA-200 leading up to a reference date.
     */
    List<OhlcvCandle> findLastNBySymbolBeforeDateAsc(String symbol, LocalDate before, int n);
}