package com.swingtrade.api.dto.strategy;

import java.util.List;

/** {@code GET /api/strategy-types} response entry: a registered strategy type + its schema. */
public record StrategyTypeResponse(String type, List<ParamDefResponse> params) {
}
