package com.swingtrade.data.repository;

import com.swingtrade.data.entity.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PositionEntity operations.
 */
@Repository
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    /**
     * Finds all open positions.
     *
     * @return list of open positions
     */
    @Query("SELECT p FROM PositionEntity p WHERE p.status = 'OPEN' ORDER BY p.entryDate DESC")
    List<PositionEntity> findAllOpenPositions();

    /**
     * Finds open positions as lightweight summaries, without loading full entities.
     * Uses an interface projection (aliases map to {@link PositionSummaryProjection}
     * properties) so the String {@code status}/{@code direction} columns are not forced
     * into enum types inside JPQL.
     *
     * @return open position summaries, most recently entered first
     */
    @Query("SELECT p.id AS id, p.symbol AS symbol, p.status AS status, p.direction AS direction, "
        + "p.entryPrice AS entryPrice, p.quantity AS quantity, p.currentPrice AS currentPrice, "
        + "p.unrealizedPnL AS unrealizedPnL, p.stopLoss AS stopLoss, p.target AS target, "
        + "p.brokerType AS brokerType "
        + "FROM PositionEntity p WHERE p.status = 'OPEN' ORDER BY p.entryDate DESC")
    List<PositionSummaryProjection> findOpenSummaries();

    /**
     * Finds an open position by symbol.
     *
     * @param symbol the stock symbol
     * @return optional containing the open position
     */
    @Query("SELECT p FROM PositionEntity p WHERE p.symbol = :symbol AND p.status = 'OPEN'")
    Optional<PositionEntity> findOpenBySymbol(@Param("symbol") String symbol);

    /**
     * Finds positions by symbol regardless of status.
     *
     * @param symbol the stock symbol
     * @return list of positions
     */
    @Query("SELECT p FROM PositionEntity p WHERE p.symbol = :symbol ORDER BY p.entryDate DESC")
    List<PositionEntity> findBySymbolOrderByEntryDateDesc(@Param("symbol") String symbol);

    /**
     * Counts open positions.
     *
     * @return the count of open positions
     */
    @Query("SELECT COUNT(p) FROM PositionEntity p WHERE p.status = 'OPEN'")
    long countOpenPositions();

    /**
     * Finds positions to be stopped based on current prices.
     *
     * @return list of positions that need stop loss checks
     */
    @Query("SELECT p FROM PositionEntity p WHERE p.status = 'OPEN'")
    List<PositionEntity> findAllPositionsForStopLossCheck();

    /**
     * Checks if an open position exists for a symbol.
     *
     * @param symbol the stock symbol
     * @return true if an open position exists
     */
    @Query("SELECT COUNT(p) > 0 FROM PositionEntity p WHERE p.symbol = :symbol AND p.status = 'OPEN'")
    boolean existsOpenBySymbol(@Param("symbol") String symbol);

    /**
     * Finds all positions that have hit their target.
     *
     * @return list of positions with target hit
     */
    @Query("SELECT p FROM PositionEntity p WHERE p.status = 'TARGET_HIT' ORDER BY p.updatedAt DESC")
    List<PositionEntity> findAllTargetHitPositions();

    /**
     * Finds all stopped positions.
     *
     * @return list of stopped positions
     */
    @Query("SELECT p FROM PositionEntity p WHERE p.status = 'STOPPED' ORDER BY p.updatedAt DESC")
    List<PositionEntity> findAllStoppedPositions();

    /**
     * Finds positions by status.
     *
     * @param status the position status
     * @return list of positions with the specified status
     */
    @Query("SELECT p FROM PositionEntity p WHERE p.status = :status ORDER BY p.entryDate DESC")
    List<PositionEntity> findByStatus(@Param("status") String status);

    /**
     * Finds positions by symbol.
     *
     * @param symbol the stock symbol
     * @return list of positions
     */
    List<PositionEntity> findBySymbol(String symbol);

    @Query("SELECT p FROM PositionEntity p WHERE p.signalId = :signalId AND p.status <> 'OPEN'")
    List<PositionEntity> findClosedBySignalId(@Param("signalId") Long signalId);

    @Query("SELECT p FROM PositionEntity p WHERE p.brokerType = :brokerType ORDER BY p.entryDate DESC")
    List<PositionEntity> findByBrokerType(@Param("brokerType") String brokerType);

    Optional<PositionEntity> findByPositionId(String positionId);

    /**
     * Finds all non-null position ID strings across every status and broker
     * type. Used on startup to reseed in-memory position ID counters (e.g.
     * {@code PaperTradingEngine}'s POS_ sequence) from the historical
     * database maximum, so that newly generated IDs never collide with an
     * existing (open OR closed) row after a restart.
     *
     * @return list of position ID strings
     */
    @Query("SELECT p.positionId FROM PositionEntity p WHERE p.positionId IS NOT NULL")
    List<String> findAllPositionIds();

    /** Open positions for a given strategy variant's own paper-trading portfolio. */
    @Query("SELECT p FROM PositionEntity p WHERE p.portfolioId = :portfolioId "
        + "AND p.symbol = :symbol AND p.status = 'OPEN'")
    List<PositionEntity> findOpenByPortfolioIdAndSymbol(@Param("portfolioId") String portfolioId,
                                                         @Param("symbol") String symbol);

    /** All closed/stopped/target-hit trades for a given strategy variant's portfolio. */
    @Query("SELECT p FROM PositionEntity p WHERE p.portfolioId = :portfolioId "
        + "AND p.status <> 'OPEN' ORDER BY p.entryDate DESC")
    List<PositionEntity> findClosedByPortfolioId(@Param("portfolioId") String portfolioId);

    @Query("SELECT COUNT(p) FROM PositionEntity p WHERE p.portfolioId = :portfolioId AND p.status = 'OPEN'")
    long countOpenByPortfolioId(@Param("portfolioId") String portfolioId);

    /** Every position (any status) in a portfolio, most recently entered first. */
    List<PositionEntity> findByPortfolioIdOrderByEntryDateDescIdDesc(String portfolioId);

    /** Every portfolio-tagged position (i.e. not a real "default"-book position) on a symbol. */
    List<PositionEntity> findBySymbolAndPortfolioIdIsNotNullOrderByEntryDateDescIdDesc(String symbol);
}
