package com.swingtrade.domain.store;

import com.swingtrade.domain.Signal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    Signal save(Signal signal, String warningFlag, String strategy);

    /**
     * Saves a signal produced by the configurable multi-strategy fan-out (plan §7.1), with the
     * additional provenance columns V47 added: {@code strategy_version}, {@code strategy_score},
     * {@code rule_outcomes}, {@code gate_outcomes}.
     *
     * @param strategyVersion the variant's version number
     * @param strategyScore   {@link com.swingtrade.domain.Signal}-independent score in [0,1]
     * @param ruleOutcomes    serialisable rule outcomes (may be {@code null})
     * @param gateOutcomes    serialisable gate outcomes (may be {@code null})
     */
    Signal saveVariantSignal(Signal signal, String warningFlag, String strategy, int strategyVersion,
                              BigDecimal strategyScore, List<Map<String, Object>> ruleOutcomes,
                              List<Map<String, Object>> gateOutcomes);

    void markProcessed(Long signalId);

    Optional<Signal> findLatestBySymbol(String symbol);
    Optional<Signal> findLatestBySymbolAndStrategy(String symbol, String strategy);

    /**
     * Finds the single latest signal per symbol, computed at the DB level (no full-table
     * in-memory dedupe).
     *
     * @return one signal per distinct symbol, the most recent for that symbol
     */
    List<Signal> findLatestSignalPerSymbol();

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
     * Finds all signals within a date range (DB-level filtering).
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of signals in the date range
     */
    List<Signal> findByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Finds signals within a date range filtered by type (DB-level filtering).
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param type signal type (BUY, SELL, HOLD)
     * @return list of signals in the date range with the specified type
     */
    List<Signal> findByDateRangeAndType(LocalDate startDate, LocalDate endDate, Signal.SignalType type);

    /**
     * Finds signals with confidence at or above a threshold (DB-level filtering).
     *
     * @param minConfidence minimum confidence value (0.0 to 1.0)
     * @return list of signals meeting the confidence threshold
     */
    List<Signal> findByMinConfidence(double minConfidence);

    /**
     * Deletes all signals for a given symbol and date. Used to clear stale processed signals before regeneration.
     *
     * @param symbol the stock symbol
     * @param date the signal date
     * @return number of signals deleted
     */
    int deleteBySymbolAndDate(String symbol, LocalDate date);

    int deleteBySymbolAndDateAndStrategy(String symbol, LocalDate date, String strategy);

    /** Idempotency key extended with strategy version (plan §7.1, replaces the F5 key above). */
    int deleteBySymbolAndDateAndStrategyAndVersion(String symbol, LocalDate date, String strategy, int strategyVersion);

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
