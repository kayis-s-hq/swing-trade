package com.swingtrade.api.dto.strategy;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/strategies/{variantId}/clone} request body. */
public record CloneRequest(@NotBlank String newVariantId, String notes) {
}
