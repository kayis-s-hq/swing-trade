package com.swingtrade.api.service;

import com.swingtrade.domain.Signal;

import java.math.BigDecimal;

/**
 * One variant's decision for a symbol/day, as the signal tournament sees it.
 *
 * @param variantId            the strategy variant id
 * @param strategyVersion      the variant's configuration version
 * @param type                 the decision type (BUY, SELL or HOLD)
 * @param sentimentGateEnabled whether the variant's overlays enable a sentiment gate
 * @param persisted            true if a signals row was written for this decision
 * @param confidence           the signal confidence in [0,1], used for arbitration
 */
public record VariantSignalOutcome(String variantId, int strategyVersion, Signal.SignalType type,
                                   boolean sentimentGateEnabled, boolean persisted, BigDecimal confidence) {
}
