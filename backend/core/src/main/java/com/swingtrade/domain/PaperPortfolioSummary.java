package com.swingtrade.domain;

import java.math.BigDecimal;

/** Read-only summary of one paper portfolio (a variant's shadow book, the "selected" book, or "default"). */
public record PaperPortfolioSummary(
    String portfolioId,
    BigDecimal initialCapital,
    BigDecimal currentCapital,
    BigDecimal realizedPnl,
    int openPositionCount
) {
}
