package com.swingtrade.api.service;

import com.swingtrade.api.dto.PromotionEligibilityResponse;
import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.PromotionEligibilityChecker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Assembles real inputs from main's current data model and runs
 * {@link PromotionEligibilityChecker} for a champion/challenger pair.
 *
 * <p><b>Known data-model gap (as of this port):</b> main's {@code PositionEntity} carries no
 * variant/config linkage (no {@code variantId} or {@code configVersion} column), so closed
 * paper-trade P&amp;L cannot yet be attributed to a specific strategy variant. There is also no
 * persisted {@code WalkForwardStabilityEvaluator.Result} for any variant yet (no experiment-log
 * table exists on main today). Both inputs therefore degrade to empty/absent here rather than
 * being backfilled with data that would misrepresent the champion or challenger's real track
 * record. Only SHADOW-tenure (derived from the challenger's current, immutable
 * {@code StrategyConfig} version's {@code createdAt}) is genuinely computed from real data. A
 * separate effort building live per-variant shadow paper-trading execution would need to add
 * that linkage before the expectancy/drawdown/walk-forward conditions here can use real
 * per-variant data; this service is written so that once such data exists, only the trade/
 * walk-forward lookup here needs to change; the checker and endpoint contract do not.</p>
 */
@Service
public class PromotionEligibilityService {

    private final StrategyConfigStore strategyConfigStore;
    private final StrategyConfigRepository strategyConfigRepository;
    private final PromotionEligibilityChecker checker;

    public PromotionEligibilityService(StrategyConfigStore strategyConfigStore,
                                       StrategyConfigRepository strategyConfigRepository,
                                       PromotionEligibilityChecker checker) {
        this.strategyConfigStore = strategyConfigStore;
        this.strategyConfigRepository = strategyConfigRepository;
        this.checker = checker;
    }

    public PromotionEligibilityResponse check(String challengerVariantId) {
        String normalized = normalize(challengerVariantId);
        StrategyConfig challenger = strategyConfigStore.findCurrentByVariantId(normalized)
            .orElseThrow(() -> new IllegalArgumentException("Strategy variant not found: " + challengerVariantId));
        StrategyConfig champion = findCurrentChampion()
            .orElseThrow(() -> new IllegalArgumentException("No current CHAMPION strategy is configured"));

        long tenureDays = Duration.between(challenger.createdAt(), LocalDateTime.now()).toDays();

        PromotionEligibilityChecker.Input input = new PromotionEligibilityChecker.Input(
            tenureDays,
            List.<BigDecimal>of(),
            List.<BigDecimal>of(),
            0.0,
            0.0,
            null,
            null,
            null
        );
        PromotionEligibilityChecker.Result result = checker.evaluate(input);

        List<String> limitations = List.of(
            "Closed-trade P&L is not yet attributable per strategy variant (positions carry no "
                + "variant/config linkage on main today), so expectancy and max-drawdown inputs are "
                + "empty rather than measured; the corresponding conditions cannot be met until that "
                + "linkage exists.",
            "No persisted walk-forward run exists yet for any variant, so the walk-forward "
                + "significance condition reports 'no data yet' rather than a real result.",
            "Only SHADOW tenure (from the challenger's current StrategyConfig version's createdAt) "
                + "is computed from real, per-variant data."
        );

        return new PromotionEligibilityResponse(
            challenger.variantId(),
            champion.variantId(),
            result.status(),
            result.conditions(),
            result.notes(),
            limitations
        );
    }

    private java.util.Optional<StrategyConfig> findCurrentChampion() {
        return strategyConfigRepository.findAll().stream()
            .map(StrategyConfigEntity::toDomain)
            .filter(config -> config.current() && config.mode() == StrategyConfig.Mode.CHAMPION)
            .findFirst();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("variantId is required");
        return value.trim();
    }
}
