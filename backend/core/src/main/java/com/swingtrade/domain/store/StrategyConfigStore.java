package com.swingtrade.domain.store;

import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link StrategyConfig} (plan §4.1/§4.4). Implemented in the {@code data}
 * module ({@code StrategyConfigStoreImpl}); orchestrated from the {@code api} module's
 * {@code StrategyConfigService}/{@code StrategyConfigController}.
 */
public interface StrategyConfigStore {

    /** The current (is_current=true) version of a variant, if it exists. */
    Optional<StrategyConfig> findCurrent(String variantId);

    /** The current version of every variant, regardless of mode. */
    List<StrategyConfig> findAllCurrent();

    /** Full version history for a variant, oldest first. */
    List<StrategyConfig> findVersions(String variantId);

    /** A specific version of a variant, if it exists. */
    Optional<StrategyConfig> findVersion(String variantId, int version);

    /**
     * Any version of the given variant (current or historical) whose params_hash matches - used
     * to dedupe identical param sets at create time (plan §4.3: "creating a version identical to
     * an existing one returns the existing one").
     */
    Optional<StrategyConfig> findByParamsHash(String variantId, String paramsHash);

    /**
     * Appends a brand-new version row for {@code config.variantId()} and flips {@code is_current}
     * off on the previous current row (both within one transaction). {@code config.version()}
     * must be exactly one greater than the previous current version's (or 1, for a new variant).
     */
    StrategyConfig save(StrategyConfig config);

    /** Changes the mode of the current version of a variant, recording an audit row. */
    void updateMode(String variantId, StrategyMode newMode);

    /** Count of current versions whose mode is SHADOW or CHAMPION (the plan's shadow cap). */
    long countActive();

    /** The current version whose mode is CHAMPION, if any (at most one can exist). */
    Optional<StrategyConfig> findCurrentChampion();
}
