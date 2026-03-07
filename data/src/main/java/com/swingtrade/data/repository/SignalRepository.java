package com.swingtrade.data.repository;

import com.swingtrade.data.entity.SignalEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SignalEntity operations.
 */
@Repository
public interface SignalRepository extends JpaRepository<SignalEntity, Long> {

    /**
     * Finds the latest signals for a stock.
     *
     * @param symbol the stock symbol
     * @param pageable pagination
     * @return list of signals
     */
    @Query("SELECT s FROM SignalEntity s WHERE s.symbol = :symbol ORDER BY s.date DESC, s.createdAt DESC")
    List<SignalEntity> findBySymbolOrderByDateDesc(
        @Param("symbol") String symbol,
        Pageable pageable
    );

    /**
     * Finds all signals for a stock on a specific date.
     *
     * @param symbol the stock symbol
     * @param date the date
     * @return list of signals
     */
    List<SignalEntity> findBySymbolAndDate(
        @Param("symbol") String symbol,
        @Param("date") LocalDate date
    );

    /**
     * Finds all BUY signals generated on or after a date.
     *
     * @param date the date
     * @return list of BUY signals
     */
    @Query("SELECT s FROM SignalEntity s WHERE s.signalType = 'BUY' AND s.date >= :date ORDER BY s.date DESC, s.createdAt DESC")
    List<SignalEntity> findBuySignalsSince(
        @Param("date") LocalDate date,
        Pageable pageable
    );

    /**
     * Finds signals by date range and signal type.
     *
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param signalType the signal type (BUY, SELL, HOLD)
     * @param pageable pagination
     * @return list of signals
     */
    @Query("SELECT s FROM SignalEntity s WHERE s.date BETWEEN :startDate AND :endDate AND s.signalType = :signalType ORDER BY s.date DESC")
    List<SignalEntity> findByDateRangeAndSignalType(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("signalType") String signalType,
        Pageable pageable
    );

    /**
     * Finds the latest signal for a stock.
     *
     * @param symbol the stock symbol
     * @return optional containing the latest signal
     */
    @Query("SELECT s FROM SignalEntity s WHERE s.symbol = :symbol ORDER BY s.date DESC LIMIT 1")
    Optional<SignalEntity> findLatestBySymbol(@Param("symbol") String symbol);

    /**
     * Counts signals by date and symbol.
     *
     * @param symbol the stock symbol
     * @param date the date
     * @return the count
     */
    @Query("SELECT COUNT(s) FROM SignalEntity s WHERE s.symbol = :symbol AND s.date = :date")
    long countBySymbolAndDate(
        @Param("symbol") String symbol,
        @Param("date") LocalDate date
    );

    /**
     * Deletes signals older than a specific date.
     *
     * @param cutoffDate the cutoff date
     * @return number of deleted signals
     */
    @Query("DELETE FROM SignalEntity s WHERE s.date < :cutoffDate")
    int deleteSignalsBefore(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Finds all distinct symbols with signals.
     *
     * @return list of distinct symbols
     */
    @Query("SELECT DISTINCT s.symbol FROM SignalEntity s ORDER BY s.symbol")
    List<String> findAllDistinctSymbols();
}
