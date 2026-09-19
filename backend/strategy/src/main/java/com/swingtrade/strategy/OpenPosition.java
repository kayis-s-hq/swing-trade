package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An open paper/live position as seen by {@link SignalStrategy#evaluateExit} and
 * {@link UniformExitEvaluator}. Distinct from {@code BacktestEngine}'s private nested
 * {@code OpenPosition} record (used by the existing, untouched single-symbol backtest loop) -
 * this one is the public SPI type new strategy types and the future portfolio backtest engine
 * (plan §6.1) evaluate against.
 *
 * @param entryIndex     bar index the position was entered on
 * @param entryDate      calendar date of {@link #entryIndex()}
 * @param entryPrice     fill price
 * @param stopLoss       ATR-based stop loss level
 * @param target         reward:risk based target level
 * @param quantity       number of shares held
 * @param highWaterMark  highest close observed since entry, used for the optional trailing stop
 *                       (§3.3); defaults to {@code entryPrice} when not supplied
 */
public record OpenPosition(
    int entryIndex,
    LocalDate entryDate,
    BigDecimal entryPrice,
    BigDecimal stopLoss,
    BigDecimal target,
    int quantity,
    BigDecimal highWaterMark
) {
    public OpenPosition {
        if (entryDate == null || entryPrice == null || stopLoss == null || target == null) {
            throw new IllegalArgumentException("entryDate/entryPrice/stopLoss/target cannot be null");
        }
        if (highWaterMark == null) {
            highWaterMark = entryPrice;
        }
    }

    /** Opens a new position with the high-water mark seeded at the entry price. */
    public static OpenPosition open(int entryIndex, LocalDate entryDate, BigDecimal entryPrice,
                                     BigDecimal stopLoss, BigDecimal target, int quantity) {
        return new OpenPosition(entryIndex, entryDate, entryPrice, stopLoss, target, quantity, entryPrice);
    }

    /** Returns a copy with the high-water mark advanced to {@code candidate} if it is higher. */
    public OpenPosition advanceHighWaterMark(BigDecimal candidate) {
        BigDecimal next = candidate.max(highWaterMark);
        return new OpenPosition(entryIndex, entryDate, entryPrice, stopLoss, target, quantity, next);
    }
}
