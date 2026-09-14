package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Immutable entry and instrument details for a position. */
public record PositionEntry(
    String symbol,
    BigDecimal entryPrice,
    LocalDate entryDate,
    Integer quantity,
    LocalDateTime entryTime,
    String entryReason,
    String positionId,
    String brokerPositionId,
    Exchange exchange,
    TradeDirection direction,
    BigDecimal averagePrice
) {
    public PositionEntry {
        exchange = exchange != null ? exchange : Exchange.NSE;
        direction = direction != null ? direction : TradeDirection.LONG;
        averagePrice = averagePrice != null ? averagePrice : entryPrice;
    }

    public static PositionEntry of(
        String symbol,
        BigDecimal entryPrice,
        LocalDate entryDate,
        Integer quantity,
        LocalDateTime entryTime,
        String entryReason,
        String positionId,
        String brokerPositionId,
        Exchange exchange,
        TradeDirection direction,
        BigDecimal averagePrice
    ) {
        return new PositionEntry(symbol, entryPrice, entryDate, quantity, entryTime, entryReason,
            positionId, brokerPositionId, exchange, direction, averagePrice);
    }
}
