package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of a SHADOW/CHAMPION variant's open paper position (plan §7.4 gap-fill),
 * returned by {@link com.swingtrade.domain.service.PaperPortfolioService#findOpenShadowPosition}
 * so the job orchestrator can evaluate an exit for it without depending on any broker/data
 * persistence type directly (domain-port pattern).
 */
public record ShadowPositionSnapshot(
    String portfolioId,
    String symbol,
    LocalDate entryDate,
    BigDecimal entryPrice,
    BigDecimal stopLoss,
    BigDecimal target,
    int quantity,
    BigDecimal highWaterMark
) {
}
