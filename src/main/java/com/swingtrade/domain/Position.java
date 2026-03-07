package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents an open position in a stock, capturing the details of a trade currently held.
 * This record stores information about the current holding, including entry details and risk parameters.
 *
 * @param symbol     the stock symbol (e.g., "AAPL")
 * @param entryPrice the price at which the position was entered
 * @param entryDate  the date when the position was entered
 * @param quantity   the number of shares held
 * @param stopLoss   the stop-loss price to limit losses
 * @param target     the target price for profit taking
 * @param status     the current status of the position (OPEN, CLOSED)
 */
public record Position(
    String symbol,
    BigDecimal entryPrice,
    LocalDate entryDate,
    Integer quantity,
    BigDecimal stopLoss,
    BigDecimal target,
    PositionStatus status
) {
    /**
     * Enum representing the different statuses of a position.
     */
    public enum PositionStatus {
        OPEN,
        CLOSED
    }
}
