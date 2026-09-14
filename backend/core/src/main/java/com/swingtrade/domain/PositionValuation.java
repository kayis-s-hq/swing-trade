package com.swingtrade.domain;

import java.math.BigDecimal;

/** Immutable market valuation and P&L details for a position. */
public record PositionValuation(
    BigDecimal currentPrice,
    BigDecimal unrealizedPnL,
    BigDecimal realizedPnL
) {
    public PositionValuation {
        unrealizedPnL = unrealizedPnL != null ? unrealizedPnL : BigDecimal.ZERO;
        realizedPnL = realizedPnL != null ? realizedPnL : BigDecimal.ZERO;
    }
}
