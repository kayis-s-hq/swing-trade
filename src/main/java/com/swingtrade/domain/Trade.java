package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a complete trade lifecycle from entry to exit.
 * This record captures all information about a trade including entry and exit details, P&L calculations, and duration.
 *
 * @param symbol         the stock symbol (e.g., "AAPL")
 * @param entryDate      the date when the trade was entered
 * @param exitDate       the date when the trade was exited
 * @param entryPrice     the price at which the trade was entered
 * @param exitPrice      the price at which the trade was exited
 * @param quantity       the number of shares traded
 * @param totalPnL       the total profit or loss of the trade
 * @param durationDays   the duration of the trade in days
 * @param tradeStatus    the current status of the trade (OPEN, CLOSED)
 */
public record Trade(
    String symbol,
    LocalDate entryDate,
    LocalDate exitDate,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    Integer quantity,
    BigDecimal totalPnL,
    Integer durationDays,
    TradeStatus tradeStatus
) {
    /**
     * Enum representing the different statuses of a trade.
     */
    public enum TradeStatus {
        OPEN,
        CLOSED
    }
}
