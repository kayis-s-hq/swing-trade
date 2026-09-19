package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single closed round-trip trade produced by {@link PortfolioBacktestEngine} (plan §6.1/§6.2).
 * Distinct from the legacy, single-symbol {@code BacktestTrade}: this record additionally carries
 * the entry risk (for R-multiple expectancy), MAE/MFE (plan §6.2 diagnostics) and the strategy
 * score at entry (used for candidate ranking diagnostics).
 *
 * @param riskPerShare       {@code entryPrice - stopLoss} at entry, used to express {@link #pnl()}
 *                           as an R-multiple ({@code pnl / (riskPerShare * quantity)})
 * @param maxAdverseExcursion  most negative unrealized P&L (per share) observed while the
 *                              position was open, i.e. worst drawdown of the trade (MAE)
 * @param maxFavorableExcursion most positive unrealized P&L (per share) observed while the
 *                               position was open (MFE)
 */
public record PortfolioTrade(
    String symbol,
    LocalDate entryDate,
    LocalDate exitDate,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal stopLoss,
    BigDecimal target,
    int quantity,
    ExitReason exitReason,
    BigDecimal riskPerShare,
    BigDecimal pnl,
    double pnlPct,
    int holdingDays,
    BigDecimal maxAdverseExcursion,
    BigDecimal maxFavorableExcursion,
    BigDecimal strategyScoreAtEntry
) {

    /** R-multiple: net P&L expressed in units of initial risk. 0 when risk was zero/unknown. */
    public double rMultiple() {
        if (riskPerShare == null || riskPerShare.signum() <= 0 || quantity == 0) {
            return 0.0;
        }
        double initialRisk = riskPerShare.doubleValue() * quantity;
        return initialRisk == 0 ? 0.0 : pnl.doubleValue() / initialRisk;
    }

    public boolean isWin() {
        return pnl.signum() > 0;
    }
}
