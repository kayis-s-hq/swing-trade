package com.swingtrade.api.dto;

import com.swingtrade.domain.StrategyConfig;
import jakarta.validation.constraints.NotNull;

/** Payload for promoting, shadowing, or disabling a strategy variant. */
public record StrategyModeRequest(
    @NotNull(message = "mode is required") StrategyConfig.Mode mode,
    String notes
) {}
