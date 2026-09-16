package com.swingtrade.strategy;

import java.math.BigDecimal;

/** Calculates round-trip delivery costs for a long equity trade. */
@FunctionalInterface
public interface BacktestCostModel {

    BigDecimal roundTripCost(BigDecimal entryPrice, BigDecimal exitPrice, int quantity,
                             BigDecimal brokerage);
}
