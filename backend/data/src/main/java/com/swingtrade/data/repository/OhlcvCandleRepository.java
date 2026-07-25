package com.swingtrade.data.repository;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for OhlcvCandleEntity operations.
 * Optimized for time-series queries on TimescaleDB hypertable.
 */
@Repository
public interface OhlcvCandleRepository extends JpaRepository<OhlcvCandleEntity, Long> {

    /**
     * Finds the most recent candle for a stock.
     *
     * @param symbol the stock symbol
     * @return optional containing the most recent candle
     */
    @Query("SELECT c FROM OhlcvCandleEntity c WHERE c.symbol = :symbol ORDER BY c.date DESC LIMIT 1")
    Optional<OhlcvCandleEntity> findLatestBySymbol(@Param("symbol") String symbol);

    /**
     * Finds all candles for a stock within a date range, ordered by date descending.
     *
     * @param symbol the stock symbol
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param pageable pagination
     * @return list of candles
     */
    @Query("SELECT c FROM OhlcvCandleEntity c WHERE c.symbol = :symbol AND c.date BETWEEN :startDate AND :endDate ORDER BY c.date DESC")
    List<OhlcvCandleEntity> findBySymbolAndDateRange(
        @Param("symbol") String symbol,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Finds the last N candles for a stock.
     *
     * @param symbol the stock symbol
     * @param count the number of candles to retrieve
     * @return list of candles
     */
    @Query("SELECT c FROM OhlcvCandleEntity c WHERE c.symbol = :symbol ORDER BY c.date DESC")
    List<OhlcvCandleEntity> findTopBySymbolOrderByDateDesc(
        @Param("symbol") String symbol,
        Pageable pageable
    );

    /**
     * Finds all candles for a stock, ordered by date descending.
     *
     * @param symbol the stock symbol
     * @return list of candles
     */
    List<OhlcvCandleEntity> findAllBySymbolOrderByDateDesc(String symbol);

    /**
     * Finds all unique symbols that have candle data.
     *
     * @return list of unique symbols
     */
    @Query("SELECT DISTINCT c.symbol FROM OhlcvCandleEntity c ORDER BY c.symbol")
    List<String> findAllDistinctSymbols();

    /**
     * Checks if a candle exists for a stock on a specific date.
     *
     * @param symbol the stock symbol
     * @param date the date
     * @return true if the candle exists
     */
    @Query("SELECT COUNT(c) > 0 FROM OhlcvCandleEntity c WHERE c.symbol = :symbol AND c.date = :date")
    boolean existsBySymbolAndDate(
        @Param("symbol") String symbol,
        @Param("date") LocalDate date
    );

    /**
     * Finds the earliest candle for a stock.
     *
     * @param symbol the stock symbol
     * @return optional containing the earliest candle
     */
    @Query("SELECT c FROM OhlcvCandleEntity c WHERE c.symbol = :symbol ORDER BY c.date ASC LIMIT 1")
    Optional<OhlcvCandleEntity> findEarliestBySymbol(@Param("symbol") String symbol);

    /**
     * Counts the number of candles for a stock.
     *
     * @param symbol the stock symbol
     * @return the count
     */
    @Query("SELECT COUNT(c) FROM OhlcvCandleEntity c WHERE c.symbol = :symbol")
    long countBySymbol(@Param("symbol") String symbol);

    /**
     * Deletes all candles for a stock.
     *
     * @param symbol the stock symbol
     */
    void deleteBySymbol(String symbol);

    /**
     * Finds candles by date range across all symbols.
     *
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param pageable pagination
     * @return list of candles
     */
    @Query("SELECT c FROM OhlcvCandleEntity c WHERE c.date BETWEEN :startDate AND :endDate ORDER BY c.date DESC, c.symbol ASC")
    List<OhlcvCandleEntity> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Finds the latest candle for a symbol before a given date/time.
     *
     * @param symbol the trading symbol
     * @param before the cutoff date/time
     * @return optional containing the latest candle before the cutoff
     */
    @Query("SELECT c FROM OhlcvCandleEntity c WHERE c.symbol = :symbol AND c.date < :before ORDER BY c.date DESC LIMIT 1")
    Optional<OhlcvCandleEntity> findLatestBySymbolBeforeDate(
        @Param("symbol") String symbol,
        @Param("before") LocalDate before
    );

    /**
     * Finds the Nth trading day candle after a given date.
     * Useful for computing returns over evaluation windows (1d, 5d, 21d).
     *
     * @param symbol the stock symbol
     * @param after the reference date (candles must be after this date)
     * @param n the Nth trading day (1 = next trading day, 5 = 5 trading days later)
     * @return optional containing the Nth candle
     */
    @Query(value = "SELECT * FROM ohlcv_candles WHERE symbol = :symbol AND date > :after ORDER BY date ASC LIMIT 1 OFFSET :n", nativeQuery = true)
    Optional<OhlcvCandleEntity> findNthBySymbolAndDateAfterOrderByDateAsc(
        @Param("symbol") String symbol,
        @Param("after") LocalDate after,
        @Param("n") int n
    );

    /**
     * Finds the latest candle for a symbol on or after a given date.
     */
    @Query("SELECT c FROM OhlcvCandleEntity c WHERE c.symbol = :symbol AND c.date >= :date ORDER BY c.date ASC LIMIT 1")
    Optional<OhlcvCandleEntity> findFirstBySymbolAndDateAfterOrderByDateAsc(
        @Param("symbol") String symbol,
        @Param("date") LocalDate date
    );

    /**
     * Finds up to N candles for a symbol before a given date, in ascending order.
     * Used for computing SMA-200 leading up to a reference date.
     */
    @Query(value = "SELECT * FROM ohlcv_candles WHERE symbol = :symbol AND date <= :before ORDER BY date DESC LIMIT :n", nativeQuery = true)
    List<OhlcvCandleEntity> findLastNBySymbolBeforeDateAsc(
        @Param("symbol") String symbol,
        @Param("before") LocalDate before,
        @Param("n") int n
    );
}
