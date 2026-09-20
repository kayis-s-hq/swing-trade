package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single closed round-trip trade produced by {@link BacktestEngine}.
 *
 * @param pnl    net profit/loss in currency units, after delivery costs; normalized to
 *               {@link FinancialScale#MONEY_SCALE} (HALF_UP)
 * @param pnlPct net profit/loss as a percentage of entry cost (quantity * entryPrice); normalized
 *               to {@link FinancialScale#PERCENT_SCALE} (HALF_UP)
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
    BigDecimal pnl,
    BigDecimal pnlPct,
    int holdingDays
) {
    public BacktestTrade {
        pnl = FinancialScale.money(pnl);
        pnlPct = FinancialScale.percent(pnlPct);
    }
}
