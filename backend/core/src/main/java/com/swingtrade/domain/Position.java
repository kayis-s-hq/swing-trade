package com.swingtrade.domain;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a trading position in a stock, capturing entry details,
 * risk parameters, execution metadata, and current status.
 *
 * @param id             the unique database identifier
 * @param symbol         the stock symbol (e.g., "RELIANCE", "TCS")
 * @param entryPrice     the price at which the position was entered
 * @param entryDate      the date when the position was entered
 * @param quantity       the number of shares held
 * @param stopLoss       the stop-loss price to limit losses
 * @param target         the target price for profit taking
 * @param status         the current status of the position
 * @param entryReason    the reason for entering the position
 * @param currentPrice   the current market price of the stock
 * @param positionId     the internal position identifier (e.g., "POS_00000001")
 * @param brokerPositionId the broker-assigned position identifier
 * @param exchange       the exchange where the position was traded
 * @param direction      LONG or SHORT
 * @param averagePrice   weighted average entry price (for partial fills)
 * @param unrealizedPnL  current unrealized profit/loss
 * @param realizedPnL    realized profit/loss from partial exits
 * @param marginUtilized margin consumed for this position
 * @param entryTime      precise entry timestamp
 * @param exitTime       precise exit timestamp
 * @param exitReason     reason for position exit
 * @param orders         list of orders associated with this position
 */
public record Position(
    Long id,
    String brokerType,
    String symbol,
    BigDecimal entryPrice,
    LocalDate entryDate,
    Integer quantity,
    BigDecimal stopLoss,
    BigDecimal target,
    PositionStatus status,
    String entryReason,
    BigDecimal currentPrice,
    // Broker-enriched fields
    String positionId,
    String brokerPositionId,
    Exchange exchange,
    TradeDirection direction,
    BigDecimal averagePrice,
    BigDecimal unrealizedPnL,
    BigDecimal realizedPnL,
    BigDecimal marginUtilized,
    LocalDateTime entryTime,
    LocalDateTime exitTime,
    String exitReason,
    List<Order> orders
) {

    // Compact constructor: fills broker-enriched defaults when not provided
    public Position {
        if (brokerType == null) brokerType = "PAPER";
        if (positionId == null && id != null) {
            positionId = "POS_" + String.format("%08d", id);
        }
        if (exchange == null) exchange = Exchange.NSE;
        if (direction == null) direction = TradeDirection.LONG;
        if (averagePrice == null) averagePrice = entryPrice;
        if (unrealizedPnL == null) unrealizedPnL = BigDecimal.ZERO;
        if (realizedPnL == null) realizedPnL = BigDecimal.ZERO;
        if (marginUtilized == null) marginUtilized = BigDecimal.ZERO;
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
            null, null,
            symbol,
            entryPrice,
            entryDate,
            quantity,
            stopLoss,
            target,
            PositionStatus.OPEN,
            entryReason,
            entryPrice,
            null, null, null,
            TradeDirection.LONG,
            null, null, null, null,
            null, null, null, null
        );
    }

    /**
     * Creates a full-position instance with broker-enriched fields.
     */
    public static Position of(
        Long id,
        String brokerType,
        String symbol,
        BigDecimal entryPrice,
        LocalDate entryDate,
        Integer quantity,
        BigDecimal stopLoss,
        BigDecimal target,
        PositionStatus status,
        String entryReason,
        BigDecimal currentPrice,
        String positionId,
        String brokerPositionId,
        Exchange exchange,
        TradeDirection direction,
        BigDecimal averagePrice,
        BigDecimal unrealizedPnL,
        BigDecimal realizedPnL,
        BigDecimal marginUtilized,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        String exitReason,
        List<Order> orders
    ) {
        return new Position(
            id, brokerType, symbol, entryPrice, entryDate, quantity,
            stopLoss, target, status, entryReason, currentPrice,
            positionId, brokerPositionId, exchange, direction,
            averagePrice, unrealizedPnL, realizedPnL, marginUtilized,
            entryTime, exitTime, exitReason, orders
        );
    }

    /**
     * Calculates the unrealized P&L for this position.
     * Supports both LONG and SHORT directions.
     *
     * @param currentPrice the current market price
     * @return the unrealized P&L as a BigDecimal
     */
    public BigDecimal calculateUnrealizedPnL(BigDecimal currentPrice) {
        BigDecimal priceDifference;
        if (direction == TradeDirection.SHORT) {
            priceDifference = entryPrice.subtract(currentPrice);
        } else {
            priceDifference = currentPrice.subtract(entryPrice);
        }
        return priceDifference.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Calculates the percentage P&L for this position.
     * Supports both LONG and SHORT directions.
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
     */
    public boolean isOpen() {
        return PositionStatus.OPEN == status;
    }

    /**
     * Returns true if the position has been closed (either by target, stop loss, or manual).
     */
    public boolean isClosed() {
        return PositionStatus.CLOSED == status
            || PositionStatus.STOPPED == status
            || PositionStatus.TARGET_HIT == status;
    }
}