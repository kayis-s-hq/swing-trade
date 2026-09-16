package com.swingtrade.api.dto.strategy;

import com.swingtrade.domain.StrategyConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * {@code GET /api/strategies} / {@code GET /api/strategies/{variantId}/versions} response entry.
 *
 * <p>Headline OOS metrics (plan §4.4: "current version of every variant + mode + headline OOS
 * metrics") are omitted here: they depend on the walk-forward/backtest analytics engine, which
 * is a later phase (plan §6). This DTO carries config data only for now.
 */
public record StrategyVariantResponse(
    String variantId,
    int version,
    String strategyType,
    Map<String, Object> params,
    Map<String, Object> overlays,
    String paramsHash,
    String mode,
    BigDecimal paperCapital,
    boolean isCurrent,
    String portfolioAction,
    String notes,
    LocalDateTime createdAt
) {
    public static StrategyVariantResponse from(StrategyConfig config) {
        return new StrategyVariantResponse(
            config.variantId(),
            config.version(),
            config.strategyType(),
            config.params(),
            config.overlays(),
            config.paramsHash(),
            config.mode().name(),
            config.paperCapital(),
            config.isCurrent(),
            config.portfolioAction() == null ? null : config.portfolioAction().name(),
            config.notes(),
            config.createdAt()
        );
    }
}
