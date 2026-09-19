package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only view of a shadow-book position, open or closed, for dashboards. */
public record ShadowPositionView(
    String portfolioId,
    String symbol,
    LocalDate entryDate,
    BigDecimal entryPrice,
    BigDecimal stopLoss,
    BigDecimal target,
    int quantity,
    String status,
    LocalDate exitDate,
    BigDecimal exitPrice,
    String exitReason,
    BigDecimal pnl
) {
}
