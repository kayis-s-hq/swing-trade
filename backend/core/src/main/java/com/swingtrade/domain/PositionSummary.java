package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lightweight read model of a position for listing and risk aggregation. Carries only the
 * fields those callers need (including entry date and reason for position lists); anything
 * that needs exit details, orders or mutation semantics should load the full {@link Position} aggregate instead.
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
    String brokerType,
    LocalDate entryDate,
    String entryReason
) {
    public PositionSummary {
        status = status != null ? status : PositionStatus.OPEN;
        direction = direction != null ? direction : TradeDirection.LONG;
        unrealizedPnL = unrealizedPnL != null ? unrealizedPnL : BigDecimal.ZERO;
        brokerType = brokerType != null ? brokerType : "PAPER";
    }

    /** Unrealized P&L at {@code price}; mirrors {@link Position#calculateUnrealizedPnL}. */
    public BigDecimal calculateUnrealizedPnL(BigDecimal price) {
        BigDecimal priceDifference = direction == TradeDirection.SHORT
            ? entryPrice.subtract(price)
            : price.subtract(entryPrice);
        return priceDifference.multiply(BigDecimal.valueOf(quantity));
    }

    /** Unrealized P&L percent at {@code price}; mirrors {@link Position#calculatePnLPercent}. */
    public BigDecimal calculatePnLPercent(BigDecimal price) {
        BigDecimal costBasis = entryPrice.multiply(BigDecimal.valueOf(quantity));
        if (costBasis.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return calculateUnrealizedPnL(price).multiply(BigDecimal.valueOf(100))
            .divide(costBasis, 4, java.math.RoundingMode.HALF_UP);
    }

    /** Projects a full position onto its summary view. */
    public static PositionSummary from(Position position) {
        return new PositionSummary(position.id(), position.symbol(), position.status(),
            position.direction(), position.entryPrice(), position.quantity(),
            position.currentPrice(), position.unrealizedPnL(), position.stopLoss(),
            position.target(), position.brokerType(), position.entryDate(), position.entryReason());
    }
}
