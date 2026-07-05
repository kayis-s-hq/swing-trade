package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single closed round-trip trade produced by {@link BacktestEngine}.
 *
 * @param pnl    net profit/loss in currency units, after brokerage
 * @param pnlPct net profit/loss as a percentage of entry cost (quantity * entryPrice)
 */
public record BacktestTrade(
    String symbol,
    LocalDate entryDate,
    LocalDate exitDate,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal stopLoss,
    BigDecimal target,
    int quantity,
    ExitReason exitReason,
    double pnl,
    double pnlPct,
    int holdingDays
) {}
