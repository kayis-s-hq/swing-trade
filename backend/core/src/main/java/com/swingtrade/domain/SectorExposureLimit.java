package com.swingtrade.domain;

/** Limits the number and capital share of simultaneously held positions in a sector. */
public record SectorExposureLimit(int maxPositions, double maxCapitalFraction) {
    public SectorExposureLimit {
        if (maxPositions < 0 || !Double.isFinite(maxCapitalFraction)
                || maxCapitalFraction < 0 || maxCapitalFraction > 1) {
            throw new IllegalArgumentException("Sector exposure limits must be non-negative and bounded");
        }
    }
}
