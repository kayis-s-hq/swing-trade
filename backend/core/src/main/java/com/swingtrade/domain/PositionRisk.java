package com.swingtrade.domain;

import java.math.BigDecimal;

/** Immutable risk and capital allocation details for a position. */
public record PositionRisk(
    BigDecimal stopLoss,
    BigDecimal target,
    BigDecimal marginUtilized,
    boolean partialExitTaken
) {
    public PositionRisk(BigDecimal stopLoss, BigDecimal target, BigDecimal marginUtilized) {
        this(stopLoss, target, marginUtilized, false);
    }

    public PositionRisk {
        marginUtilized = marginUtilized != null ? marginUtilized : BigDecimal.ZERO;
    }
}
