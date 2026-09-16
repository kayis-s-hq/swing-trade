package com.swingtrade.strategy;

import com.swingtrade.domain.RiskManagementPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Moves a stop to breakeven, then trails the highest completed close by a fixed percentage. */
public record TrailingBreakevenPolicy(double breakevenRiskMultiple, double trailingStopPct)
        implements RiskManagementPolicy {

    public TrailingBreakevenPolicy {
        if (!Double.isFinite(breakevenRiskMultiple) || breakevenRiskMultiple <= 0
                || !Double.isFinite(trailingStopPct) || trailingStopPct <= 0 || trailingStopPct >= 1) {
            throw new IllegalArgumentException("Risk-policy parameters must be finite and in range");
        }
    }

    @Override
    public RiskManagementDecision evaluate(RiskManagementContext context) {
        BigDecimal risk = context.entryPrice().subtract(context.initialStop());
        if (risk.signum() <= 0) return RiskManagementDecision.hold();

        BigDecimal breakevenTrigger = context.entryPrice().add(
                risk.multiply(BigDecimal.valueOf(breakevenRiskMultiple)));
        BigDecimal stop = context.initialStop();
        String reason = "";
        if (context.highestCloseBeforeBar().compareTo(breakevenTrigger) >= 0) {
            stop = context.entryPrice();
            reason = "BREAKEVEN_STOP";
            BigDecimal trailing = context.highestCloseBeforeBar().multiply(
                    BigDecimal.ONE.subtract(BigDecimal.valueOf(trailingStopPct)));
            if (trailing.compareTo(stop) > 0) {
                stop = trailing;
                reason = "TRAILING_STOP";
            }
        }
        if (!reason.isEmpty() && context.currentLow().compareTo(stop) <= 0) {
            return RiskManagementDecision.exit(stop.setScale(8, RoundingMode.HALF_UP), reason);
        }
        return RiskManagementDecision.hold();
    }
}
