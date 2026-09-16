package com.swingtrade.api.dto.strategy;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.Map;

/** {@code POST /api/strategies} request body: creates a new variant's v1. */
public record CreateVariantRequest(
    @NotBlank String variantId,
    @NotBlank String strategyType,
    Map<String, Object> params,
    Map<String, Object> overlays,
    BigDecimal paperCapital,
    String notes
) {
}
