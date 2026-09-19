package com.swingtrade.domain;

import java.util.List;
import java.util.Map;

/** Immutable input presented to a portfolio entry exposure policy. */
public record PortfolioExposureContext(
        String candidateSymbol,
        String candidateSector,
        double candidateNotional,
        double initialCapital,
        List<Holding> openHoldings,
        Map<String, List<Double>> closingPrices
) {
    public PortfolioExposureContext {
        if (candidateSymbol == null || !Double.isFinite(candidateNotional) || candidateNotional <= 0
                || !Double.isFinite(initialCapital) || initialCapital <= 0) {
            throw new IllegalArgumentException("Portfolio exposure context is invalid");
        }
        openHoldings = List.copyOf(openHoldings == null ? List.of() : openHoldings);
        closingPrices = Map.copyOf(closingPrices == null ? Map.of() : closingPrices);
    }

    public record Holding(String symbol, String sector, double notional) {
        public Holding {
            if (symbol == null || !Double.isFinite(notional) || notional <= 0) {
                throw new IllegalArgumentException("Portfolio holding is invalid");
            }
        }
    }
}
