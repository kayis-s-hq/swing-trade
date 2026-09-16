package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * An immutable, versioned strategy configuration ("variant") - plan
 * docs/plans/2026-09-16-configurable-multi-strategy.md §4.1.
 *
 * <p>Rows are append-only: {@code params}/{@code overlays}/{@code strategyType} never change
 * once a version is created. Only {@code mode}, {@code isCurrent}, and {@code notes} may change
 * on a row (creating a new version, or flipping mode/is_current), which is why this record has
 * no "with params" style mutator - callers create a brand new {@link StrategyConfig} for a new
 * version via the store, they never mutate an existing one's params.
 *
 * @param id              database id (null for a not-yet-persisted config)
 * @param variantId       the variant's stable identifier, e.g. "PULLBACK_B"
 * @param version         business version number (1, 2, ...); distinct from any JPA optimistic
 *                        lock column in the persistence layer
 * @param strategyType    the {@code SignalStrategy.type()} this variant configures, e.g. "BREAKOUT"
 * @param params          resolved parameter values (unknown keys already rejected, missing keys
 *                        already filled with defaults at create time - see ParamSchemaValidator)
 * @param overlays        overlay toggles (e.g. {@code sentimentGate}, {@code regimeGate})
 * @param paramsHash       sha256 of the canonical JSON of (strategyType, params, overlays)
 * @param mode            current live-execution mode
 * @param paperCapital    starting capital for this variant's virtual paper portfolio
 * @param isCurrent       true if this is the current (latest activated) version for variantId
 * @param portfolioAction the recorded intent for this version's activation (null for v1)
 * @param notes           free-text notes
 * @param createdAt       creation timestamp
 */
public record StrategyConfig(
    Long id,
    String variantId,
    int version,
    String strategyType,
    Map<String, Object> params,
    Map<String, Object> overlays,
    String paramsHash,
    StrategyMode mode,
    BigDecimal paperCapital,
    boolean isCurrent,
    PortfolioAction portfolioAction,
    String notes,
    LocalDateTime createdAt
) {
    public StrategyConfig {
        if (variantId == null || variantId.isBlank()) {
            throw new IllegalArgumentException("variantId cannot be null or blank");
        }
        if (strategyType == null || strategyType.isBlank()) {
            throw new IllegalArgumentException("strategyType cannot be null or blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        params = params == null ? Map.of() : Map.copyOf(params);
        overlays = overlays == null ? Map.of() : Map.copyOf(overlays);
        mode = mode == null ? StrategyMode.OFF : mode;
    }

    /** True for the modes that count toward the plan's <=12-active-variants shadow cap. */
    public boolean isActive() {
        return mode == StrategyMode.SHADOW || mode == StrategyMode.CHAMPION;
    }
}
