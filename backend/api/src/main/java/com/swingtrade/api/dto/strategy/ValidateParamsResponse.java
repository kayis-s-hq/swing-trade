package com.swingtrade.api.dto.strategy;

import com.swingtrade.strategy.ParamValidationResult;

import java.util.List;
import java.util.Map;

/** {@code POST /api/strategies/validate} response. */
public record ValidateParamsResponse(boolean valid, List<String> errors, Map<String, Object> resolvedParams) {
    public static ValidateParamsResponse from(ParamValidationResult result) {
        return new ValidateParamsResponse(result.valid(), result.errors(), result.resolvedParams());
    }
}
