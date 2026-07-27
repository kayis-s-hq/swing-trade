package com.swingtrade.data.repository;

import com.swingtrade.data.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository interface for Trade operations.
 */
@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    /**
     * Finds trades by position ID.
     *
     * @param positionId the position ID
     * @return list of trades
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.positionId = :positionId ORDER BY t.entryDate DESC")
    List<TradeEntity> findByPositionId(@Param("positionId") Long positionId);

    /**
     * Finds trades by symbol.
     *
     * @param symbol the stock symbol
     * @return list of trades
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.symbol = :symbol ORDER BY t.entryDate DESC")
    List<TradeEntity> findBySymbol(@Param("symbol") String symbol);

    /**
     * Finds trades by entry date range.
     *
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of trades
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.entryDate BETWEEN :startDate AND :endDate ORDER BY t.entryDate DESC")
    List<TradeEntity> findByEntryDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Finds trades by exit date range.
     *
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of trades
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.exitDate BETWEEN :startDate AND :endDate ORDER BY t.exitDate DESC")
    List<TradeEntity> findByExitDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Finds trades by status.
     *
     * @param status the trade status
     * @return list of trades
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.tradeStatus = :status ORDER BY t.entryDate DESC")
    List<TradeEntity> findByStatus(@Param("status") String status);

    /**
     * Finds open trades.
     *
     * @return list of open trades
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.tradeStatus = 'OPEN'")
    List<TradeEntity> findAllOpenTrades();

    /**
     * Finds closed trades.
     *
     * @return list of closed trades
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.tradeStatus = 'CLOSED' OR t.tradeStatus = 'STOPPED' OR t.tradeStatus = 'TARGET_HIT'")
    List<TradeEntity> findAllClosedTrades();

    /**
     * Counts trades by symbol.
     *
     * @param symbol the stock symbol
     * @return the count
     */
    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.symbol = :symbol")
    long countBySymbol(@Param("symbol") String symbol);

    /**
     * Counts open trades.
     *
     * @return the count of open trades
     */
    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.tradeStatus = 'OPEN'")
    long countOpenTrades();
}
