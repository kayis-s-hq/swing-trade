package com.swingtrade.domain;

/** Limits the absolute return correlation with any existing holding. */
public record CorrelationExposureLimit(double maxAbsoluteCorrelation, int lookbackDays) {
    public CorrelationExposureLimit {
        if (!Double.isFinite(maxAbsoluteCorrelation) || maxAbsoluteCorrelation < 0
                || maxAbsoluteCorrelation > 1 || lookbackDays < 2) {
            throw new IllegalArgumentException("Correlation exposure limits must be bounded with a valid lookback");
        }
    }
}
