package com.swingtrade.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Immutable input presented to a portfolio entry exposure policy. Notionals and capital are exact
 * {@link BigDecimal} money; {@code closingPrices} stay {@code Double} because they only feed the
 * statistical correlation screen.
 */
public record PortfolioExposureContext(
        String candidateSymbol,
        String candidateSector,
        BigDecimal candidateNotional,
        BigDecimal initialCapital,
        List<Holding> openHoldings,
        Map<String, List<Double>> closingPrices
) {
    public PortfolioExposureContext {
        if (candidateSymbol == null || candidateNotional == null || candidateNotional.signum() <= 0
                || initialCapital == null || initialCapital.signum() <= 0) {
            throw new IllegalArgumentException("Portfolio exposure context is invalid");
        }
        openHoldings = List.copyOf(openHoldings == null ? List.of() : openHoldings);
        closingPrices = Map.copyOf(closingPrices == null ? Map.of() : closingPrices);
    }

    public record Holding(String symbol, String sector, BigDecimal notional) {
        public Holding {
            if (symbol == null || notional == null || notional.signum() <= 0) {
                throw new IllegalArgumentException("Portfolio holding is invalid");
            }
        }
    }
}
