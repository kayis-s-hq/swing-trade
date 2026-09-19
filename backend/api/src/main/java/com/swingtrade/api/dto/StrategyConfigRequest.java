package com.swingtrade.api.dto;

import com.swingtrade.domain.StrategyConfig;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

/** Payload for creating a new immutable strategy configuration version. */
public record StrategyConfigRequest(
    @NotBlank(message = "variantId is required") String variantId,
    @Min(value = 1, message = "version must be positive") Integer version,
    @NotBlank(message = "strategyType is required") String strategyType,
    @NotNull(message = "params are required") Map<String, Object> params,
    Map<String, Object> overlays,
    @NotNull(message = "mode is required") StrategyConfig.Mode mode,
    @NotNull(message = "paperCapital is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "paperCapital must be non-negative")
    BigDecimal paperCapital,
    String notes
) {
    public Map<String, Object> normalizedOverlays() {
        return overlays == null ? Map.of() : overlays;
    }
}
