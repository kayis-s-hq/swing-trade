package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A closed round-trip shadow-variant paper trade (plan §7.4 gap-fill): the BUY that
 * {@code executeVariantBuy} simulated, paired with the exit that later closed it. Returned by
 * {@link com.swingtrade.domain.service.PaperPortfolioService#findClosedTrades} so a caller (the
 * promotion-eligibility checker/REST endpoint) can compute P&L, hold duration and R-multiple
 * without needing to know how this is persisted. Mirrors the shape of the backtest-domain
 * {@code PortfolioTrade} (strategy module) where it fits cleanly, without depending on that
 * module from core.
 */
public record ShadowClosedTrade(
    String portfolioId,
    String symbol,
    LocalDate entryDate,
    LocalDate exitDate,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal stopLoss,
    BigDecimal target,
    int quantity,
    String exitReason,
    BigDecimal pnl
) {

    /** Calendar days held, or 0 if either date is missing. */
    public long holdingDays() {
        if (entryDate == null || exitDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(entryDate, exitDate);
    }

    /** R-multiple: net P&L expressed in units of initial risk. 0 when risk is zero/unknown. */
    public double rMultiple() {
        if (entryPrice == null || stopLoss == null || pnl == null || quantity == 0) {
            return 0.0;
        }
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss);
        if (riskPerShare.signum() <= 0) {
            return 0.0;
        }
        double initialRisk = riskPerShare.doubleValue() * quantity;
        return initialRisk == 0 ? 0.0 : pnl.doubleValue() / initialRisk;
    }

    public boolean isWin() {
        return pnl != null && pnl.signum() > 0;
    }
}
