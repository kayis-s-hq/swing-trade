package com.swingtrade.api.dto;

import com.swingtrade.domain.StrategyConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record StrategyConfigResponse(
    Long id,
    String variantId,
    int version,
    String strategyType,
    Map<String, Object> params,
    Map<String, Object> overlays,
    String paramsHash,
    StrategyConfig.Mode mode,
    BigDecimal paperCapital,
    boolean current,
    String notes,
    LocalDateTime createdAt
) {
    public static StrategyConfigResponse from(StrategyConfig config) {
        return new StrategyConfigResponse(config.id(), config.variantId(), config.version(),
            config.strategyType(), config.params(), config.overlays(), config.paramsHash(),
            config.mode(), config.paperCapital(), config.current(), config.notes(), config.createdAt());
    }
}
