package com.swingtrade.broker.manager;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.OhlcvCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of trading positions including entry, partial exit, full exit,
 * stop loss triggers, target hits, and P&L tracking.
 */
@Component
public class PositionManager {

    private static final Logger logger = LoggerFactory.getLogger(PositionManager.class);

    // Position storage
    private final Map<String, Position> positions;

    // Configuration
    private static final BigDecimal PARTIAL_EXIT_RATIO = BigDecimal.valueOf(0.5); // Exit 50% at partial
    private final int maxPositions;

    /**
     * Creates a new PositionManager with empty position storage.
     * Max positions is read from PaperTradingProperties.
     */
    public PositionManager(PaperTradingProperties properties) {
        this.positions = new ConcurrentHashMap<>();
        this.maxPositions = properties.getMaxConcurrentPositions();
    }

    /**
     * Creates a new position based on a trading signal.
     */
    public Position createPosition(
        String positionId,
        String symbol,
        TradeDirection direction,
        Integer quantity,
        BigDecimal entryPrice,
        BigDecimal atr,
        String entryReason
    ) {
        BigDecimal stopLoss = calculateStopLoss(entryPrice, direction, atr);
        BigDecimal target = calculateTarget(entryPrice, stopLoss, direction);

        Position position = Position.openPaper(
            positionId,
            symbol,
            direction,
            quantity,
            entryPrice,
            stopLoss,
            target,
            entryReason,
            LocalDate.now(),
            LocalDateTime.now()
        );

        positions.put(positionId, position);

        logger.info("Created position {} for {} at {} (SL: {}, Target: {}, Qty: {})",
            positionId, symbol, entryPrice, stopLoss, target, quantity);

        return position;
    }

    /**
     * Calculates the stop loss price based on entry price and ATR.
     */
    public BigDecimal calculateStopLoss(BigDecimal entryPrice, TradeDirection direction, BigDecimal atr) {
        if (atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal fallbackSl = entryPrice.multiply(BigDecimal.valueOf(0.97));
            logger.warn("ATR not available, using 3% fallback stop loss for {}", entryPrice);
            return fallbackSl;
        }

        if (direction == TradeDirection.LONG) {
            return entryPrice.subtract(atr.multiply(BigDecimal.valueOf(2)));
        } else {
            return entryPrice.add(atr.multiply(BigDecimal.valueOf(2)));
        }
    }

    /**
     * Calculates the target price with a 2.5:1 reward-to-risk ratio.
     */
    public BigDecimal calculateTarget(BigDecimal entryPrice, BigDecimal stopLoss, TradeDirection direction) {
        BigDecimal risk;
        if (direction == TradeDirection.LONG) {
            risk = entryPrice.subtract(stopLoss);
        } else {
            risk = stopLoss.subtract(entryPrice);
        }

        BigDecimal rewardRatio = BigDecimal.valueOf(2.5);
        if (direction == TradeDirection.LONG) {
            return entryPrice.add(risk.multiply(rewardRatio));
        } else {
            return entryPrice.subtract(risk.multiply(rewardRatio));
        }
    }

    /**
     * Updates the current price for a position and returns a new Position with updated P&L.
     */
    public Position updatePositionPrice(String positionId, BigDecimal currentPrice) {
        Position position = getPosition(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        // Recalculate unrealized P&L based on new current price
        BigDecimal unrealizedPnL;
        if (position.direction() == TradeDirection.LONG) {
            unrealizedPnL = currentPrice.subtract(position.entryPrice()).multiply(BigDecimal.valueOf(position.quantity()));
        } else {
            unrealizedPnL = position.entryPrice().subtract(currentPrice).multiply(BigDecimal.valueOf(position.quantity()));
        }

        Position updated = position.withValuation(currentPrice, unrealizedPnL);

        positions.put(positionId, updated);
        return updated;
    }

    /**
     * Updates positions based on new candle data.
     */
    public List<Position> updatePositionsWithCandleData(String symbol, OhlcvCandle candleData) {
        List<Position> updatedPositions = new ArrayList<>();

        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            Position position = entry.getValue();

            if (position.symbol().equals(symbol) && position.status() == PositionStatus.OPEN) {
                Position updated = updatePositionPrice(entry.getKey(), candleData.close());
                checkPositionTriggers(updated, candleData);

                // Re-read from the map: checkPositionTriggers may have replaced this
                // entry with a closed instance (stop-loss/target hit). Callers branch
                // on status(), so they must see the final state, not the pre-trigger one.
                updatedPositions.add(positions.get(entry.getKey()));
            }
        }

        return updatedPositions;
    }

    /**
     * Checks if position has hit stop loss or target levels based on candle data.
     */
    public void checkPositionTriggers(Position position, OhlcvCandle candleData) {
        if (position.status() != PositionStatus.OPEN) {
            return;
        }

        BigDecimal low = candleData.low();
        BigDecimal high = candleData.high();
        BigDecimal stopLoss = position.stopLoss();
        BigDecimal target = position.target();
        TradeDirection direction = position.direction();

        if (direction == TradeDirection.LONG) {
            if (low.compareTo(stopLoss) <= 0) {
                BigDecimal fill = candleData.open().compareTo(stopLoss) <= 0 ? candleData.open() : stopLoss;
                closePosition(position.positionId(), PositionStatus.STOPPED, "Stop Loss Hit - Price dropped to " + low, fill);
            } else if (high.compareTo(target) >= 0) {
                BigDecimal fill = candleData.open().compareTo(target) >= 0 ? candleData.open() : target;
                closePosition(position.positionId(), PositionStatus.TARGET_HIT, "Target Hit - Price rose to " + high, fill);
            }
        } else {
            if (high.compareTo(stopLoss) >= 0) {
                BigDecimal fill = candleData.open().compareTo(stopLoss) >= 0 ? candleData.open() : stopLoss;
                closePosition(position.positionId(), PositionStatus.STOPPED, "Stop Loss Hit - Price rose to " + high, fill);
            } else if (low.compareTo(target) <= 0) {
                BigDecimal fill = candleData.open().compareTo(target) <= 0 ? candleData.open() : target;
                closePosition(position.positionId(), PositionStatus.TARGET_HIT, "Target Hit - Price dropped to " + low, fill);
            }
        }
    }

    /**
     * Calculates the unrealized P&L for a position.
     */
    public BigDecimal calculatePositionPnL(Position position, BigDecimal currentPrice) {
        if (position.entryPrice() == null || position.entryPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceDifference;
        if (position.direction() == TradeDirection.LONG) {
            priceDifference = currentPrice.subtract(position.entryPrice());
        } else {
            priceDifference = position.entryPrice().subtract(currentPrice);
        }

        return priceDifference.multiply(BigDecimal.valueOf(position.quantity()));
    }

    /**
     * Calculates the percentage P&L for a position.
     */
    public BigDecimal calculatePnLPercentage(Position position, BigDecimal currentPrice) {
        if (position.entryPrice() == null || position.entryPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceDifference;
        if (position.direction() == TradeDirection.LONG) {
            priceDifference = currentPrice.subtract(position.entryPrice());
        } else {
            priceDifference = position.entryPrice().subtract(currentPrice);
        }

        BigDecimal percentage = priceDifference.divide(position.entryPrice(), 4, BigDecimal.ROUND_HALF_UP);
        return percentage.multiply(BigDecimal.valueOf(100));
    }

    /**
     * Executes a partial exit of the position (e.g., 50% at first target).
     */
    public Position partialExitPosition(String positionId, BigDecimal exitRatio, BigDecimal exitPrice) {
        Position position = getPosition(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        if (position.status() != PositionStatus.OPEN) {
            throw new IllegalStateException("Position is not open: " + positionId);
        }

        if (exitRatio.compareTo(BigDecimal.ZERO) <= 0 || exitRatio.compareTo(BigDecimal.ONE) > 1) {
            throw new IllegalArgumentException("Exit ratio must be between 0 and 1: " + exitRatio);
        }

        Integer originalQuantity = position.quantity();
        BigDecimal exitQuantity = BigDecimal.valueOf(originalQuantity).multiply(exitRatio);
        Integer remainingQuantity = exitQuantity.setScale(0, BigDecimal.ROUND_DOWN).intValue();

        // Calculate realized P&L from partial exit
        BigDecimal realizedPnL = calculatePartialExitPnL(position, exitQuantity, exitPrice);
        BigDecimal currentUnrealized = position.unrealizedPnL();

        // Update quantity and track realized P&L
        Position updated = position.withQuantityAndRealizedPnL(
            remainingQuantity,
            position.realizedPnL().add(realizedPnL)
        ).withValuation(position.currentPrice(), currentUnrealized);

        if (remainingQuantity == 0) {
            // Full exit
            updated = updated.close(
                PositionStatus.CLOSED,
                updated.realizedPnL(),
                LocalDateTime.now(),
                "Position fully exited at " + exitPrice,
                null
            );
            positions.put(positionId, updated);
            logger.info("Closed position {} (full exit) at {} - Realized P&L: {}",
                positionId, exitPrice, realizedPnL);
        } else {
            positions.put(positionId, updated);
            logger.info("Partial exit of position {}: sold {} at {}, remaining {} (Exit P&L: {})",
                positionId, exitQuantity, exitPrice, remainingQuantity, realizedPnL);
        }

        return updated;
    }

    /**
     * Calculates the P&L from a partial exit.
     */
    private BigDecimal calculatePartialExitPnL(Position position, BigDecimal exitQuantity, BigDecimal exitPrice) {
        BigDecimal priceDifference;
        if (position.direction() == TradeDirection.LONG) {
            priceDifference = exitPrice.subtract(position.entryPrice());
        } else {
            priceDifference = position.entryPrice().subtract(exitPrice);
        }

        return priceDifference.multiply(exitQuantity);
    }

    /**
     * Closes a position completely. Returns a new Position instance.
     *
     * <p>Intentionally NOT {@code @Transactional}: this class is a pure
     * in-memory store (a {@link ConcurrentHashMap}) with no JPA/DB
     * participation, so annotating it provides no transactional guarantee —
     * it only risks marking a caller's ambient transaction rollback-only if a
     * lookup here fails (e.g. an unresolved positionId), even when the caller
     * catches and handles the failure gracefully.
     */
    public Position closePosition(String positionId, BigDecimal exitPrice, String reason) {
        Position position = getPosition(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        return closePosition(position, PositionStatus.CLOSED, reason, exitPrice);
    }

    /**
     * Closes a position by ID with a specific status (STOPPED, TARGET_HIT, etc.),
     * booking realized P&amp;L at the given exit price.
     *
     * <p>Intentionally NOT {@code @Transactional} — see
     * {@link #closePosition(String, BigDecimal, String)} for rationale.
     */
    public Position closePosition(String positionId, PositionStatus status, String reason, BigDecimal exitPrice) {
        Position position = getPosition(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }
        return closePosition(position, status, reason, exitPrice);
    }

    /**
     * Closes a position by ID with a specific status, booking realized P&amp;L at
     * the position's last-known current price. Prefer
     * {@link #closePosition(String, PositionStatus, String, BigDecimal)} when the
     * actual fill/trigger price is known.
     *
     * <p>Intentionally NOT {@code @Transactional} — see
     * {@link #closePosition(String, BigDecimal, String)} for rationale.
     */
    public Position closePosition(String positionId, PositionStatus status, String reason) {
        Position position = getPosition(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }
        return closePosition(position, status, reason, position.currentPrice());
    }

    /**
     * Closes a position with a specific status at the position's current price.
     * Prefer {@link #closePosition(Position, PositionStatus, String, BigDecimal)}
     * when the actual fill/trigger price is known.
     *
     * <p>Intentionally NOT {@code @Transactional} — see
     * {@link #closePosition(String, BigDecimal, String)} for rationale.
     */
    Position closePosition(Position position, PositionStatus status, String reason) {
        return closePosition(position, status, reason, position.currentPrice());
    }

    /**
     * Internal method to close a position with specific status at a given exit
     * price. Returns a new Position.
     *
     * <p>Intentionally NOT {@code @Transactional} — see
     * {@link #closePosition(String, BigDecimal, String)} for rationale.
     */
    Position closePosition(Position position, PositionStatus status, String reason, BigDecimal exitPrice) {
        if (position.status() == PositionStatus.CLOSED ||
            position.status() == PositionStatus.STOPPED ||
            position.status() == PositionStatus.TARGET_HIT) {
            logger.warn("Position {} already closed with status {}", position.positionId(), position.status());
            return position;
        }

        BigDecimal realizedPnL = calculatePositionPnL(position, exitPrice);

        // Refresh the valuation's currentPrice to the actual exit price before closing.
        // Position.close() carries forward whatever currentPrice() already holds, which
        // is only refreshed by the EOD mark-to-market cron - so without this, a
        // signal-triggered close persists the stale (possibly never-updated entry) price
        // instead of the real market price that triggered the exit.
        Position priced = position.withValuation(exitPrice, position.calculateUnrealizedPnL(exitPrice));
        Position updated = priced.close(status, realizedPnL, LocalDateTime.now(), reason, position.target());

        positions.put(position.positionId(), updated);

        logger.info("Closed position {} with status {} at {} - Reason: {} | P&L: {} ({}%)",
            position.positionId(), status, exitPrice, reason, realizedPnL,
            calculatePnLPercentage(updated, exitPrice));

        return updated;
    }

    /**
     * Gets a position by ID.
     * Returns null (instead of throwing) for a null/blank ID, since
     * ConcurrentHashMap does not permit null keys and callers may pass
     * an unresolved positionId (e.g. a DB-backed position that was never
     * linked to the in-memory engine).
     */
    public Position getPosition(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        return positions.get(positionId);
    }

    /**
     * Gets the internal position map. Used by state persistence layer.
     */
    public Map<String, Position> getPositions() {
        return positions;
    }

    /**
     * Gets all open positions.
     */
    public List<Position> getOpenPositions() {
        return positions.values().stream()
            .filter(p -> p.status() == PositionStatus.OPEN)
            .toList();
    }

    /**
     * Gets all closed positions.
     */
    public List<Position> getClosedPositions() {
        return positions.values().stream()
            .filter(p -> p.status() == PositionStatus.CLOSED ||
                        p.status() == PositionStatus.STOPPED ||
                        p.status() == PositionStatus.TARGET_HIT)
            .toList();
    }

    /**
     * Gets all positions (open and closed).
     */
    public List<Position> getAllPositions() {
        return new ArrayList<>(positions.values());
    }

    /**
     * Checks if a symbol has an open position.
     */
    public Position findOpenPositionBySymbol(String symbol) {
        return positions.values().stream()
            .filter(p -> p.symbol().equals(symbol) && p.status() == PositionStatus.OPEN)
            .findFirst()
            .orElse(null);
    }

    public boolean hasOpenPosition(String symbol) {
        return positions.values().stream()
            .anyMatch(p -> p.symbol().equals(symbol) && p.status() == PositionStatus.OPEN);
    }

    /**
     * Gets the number of open positions.
     */
    public int getOpenPositionCount() {
        return getOpenPositions().size();
    }

    /**
     * Checks if position limit has been reached.
     */
    public boolean hasReachedPositionLimit() {
        return getOpenPositionCount() >= maxPositions;
    }

    /**
     * Gets the maximum number of concurrent positions allowed.
     */
    public int getMaxPositions() {
        return maxPositions;
    }

    /**
     * Removes a position from management (for cleanup after finalization).
     */
    public boolean removePosition(String positionId) {
        return positions.remove(positionId) != null;
    }

    /**
     * Clears all positions (for testing purposes).
     */
    public void clearAllPositions() {
        positions.clear();
    }

    /**
     * Calculates the total unrealized P&L across all open positions.
     */
    public BigDecimal getTotalUnrealizedPnL() {
        return getOpenPositions().stream()
            .map(Position::unrealizedPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates the total realized P&L across all closed positions.
     */
    public BigDecimal getTotalRealizedPnL() {
        return getClosedPositions().stream()
            .map(Position::realizedPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
