package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single entry point that turns a persisted {@link StrategyConfig} into a runnable strategy for
 * live evaluation. Lookup order: {@link StrategyTypeRegistry} ({@link SignalStrategy} types such
 * as BREAKOUT/PULLBACK/SQUEEZE), then the legacy {@link StrategyRegistry} ({@link TradingStrategy}
 * names). Never returns {@code null} and never throws for bad input - see
 * {@link ResolvedStrategy.Unresolved}.
 */
@Component
public class StrategyResolver {

    private final StrategyTypeRegistry typeRegistry;
    private final StrategyRegistry legacyRegistry;
    private final ParamSchemaValidator paramValidator;

    public StrategyResolver(StrategyTypeRegistry typeRegistry, StrategyRegistry legacyRegistry,
                            ParamSchemaValidator paramValidator) {
        this.typeRegistry = typeRegistry;
        this.legacyRegistry = legacyRegistry;
        this.paramValidator = paramValidator;
    }

    public ResolvedStrategy resolve(StrategyConfig config) {
        if (config == null) {
            return new ResolvedStrategy.Unresolved("no strategy config supplied");
        }
        String type = config.strategyType();
        Optional<SignalStrategy> signalStrategy = typeRegistry.findByType(type);
        if (signalStrategy.isPresent()) {
            return resolveSignalStrategy(signalStrategy.get(), config);
        }
        try {
            Optional<TradingStrategy> legacy = legacyRegistry.resolve(config);
            if (legacy.isPresent()) {
                return new ResolvedStrategy.Legacy(legacy.get());
            }
        } catch (IllegalArgumentException e) {
            return new ResolvedStrategy.Unresolved("invalid parameters for " + type + ": " + e.getMessage());
        }
        return new ResolvedStrategy.Unresolved("unsupported strategy type " + type);
    }

    private ResolvedStrategy resolveSignalStrategy(SignalStrategy strategy, StrategyConfig config) {
        ParamValidationResult validation = paramValidator.validate(
            strategy.paramSchema(), strategy.crossFieldRules(), config.params());
        if (!validation.valid()) {
            return new ResolvedStrategy.Unresolved("invalid parameters for " + strategy.type() + ": "
                + String.join("; ", validation.errors()));
        }
        StrategyParamsView params = StrategyParamsView.of(validation.resolvedParams());
        boolean needsIndex = strategy.requiredIndicators(params).stream()
            .anyMatch(key -> key == IndicatorKey.INDEX_CLOSE || key == IndicatorKey.INDEX_EMA);
        if (needsIndex) {
            return new ResolvedStrategy.Unresolved(
                strategy.type() + " requires an index series, which live evaluation does not supply yet");
        }
        return new ResolvedStrategy.Signal(strategy, params, validation.resolvedParams());
    }
}
