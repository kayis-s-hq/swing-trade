package com.swingtrade.domain.store;

import com.swingtrade.domain.Signal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SignalStore {

    List<Signal> findAll();

    List<Signal> findBySymbol(String symbol);

    List<Signal> findBySymbolOrderByDateDesc(String symbol);

    List<Signal> findBySymbolAndDate(String symbol, LocalDate date);

    List<Signal> findByType(Signal.SignalType type);

    List<Signal> findUnprocessed();

    Signal save(Signal signal);

    /**
     * Saves a signal with an optional warning flag (e.g., NEUTRAL_SENTIMENT).
     */
    Signal save(Signal signal, String warningFlag);

    void markProcessed(Long signalId);

    Optional<Signal> findLatestBySymbol(String symbol);

    List<String> findAllDistinctSymbols();

    long countBySymbolAndDate(String symbol, LocalDate date);

    /**
     * Finds all BUY signals generated on or after a date (DB-level filtering).
     *
     * @param sinceDate the start date
     * @return list of BUY signals
     */
    List<Signal> findBuySignalsSince(LocalDate sinceDate);

    /**
     * Deletes all signals for a given date. Used to clear stale signals before regeneration.
     *
     * @param date the signal date
     * @return number of signals deleted
     */
    int deleteByDate(LocalDate date);

    /**
     * Deletes all signals. Used to clear all stale signals before full regeneration.
     *
     * @return number of signals deleted
     */
    int deleteAllSignals();
}