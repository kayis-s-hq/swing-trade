package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a complete trade lifecycle from entry to exit.
 * This record captures all information about a trade including entry and exit details,
 * P&L calculations, duration, and status.
 *
 * @param id           the unique identifier for the trade (database auto-generated)
 * @param positionId   the associated position ID
 * @param symbol       the stock symbol (e.g., "RELIANCE", "TCS")
 * @param entryDate    the date when the trade was entered
 * @param exitDate     the date when the trade was exited (null if still open)
 * @param entryPrice   the price at which the trade was entered
 * @param exitPrice    the price at which the trade was exited (null if still open)
 * @param quantity     the number of shares traded
 * @param totalPnL     the total profit or loss of the trade
 * @param durationDays the duration of the trade in days
 * @param tradeStatus  the current status of the trade (OPEN, CLOSED, STOPPED, TARGET_HIT)
 * @param entryReason  the reason for entering the trade
 * @param exitReason   the reason for exiting the trade (null if still open)
 * @param fees         the total fees paid for the trade
 */
public record Trade(
    Long id,
    Long positionId,
    String symbol,
    LocalDate entryDate,
    LocalDate exitDate,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    Integer quantity,
    TradeDirection direction,
    BigDecimal totalPnL,
    Integer durationDays,
    TradeStatus tradeStatus,
    String entryReason,
    String exitReason,
    BigDecimal fees
) {
    /**
     * Enum representing the different statuses of a trade.
     */
    public enum TradeStatus {
        OPEN("Open"),
        CLOSED("Closed - Profit"),
        STOPPED("Stopped - Loss"),
        TARGET_HIT("Target Hit - Profit"),
        TIME_STOP("Time Stop");

        private final String displayName;

        TradeStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Creates a new open Trade.
     *
     * @param positionId the associated position ID
     * @param symbol the stock symbol
     * @param entryDate the entry date
     * @param entryPrice the entry price
     * @param quantity the quantity
     * @param entryReason the reason for entering
     * @param fees the fees paid
     * @return a new Trade instance
     */
    public static Trade open(
        Long positionId,
        String symbol,
        LocalDate entryDate,
        BigDecimal entryPrice,
        Integer quantity,
        String entryReason,
        BigDecimal fees
    ) {
        return open(positionId, symbol, entryDate, entryPrice, quantity, entryReason, fees, TradeDirection.LONG);
    }

    public static Trade open(
        Long positionId,
        String symbol,
        LocalDate entryDate,
        BigDecimal entryPrice,
        Integer quantity,
        String entryReason,
        BigDecimal fees,
        TradeDirection direction
    ) {
        return new Trade(
            null,
            positionId,
            symbol,
            entryDate,
            null,
            entryPrice,
            null,
            quantity,
            direction,
            null,
            null,
            TradeStatus.OPEN,
            entryReason,
            null,
            fees
        );
    }

    /**
     * Closes a trade with the given exit details.
     *
     * @param trade the open trade to close
     * @param exitDate the exit date
     * @param exitPrice the exit price
     * @param exitReason the reason for exit
     * @return a new Trade instance with exit details populated
     */
    public static Trade close(
        Trade trade,
        LocalDate exitDate,
        BigDecimal exitPrice,
        String exitReason
    ) {
        // totalPnL is the gross P&L on the position move; fees are tracked separately via the
        // `fees` field (persisted alongside totalPnL, see TradeEntity) rather than netted in
        // here — callers that need net P&L compute totalPnL.subtract(fees) themselves.
        BigDecimal totalPnL;
        if (trade.direction() == TradeDirection.SHORT) {
            totalPnL = trade.entryPrice().subtract(exitPrice)
                .multiply(BigDecimal.valueOf(trade.quantity()));
        } else {
            totalPnL = exitPrice.subtract(trade.entryPrice())
                .multiply(BigDecimal.valueOf(trade.quantity()));
        }

        int durationDays = (int) java.time.temporal.ChronoUnit.DAYS.between(trade.entryDate(), exitDate);

        return new Trade(
            trade.id(),
            trade.positionId(),
            trade.symbol(),
            trade.entryDate(),
            exitDate,
            trade.entryPrice(),
            exitPrice,
            trade.quantity(),
            trade.direction(),
            totalPnL,
            durationDays,
            isProfit(totalPnL) ? TradeStatus.CLOSED : TradeStatus.STOPPED,
            trade.entryReason(),
            exitReason,
            trade.fees()
        );
    }

    private static boolean isProfit(BigDecimal pnl) {
        return pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Returns true if the trade is open.
     *
     * @return true if OPEN
     */
    public boolean isOpen() {
        return TradeStatus.OPEN == tradeStatus;
    }

    /**
     * Returns true if the trade has a profit.
     *
     * @return true if PnL > 0
     */
    public boolean isProfitable() {
        return totalPnL != null && totalPnL.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Returns true if the trade resulted in a loss.
     *
     * @return true if PnL < 0
     */
    public boolean isLoss() {
        return totalPnL != null && totalPnL.compareTo(BigDecimal.ZERO) < 0;
    }
}
