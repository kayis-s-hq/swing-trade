package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents an open position in a stock, capturing the details of a trade currently held.
 * This record stores information about the current holding, including entry details,
 * risk parameters (stop loss, target), and current status.
 *
 * @param id         the unique identifier for the position (database auto-generated)
 * @param symbol     the stock symbol (e.g., "RELIANCE", "TCS")
 * @param entryPrice the price at which the position was entered
 * @param entryDate  the date when the position was entered
 * @param quantity   the number of shares held
 * @param stopLoss   the stop-loss price to limit losses
 * @param target     the target price for profit taking
 * @param status     the current status of the position (OPEN, CLOSED, STOPPED)
 * @param entryReason the reason for entering the position
 * @param currentPrice the current market price of the stock
 */
public record Position(
    Long id,
    String symbol,
    BigDecimal entryPrice,
    LocalDate entryDate,
    Integer quantity,
    BigDecimal stopLoss,
    BigDecimal target,
    PositionStatus status,
    String entryReason,
    BigDecimal currentPrice
) {
    /**
     * Enum representing the different statuses of a position.
     */
    public enum PositionStatus {
        OPEN("Open"),
        CLOSED("Closed"),
        STOPPED("Stopped"),
        TARGET_HIT("Target Hit");

        private final String displayName;

        PositionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Creates a new Position with calculated stop loss and target.
     *
     * @param symbol the stock symbol
     * @param entryPrice the entry price
     * @param entryDate the entry date
     * @param quantity the quantity
     * @param atr the Average True Range for stop loss calculation
     * @param entryReason the reason for entering the position
     * @return a new Position instance with calculated stop loss and target
     */
    public static Position createWithRisk(
        String symbol,
        BigDecimal entryPrice,
        LocalDate entryDate,
        Integer quantity,
        BigDecimal atr,
        String entryReason
    ) {
        // Stop loss: entry - (2 * ATR)
        BigDecimal stopLoss = entryPrice.subtract(atr.multiply(BigDecimal.valueOf(2)));

        // Target: entry + (2.5 * risk) where risk = entry - stopLoss
        BigDecimal risk = entryPrice.subtract(stopLoss);
        BigDecimal target = entryPrice.add(risk.multiply(BigDecimal.valueOf(2.5)));

        return new Position(
            null,
            symbol,
            entryPrice,
            entryDate,
            quantity,
            stopLoss,
            target,
            PositionStatus.OPEN,
            entryReason,
            entryPrice
        );
    }

    /**
     * Calculates the unrealized P&L for this position.
     *
     * @param currentPrice the current market price
     * @return the unrealized P&L as a BigDecimal
     */
    public BigDecimal calculateUnrealizedPnL(BigDecimal currentPrice) {
        BigDecimal positionValue = currentPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal costBasis = entryPrice.multiply(BigDecimal.valueOf(quantity));
        return positionValue.subtract(costBasis);
    }

    /**
     * Calculates the percentage P&L for this position.
     *
     * @param currentPrice the current market price
     * @return the percentage P&L as a BigDecimal (0-100 scale)
     */
    public BigDecimal calculatePnLPercent(BigDecimal currentPrice) {
        BigDecimal pnl = calculateUnrealizedPnL(currentPrice);
        BigDecimal costBasis = entryPrice.multiply(BigDecimal.valueOf(quantity));
        if (costBasis.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pnl.multiply(BigDecimal.valueOf(100)).divide(costBasis, 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Returns true if the position is open.
     *
     * @return true if OPEN status
     */
    public boolean isOpen() {
        return PositionStatus.OPEN == status;
    }

    /**
     * Returns true if the position has been closed (either by target or stop loss).
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return PositionStatus.CLOSED == status || PositionStatus.STOPPED == status || PositionStatus.TARGET_HIT == status;
    }
}
