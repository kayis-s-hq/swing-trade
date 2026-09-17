package com.swingtrade.api.service;

import com.swingtrade.domain.PortfolioAction;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.ParamSchemaValidator;
import com.swingtrade.strategy.ParamValidationResult;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyConfigHasher;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates {@link StrategyConfigStore} + param validation + the plan's operator-facing
 * business rules (plan §4.3-§4.5):
 * <ul>
 *   <li>&lt;=12 active (SHADOW|CHAMPION) variants, single CHAMPION - enforced here (409) as
 *       belt-and-suspenders alongside V46's partial unique indexes, which a default H2-profile
 *       test run never exercises (see V46's migration comment)</li>
 *   <li>params_hash dedup: creating a version identical to an existing one returns the existing
 *       one instead of inserting a duplicate</li>
 *   <li>unknown-key rejection / defaults-filled-at-create-time via {@link ParamSchemaValidator}</li>
 * </ul>
 *
 * <p>Mode/param changes here do not themselves trigger anything at request time: the
 * orchestrator (JobOrchestratorService) reading the active-config list happens at job start,
 * which is later-phase wiring (plan §7). This service also does not add a
 * {@code job_runs.strategy_snapshot} column - see this class's note on that in the phase report;
 * it's deferred to that same later phase since nothing would populate it yet.
 */
@Service
public class StrategyConfigService {

    private static final int MAX_ACTIVE_VARIANTS = 12;

    private final StrategyConfigStore store;
    private final StrategyTypeRegistry typeRegistry;
    private final ParamSchemaValidator validator;
    private final StrategyConfigHasher hasher;
    private final PaperPortfolioService paperPortfolioService;

    public StrategyConfigService(
        StrategyConfigStore store,
        StrategyTypeRegistry typeRegistry,
        ParamSchemaValidator validator,
        StrategyConfigHasher hasher,
        PaperPortfolioService paperPortfolioService
    ) {
        this.store = store;
        this.typeRegistry = typeRegistry;
        this.validator = validator;
        this.hasher = hasher;
        this.paperPortfolioService = paperPortfolioService;
    }

    public List<SignalStrategy> listTypes() {
        return typeRegistry.all();
    }

    public List<StrategyConfig> listCurrent() {
        return store.findAllCurrent();
    }

    public List<StrategyConfig> listVersions(String variantId) {
        List<StrategyConfig> versions = store.findVersions(variantId);
        if (versions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown variant: " + variantId);
        }
        return versions;
    }

    public ParamValidationResult validateParams(String strategyType, Map<String, Object> rawParams) {
        SignalStrategy strategy = requireType(strategyType);
        return validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), rawParams);
    }

    public StrategyConfig createVariant(
        String variantId, String strategyType, Map<String, Object> rawParams,
        Map<String, Object> overlays, BigDecimal paperCapital, String notes
    ) {
        if (store.findCurrent(variantId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Variant already exists: " + variantId);
        }
        ParamValidationResult validation = validateOrThrow(strategyType, rawParams);
        Map<String, Object> resolvedOverlays = overlays == null ? Map.of() : overlays;
        String hash = hasher.hash(strategyType, validation.resolvedParams(), resolvedOverlays);

        StrategyConfig config = new StrategyConfig(
            null, variantId, 1, strategyType, validation.resolvedParams(), resolvedOverlays, hash,
            StrategyMode.OFF, paperCapital == null ? new BigDecimal("500000") : paperCapital,
            true, null, notes, LocalDateTime.now());
        return store.save(config);
    }

    public StrategyConfig createVersion(
        String variantId, Map<String, Object> rawParams, Map<String, Object> overlays,
        BigDecimal paperCapital, String portfolioActionRaw, String notes
    ) {
        StrategyConfig current = store.findCurrent(variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown variant: " + variantId));

        Map<String, Object> effectiveParams = rawParams == null ? current.params() : rawParams;
        Map<String, Object> effectiveOverlays = overlays == null ? current.overlays() : overlays;
        ParamValidationResult validation = validateOrThrow(current.strategyType(), effectiveParams);

        String hash = hasher.hash(current.strategyType(), validation.resolvedParams(), effectiveOverlays);
        Optional<StrategyConfig> existing = store.findByParamsHash(variantId, hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Default to RESET for param changes affecting entries, per plan §4.5's suggestion;
        // callers can pass CONTINUE explicitly.
        PortfolioAction portfolioAction = portfolioActionRaw == null
            ? PortfolioAction.RESET
            : PortfolioAction.valueOf(portfolioActionRaw);

        StrategyConfig next = new StrategyConfig(
            null, variantId, current.version() + 1, current.strategyType(), validation.resolvedParams(),
            effectiveOverlays, hash, current.mode(),
            paperCapital == null ? current.paperCapital() : paperCapital,
            true, portfolioAction, notes, LocalDateTime.now());
        return store.save(next);
    }

    public StrategyConfig clone(String variantId, String newVariantId, String notes) {
        if (store.findCurrent(newVariantId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Variant already exists: " + newVariantId);
        }
        StrategyConfig source = store.findCurrent(variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown variant: " + variantId));

        String hash = hasher.hash(source.strategyType(), source.params(), source.overlays());
        StrategyConfig cloned = new StrategyConfig(
            null, newVariantId, 1, source.strategyType(), source.params(), source.overlays(), hash,
            StrategyMode.OFF, source.paperCapital(), true, null, notes, LocalDateTime.now());
        return store.save(cloned);
    }

    public StrategyConfig changeMode(String variantId, String modeRaw, boolean confirm) {
        StrategyConfig current = store.findCurrent(variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown variant: " + variantId));
        StrategyMode newMode = parseMode(modeRaw);

        if (newMode == StrategyMode.CHAMPION) {
            if (!confirm) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting CHAMPION requires confirm=true");
            }
            Optional<StrategyConfig> existingChampion = store.findCurrentChampion();
            if (existingChampion.isPresent() && !existingChampion.get().variantId().equals(variantId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another variant is already CHAMPION: " + existingChampion.get().variantId());
            }
        }

        if ((newMode == StrategyMode.SHADOW || newMode == StrategyMode.CHAMPION) && !current.isActive()) {
            long activeNow = store.countActive();
            if (activeNow >= MAX_ACTIVE_VARIANTS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot activate " + variantId + ": already at the max of " + MAX_ACTIVE_VARIANTS + " active variants");
            }
        }

        store.updateMode(variantId, newMode);

        // Plan §7.2: a paper_trading_portfolio row per variant, created on first SHADOW (or
        // CHAMPION) activation with the variant's paper_capital. ensurePortfolio() is idempotent,
        // so a later re-activation of an already-provisioned variant is a no-op here.
        if (newMode == StrategyMode.SHADOW || newMode == StrategyMode.CHAMPION) {
            paperPortfolioService.ensurePortfolio(variantId, current.paperCapital());
        }

        return store.findCurrent(variantId).orElseThrow();
    }

    private ParamValidationResult validateOrThrow(String strategyType, Map<String, Object> rawParams) {
        SignalStrategy strategy = requireType(strategyType);
        ParamValidationResult result = validator.validate(strategy.paramSchema(), strategy.crossFieldRules(), rawParams);
        if (!result.valid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", result.errors()));
        }
        return result;
    }

    private SignalStrategy requireType(String strategyType) {
        return typeRegistry.findByType(strategyType)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown strategy type: " + strategyType));
    }

    private StrategyMode parseMode(String modeRaw) {
        try {
            return StrategyMode.valueOf(modeRaw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown mode: " + modeRaw);
        }
    }
}
