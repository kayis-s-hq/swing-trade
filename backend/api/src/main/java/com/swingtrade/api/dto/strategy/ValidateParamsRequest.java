package com.swingtrade.api.dto.strategy;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** {@code POST /api/strategies/validate} request body: validates params without saving. */
public record ValidateParamsRequest(@NotBlank String strategyType, Map<String, Object> params) {
}
