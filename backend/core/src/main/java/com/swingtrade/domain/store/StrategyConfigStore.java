package com.swingtrade.domain.store;

import com.swingtrade.domain.StrategyConfig;

import java.util.List;
import java.util.Optional;

/** Persistence port for append-only, versioned strategy configurations. */
public interface StrategyConfigStore {
    Optional<StrategyConfig> findCurrentByVariantId(String variantId);

    Optional<StrategyConfig> findByVariantIdAndVersion(String variantId, int version);

    List<StrategyConfig> findByVariantId(String variantId);

    StrategyConfig save(StrategyConfig config);
}
