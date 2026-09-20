package com.swingtrade.domain;

import java.math.BigDecimal;

/**
 * Lightweight read model of a position for listing and risk aggregation. Carries only the
 * fields those callers need; anything that needs entry dates, exit details, orders or
 * mutation semantics should load the full {@link Position} aggregate instead.
 */
public record PositionSummary(
    Long id,
    String symbol,
    PositionStatus status,
    TradeDirection direction,
    BigDecimal entryPrice,
    Integer quantity,
    BigDecimal currentPrice,
    BigDecimal unrealizedPnL,
    BigDecimal stopLoss,
    BigDecimal target,
    String brokerType
) {
    public PositionSummary {
        status = status != null ? status : PositionStatus.OPEN;
        direction = direction != null ? direction : TradeDirection.LONG;
        unrealizedPnL = unrealizedPnL != null ? unrealizedPnL : BigDecimal.ZERO;
        brokerType = brokerType != null ? brokerType : "PAPER";
    }

    /** Projects a full position onto its summary view. */
    public static PositionSummary from(Position position) {
        return new PositionSummary(position.id(), position.symbol(), position.status(),
            position.direction(), position.entryPrice(), position.quantity(),
            position.currentPrice(), position.unrealizedPnL(), position.stopLoss(),
            position.target(), position.brokerType());
    }
}
