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
    List<PositionEntity> findBySymbolOrderByEntryDateDesc(String symbol);

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
}
