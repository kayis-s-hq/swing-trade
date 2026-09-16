package com.swingtrade.api.dto.strategy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * {@code POST /api/strategies/{variantId}/versions} request body: creates a new version of an
 * existing variant. {@code portfolioAction} ({@code CONTINUE|RESET}) is recorded intent only in
 * this phase (plan §4.5) - actual virtual-position manipulation is deferred.
 */
public record CreateVersionRequest(
    Map<String, Object> params,
    Map<String, Object> overlays,
    BigDecimal paperCapital,
    String portfolioAction,
    String notes
) {
}
