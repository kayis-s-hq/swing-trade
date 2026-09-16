package com.swingtrade.strategy;

/**
 * A symbol excluded from a {@link PortfolioBacktestEngine} run by {@link DataQualityGate}
 * (plan §6.6), with the reason it was blocked so the exclusion is auditable rather than silent.
 */
public record ExcludedSymbol(String symbol, String reason) {
    public ExcludedSymbol {
        if (symbol == null || symbol.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("symbol and reason must be non-blank");
        }
    }
}
