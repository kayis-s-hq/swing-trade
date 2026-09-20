package com.swingtrade.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Port for portfolio-level entry acceptance rules. */
@FunctionalInterface
public interface PortfolioExposurePolicy {
    String SECTOR_POSITION_LIMIT = "SECTOR_POSITION_LIMIT";
    String SECTOR_CAPITAL_LIMIT = "SECTOR_CAPITAL_LIMIT";
    String CORRELATION_LIMIT = "CORRELATION_LIMIT";

    PortfolioExposureDecision evaluate(PortfolioExposureContext context);

    static PortfolioExposurePolicy none() {
        return context -> PortfolioExposureDecision.accept();
    }

    static PortfolioExposurePolicy limits(SectorExposureLimit sectorLimit,
                                          CorrelationExposureLimit correlationLimit) {
        if (sectorLimit == null || correlationLimit == null) {
            throw new IllegalArgumentException("Exposure limits must be non-null");
        }
        return context -> {
            List<PortfolioExposureContext.Holding> sectorHoldings = context.openHoldings().stream()
                    .filter(holding -> context.candidateSector() != null
                            && context.candidateSector().equals(holding.sector()))
                    .toList();
            if (sectorHoldings.size() >= sectorLimit.maxPositions()) {
                return PortfolioExposureDecision.reject(SECTOR_POSITION_LIMIT);
            }
            BigDecimal sectorNotional = sectorHoldings.stream().map(PortfolioExposureContext.Holding::notional)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal capitalCap = context.initialCapital()
                    .multiply(BigDecimal.valueOf(sectorLimit.maxCapitalFraction()));
            if (sectorNotional.add(context.candidateNotional()).compareTo(capitalCap) > 0) {
                return PortfolioExposureDecision.reject(SECTOR_CAPITAL_LIMIT);
            }
            for (PortfolioExposureContext.Holding holding : context.openHoldings()) {
                List<Double> candidate = returns(context.closingPrices().get(context.candidateSymbol()),
                        correlationLimit.lookbackDays());
                List<Double> held = returns(context.closingPrices().get(holding.symbol()),
                        correlationLimit.lookbackDays());
                if (candidate.size() >= 2 && held.size() >= 2) {
                    int size = Math.min(candidate.size(), held.size());
                    double correlation = correlation(candidate.subList(candidate.size() - size, candidate.size()),
                            held.subList(held.size() - size, held.size()));
                    if (Double.isFinite(correlation)
                            && Math.abs(correlation) > correlationLimit.maxAbsoluteCorrelation()) {
                        return PortfolioExposureDecision.reject(CORRELATION_LIMIT);
                    }
                }
            }
            return PortfolioExposureDecision.accept();
        };
    }

    private static List<Double> returns(List<Double> prices, int lookback) {
        if (prices == null) return List.of();
        int from = Math.max(0, prices.size() - lookback - 1);
        List<Double> result = new ArrayList<>();
        for (int i = from + 1; i < prices.size(); i++) {
            double previous = prices.get(i - 1);
            double current = prices.get(i);
            if (previous <= 0 || current <= 0 || !Double.isFinite(previous) || !Double.isFinite(current)) return List.of();
            result.add(current / previous - 1.0);
        }
        return result;
    }

    private static double correlation(List<Double> left, List<Double> right) {
        double leftMean = left.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double rightMean = right.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double covariance = 0, leftVariance = 0, rightVariance = 0;
        for (int i = 0; i < left.size(); i++) {
            double l = left.get(i) - leftMean, r = right.get(i) - rightMean;
            covariance += l * r;
            leftVariance += l * l;
            rightVariance += r * r;
        }
        return leftVariance == 0 || rightVariance == 0
                ? Double.NaN : covariance / Math.sqrt(leftVariance * rightVariance);
    }
}
