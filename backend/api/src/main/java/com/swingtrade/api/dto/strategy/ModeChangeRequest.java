package com.swingtrade.api.dto.strategy;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/strategies/{variantId}/mode} request body. {@code confirm=true} is required
 * to set mode to CHAMPION (plan §4.4: "CHAMPION change requires confirm=true").
 */
public record ModeChangeRequest(@NotBlank String mode, boolean confirm) {
}
