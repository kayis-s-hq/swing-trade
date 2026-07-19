package com.swingtrade.broker.manager;

import com.swingtrade.broker.model.Position;
import com.swingtrade.broker.model.PositionStatus;
import com.swingtrade.broker.model.TradeDirection;
import com.swingtrade.domain.OhlcvCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private static final int MAX_POSITIONS = 10;

    /**
     * Creates a new PositionManager with empty position storage.
     */
    public PositionManager() {
        this.positions = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new position based on a trading signal.
     *
     * @param positionId unique identifier for the position
     * @param symbol trading symbol (e.g., "RELIANCE", "TCS")
     * @param direction trade direction (LONG or SHORT)
     * @param quantity number of shares
     * @param entryPrice entry price
     * @param atr Average True Range for dynamic stop loss calculation
     * @param entryReason reason for entering the position
     * @return the created position
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
        // Calculate stop loss based on ATR (2x ATR below entry for long)
        BigDecimal stopLoss = calculateStopLoss(entryPrice, direction, atr);

        // Calculate target with 2.5:1 reward-to-risk ratio
        BigDecimal target = calculateTarget(entryPrice, stopLoss, direction);

        // Create new position
        Position position = new Position();
        position.setPositionId(positionId);
        position.setSymbol(symbol);
        position.setDirection(direction);
        position.setQuantity(BigDecimal.valueOf(quantity));
        position.setEntryPrice(entryPrice);
        position.setSlPrice(stopLoss);
        position.setTargetPrice(target);
        position.setStatus(PositionStatus.OPEN);
        position.setEntryTime(LocalDateTime.now());
        position.setCurrentPrice(entryPrice);
        position.setOrders(new ArrayList<>());
        position.setProfitLoss(BigDecimal.ZERO);
        position.setEntryReason(entryReason);

        // Store the position
        positions.put(positionId, position);

        logger.info("Created position {} for {} at {} (SL: {}, Target: {}, Qty: {})",
            positionId, symbol, entryPrice, stopLoss, target, quantity);

        return position;
    }

    /**
     * Calculates the stop loss price based on entry price and ATR.
     * Uses 2x ATR for dynamic risk-based stop loss.
     *
     * @param entryPrice the entry price
     * @param direction the trade direction
     * @param atr the Average True Range
     * @return the calculated stop loss price
     */
    public BigDecimal calculateStopLoss(BigDecimal entryPrice, TradeDirection direction, BigDecimal atr) {
        if (atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) {
            // Fallback: 3% stop loss if ATR is not available
            BigDecimal fallbackSl = entryPrice.multiply(BigDecimal.valueOf(0.97));
            logger.warn("ATR not available, using 3% fallback stop loss for {}", entryPrice);
            return fallbackSl;
        }

        if (direction == TradeDirection.LONG) {
            // Stop loss is below entry price
            return entryPrice.subtract(atr.multiply(BigDecimal.valueOf(2)));
        } else {
            // For short positions, stop loss is above entry price
            return entryPrice.add(atr.multiply(BigDecimal.valueOf(2)));
        }
    }

    /**
     * Calculates the target price with a 2.5:1 reward-to-risk ratio.
     *
     * @param entryPrice the entry price
     * @param stopLoss the calculated stop loss price
     * @param direction the trade direction
     * @return the calculated target price
     */
    public BigDecimal calculateTarget(BigDecimal entryPrice, BigDecimal stopLoss, TradeDirection direction) {
        // Calculate risk (distance from entry to stop loss)
        BigDecimal risk;
        if (direction == TradeDirection.LONG) {
            risk = entryPrice.subtract(stopLoss);
        } else {
            risk = stopLoss.subtract(entryPrice);
        }

        // Target is entry + (2.5 * risk) for long, entry - (2.5 * risk) for short
        BigDecimal rewardRatio = BigDecimal.valueOf(2.5);
        if (direction == TradeDirection.LONG) {
            return entryPrice.add(risk.multiply(rewardRatio));
        } else {
            return entryPrice.subtract(risk.multiply(rewardRatio));
        }
    }

    /**
     * Updates the current price for a position and recalculates P&L.
     *
     * @param positionId the ID of the position to update
     * @param currentPrice the current market price
     * @return the updated position
     */
    public Position updatePositionPrice(String positionId, BigDecimal currentPrice) {
        Position position = positions.get(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        position.setCurrentPrice(currentPrice);
        position.setProfitLoss(calculatePositionPnL(position));

        return position;
    }

    /**
     * Updates positions based on new candle data.
     *
     * @param symbol the trading symbol
     * @param candleData the candle data with OHLC prices
     * @return list of updated positions
     */
    public List<Position> updatePositionsWithCandleData(String symbol, OhlcvCandle candleData) {
        List<Position> updatedPositions = new ArrayList<>();

        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            Position position = entry.getValue();

            if (position.getSymbol().equals(symbol) && position.getStatus() == PositionStatus.OPEN) {
                position.setCurrentPrice(candleData.close());
                position.setProfitLoss(calculatePositionPnL(position));
                updatedPositions.add(position);

                // Check for SL/TP triggers
                checkPositionTriggers(position, candleData);
            }
        }

        return updatedPositions;
    }

    /**
     * Checks if position has hit stop loss or target levels based on candle data.
     *
     * @param position the position to check
     * @param candleData the candle data with OHLC prices
     */
    public void checkPositionTriggers(Position position, OhlcvCandle candleData) {
        if (position.getStatus() != PositionStatus.OPEN) {
            return;
        }

        BigDecimal low = candleData.low();
        BigDecimal high = candleData.high();
        BigDecimal stopLoss = position.getSlPrice();
        BigDecimal target = position.getTargetPrice();
        TradeDirection direction = position.getDirection();

        if (direction == TradeDirection.LONG) {
            // Long position: price drops below SL or rises above target
            if (low.compareTo(stopLoss) <= 0) {
                closePosition(position, PositionStatus.STOPPED, "Stop Loss Hit - Price dropped to " + low);
            } else if (high.compareTo(target) >= 0) {
                closePosition(position, PositionStatus.TARGET_HIT, "Target Hit - Price rose to " + high);
            }
        } else {
            // Short position: price rises above SL or drops below target
            if (high.compareTo(stopLoss) >= 0) {
                closePosition(position, PositionStatus.STOPPED, "Stop Loss Hit - Price rose to " + high);
            } else if (low.compareTo(target) <= 0) {
                closePosition(position, PositionStatus.TARGET_HIT, "Target Hit - Price dropped to " + low);
            }
        }
    }

    /**
     * Calculates the unrealized P&L for a position.
     *
     * @param position the position to calculate P&L for
     * @return the calculated P&L (positive for profit, negative for loss)
     */
    public BigDecimal calculatePositionPnL(Position position) {
        if (position.getEntryPrice() == null || position.getEntryPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceDifference;
        if (position.getDirection() == TradeDirection.LONG) {
            priceDifference = position.getCurrentPrice().subtract(position.getEntryPrice());
        } else {
            priceDifference = position.getEntryPrice().subtract(position.getCurrentPrice());
        }

        return priceDifference.multiply(position.getQuantity());
    }

    /**
     * Calculates the percentage P&L for a position.
     *
     * @param position the position to calculate P&L % for
     * @return the P&L as a percentage
     */
    public BigDecimal calculatePnLPercentage(Position position) {
        if (position.getEntryPrice() == null || position.getEntryPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceDifference;
        if (position.getDirection() == TradeDirection.LONG) {
            priceDifference = position.getCurrentPrice().subtract(position.getEntryPrice());
        } else {
            priceDifference = position.getEntryPrice().subtract(position.getCurrentPrice());
        }

        BigDecimal percentage = priceDifference.divide(position.getEntryPrice(), 4, BigDecimal.ROUND_HALF_UP);
        return percentage.multiply(BigDecimal.valueOf(100));
    }

    /**
     * Executes a partial exit of the position (e.g., 50% at first target).
     *
     * @param positionId the ID of the position to partially exit
     * @param exitRatio the ratio to exit (0.0 to 1.0, where 1.0 is full exit)
     * @param exitPrice the price at which to exit
     * @return the partially exited position
     */
    public Position partialExitPosition(String positionId, BigDecimal exitRatio, BigDecimal exitPrice) {
        Position position = positions.get(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        if (position.getStatus() != PositionStatus.OPEN) {
            throw new IllegalStateException("Position is not open: " + positionId);
        }

        if (exitRatio.compareTo(BigDecimal.ZERO) <= 0 || exitRatio.compareTo(BigDecimal.ONE) > 1) {
            throw new IllegalArgumentException("Exit ratio must be between 0 and 1: " + exitRatio);
        }

        BigDecimal originalQuantity = position.getQuantity();
        BigDecimal exitQuantity = originalQuantity.multiply(exitRatio);
        BigDecimal remainingQuantity = originalQuantity.subtract(exitQuantity);

        // Calculate realized P&L from partial exit
        BigDecimal realizedPnL = calculatePartialExitPnL(position, exitQuantity, exitPrice);
        position.setProfitLoss(realizedPnL);

        // Update quantity
        position.setQuantity(remainingQuantity);

        // For partially exited positions, reduce target proportionally
        BigDecimal originalTarget = position.getTargetPrice();
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            // Full exit - no more target needed
            position.setTargetPrice(null);
            closePosition(position, PositionStatus.CLOSED, "Position fully exited at " + exitPrice);
        } else {
            // Partial exit - maintain remaining target
            position.setTargetPrice(originalTarget);
            logger.info("Partial exit of position {}: sold {} at {}, remaining {} (Exit P&L: {})",
                positionId, exitQuantity, exitPrice, remainingQuantity, realizedPnL);
        }

        return position;
    }

    /**
     * Calculates the P&L from a partial exit.
     *
     * @param position the position being exited
     * @param exitQuantity the quantity being exited
     * @param exitPrice the exit price
     * @return the realized P&L
     */
    private BigDecimal calculatePartialExitPnL(Position position, BigDecimal exitQuantity, BigDecimal exitPrice) {
        BigDecimal priceDifference;
        if (position.getDirection() == TradeDirection.LONG) {
            priceDifference = exitPrice.subtract(position.getEntryPrice());
        } else {
            priceDifference = position.getEntryPrice().subtract(exitPrice);
        }

        return priceDifference.multiply(exitQuantity);
    }

    /**
     * Closes a position completely.
     *
     * @param positionId the ID of the position to close
     * @param exitPrice the price at which to close
     * @param reason the reason for closing
     * @return the closed position
     */
    public Position closePosition(String positionId, BigDecimal exitPrice, String reason) {
        Position position = positions.get(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        return closePosition(position, PositionStatus.CLOSED, reason);
    }

    /**
     * Internal method to close a position with specific status.
     *
     * @param position the position to close
     * @param status the final status
     * @param reason the reason for closing
     * @return the closed position
     */
    Position closePosition(Position position, PositionStatus status, String reason) {
        if (position.getStatus() == PositionStatus.CLOSED ||
            position.getStatus() == PositionStatus.STOPPED ||
            position.getStatus() == PositionStatus.TARGET_HIT) {
            logger.warn("Position {} already closed with status {}", position.getPositionId(), position.getStatus());
            return position;
        }

        BigDecimal exitPrice = position.getCurrentPrice();
        BigDecimal realizedPnL = calculatePositionPnL(position);

        // Update position
        position.setStatus(status);
        position.setExitTime(LocalDateTime.now());
        position.setProfitLoss(realizedPnL);

        logger.info("Closed position {} with status {} at {} - Reason: {} | P&L: {} ({}%)",
            position.getPositionId(), status, exitPrice, reason, realizedPnL,
            calculatePnLPercentage(position));

        return position;
    }

    /**
     * Gets a position by ID.
     *
     * @param positionId the ID of the position
     * @return the position if found, null otherwise
     */
    public Position getPosition(String positionId) {
        return positions.get(positionId);
    }

    /**
     * Gets all open positions.
     *
     * @return list of open positions
     */
    public List<Position> getOpenPositions() {
        return positions.values().stream()
            .filter(p -> p.getStatus() == PositionStatus.OPEN)
            .toList();
    }

    /**
     * Gets all closed positions.
     *
     * @return list of closed positions
     */
    public List<Position> getClosedPositions() {
        return positions.values().stream()
            .filter(p -> p.getStatus() == PositionStatus.CLOSED ||
                        p.getStatus() == PositionStatus.STOPPED ||
                        p.getStatus() == PositionStatus.TARGET_HIT)
            .toList();
    }

    /**
     * Gets all positions (open and closed).
     *
     * @return list of all positions
     */
    public List<Position> getAllPositions() {
        return new ArrayList<>(positions.values());
    }

    /**
     * Checks if a symbol has an open position.
     *
     * @param symbol the trading symbol
     * @return true if position exists and is open
     */
    public Position findOpenPositionBySymbol(String symbol) {
        return positions.values().stream()
            .filter(p -> p.getSymbol().equals(symbol) && p.getStatus() == PositionStatus.OPEN)
            .findFirst()
            .orElse(null);
    }

    public boolean hasOpenPosition(String symbol) {
        return positions.values().stream()
            .anyMatch(p -> p.getSymbol().equals(symbol) && p.getStatus() == PositionStatus.OPEN);
    }

    /**
     * Gets the number of open positions.
     *
     * @return count of open positions
     */
    public int getOpenPositionCount() {
        return getOpenPositions().size();
    }

    /**
     * Checks if position limit has been reached.
     *
     * @return true if maximum positions limit has been reached
     */
    public boolean hasReachedPositionLimit() {
        return getOpenPositionCount() >= MAX_POSITIONS;
    }

    /**
     * Gets the maximum number of concurrent positions allowed.
     *
     * @return maximum positions limit
     */
    public int getMaxPositions() {
        return MAX_POSITIONS;
    }

    /**
     * Removes a position from management (for cleanup after finalization).
     *
     * @param positionId the ID of the position to remove
     * @return true if position was removed
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
     *
     * @return total unrealized P&L
     */
    public BigDecimal getTotalUnrealizedPnL() {
        return getOpenPositions().stream()
            .map(Position::getProfitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates the total realized P&L across all closed positions.
     *
     * @return total realized P&L
     */
    public BigDecimal getTotalRealizedPnL() {
        return getClosedPositions().stream()
            .map(Position::getProfitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
