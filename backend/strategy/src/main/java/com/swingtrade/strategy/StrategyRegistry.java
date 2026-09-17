package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Looks up a {@link TradingStrategy} by name for backtest/testing use. Spring supplies
 * every {@code TradingStrategy} bean here automatically - adding a new strategy means
 * registering another {@code @Component} implementation, nothing else.
 *
     * <p>Backtest paths and configured live orchestration select a strategy by name through
     * this registry. The default remains the compatibility fallback for unconfigured installs.
 */
@Component
public class StrategyRegistry {

    private static final Set<String> EXPLICITLY_ENABLED_NAMES = Set.of(
        PriceActionStrategy.NAME,
        PriceActionConfluenceStrategy.NAME,
        PullbackInUptrendStrategy.NAME,
        VolatilitySqueezeStrategy.NAME,
        FiftyTwoWeekHighBreakoutStrategy.NAME,
        Rsi2MeanReversionStrategy.NAME,
        RelativeStrengthMomentumStrategy.NAME
    );

    private final Map<String, TradingStrategy> strategiesByName;
    private final TradingStrategy defaultStrategy;
    private final PriceActionConfluenceStrategy priceActionConfluenceStrategy;

    @org.springframework.beans.factory.annotation.Autowired
    public StrategyRegistry(List<TradingStrategy> strategies, PriceActionStrategy defaultStrategy,
                            PriceActionConfluenceStrategy priceActionConfluenceStrategy) {
        this.strategiesByName = strategies.stream()
            .filter(strategy -> EXPLICITLY_ENABLED_NAMES.contains(strategy.name()))
            .collect(Collectors.toMap(TradingStrategy::name, Function.identity()));
        this.defaultStrategy = defaultStrategy;
        this.priceActionConfluenceStrategy = priceActionConfluenceStrategy;
    }

    /** Compatibility constructor for unit tests and callers that only register the default bean. */
    public StrategyRegistry(List<TradingStrategy> strategies, PriceActionStrategy defaultStrategy) {
        this(strategies, defaultStrategy, new PriceActionConfluenceStrategy());
    }

    /** The compatibility strategy used when no live configuration is active. */
    public TradingStrategy defaultStrategy() {
        return defaultStrategy;
    }

    public Optional<TradingStrategy> find(String name) {
        return Optional.ofNullable(strategiesByName.get(name));
    }

    /**
     * Resolves a persisted strategy configuration, applying only parameters with an explicit
     * strategy-owned parser. Unknown strategy types remain fail-closed through an empty result.
     */
    public Optional<TradingStrategy> resolve(StrategyConfig config) {
        if (config == null) return Optional.empty();
        if (PriceActionConfluenceStrategy.NAME.equals(config.strategyType())) {
            // Build a request-scoped instance so one variant's RSI range cannot leak into another.
            return Optional.of(PriceActionConfluenceStrategy.fromParameters(config.params()));
        }
        if (RelativeStrengthMomentumStrategy.NAME.equals(config.strategyType())) {
            // Build a request-scoped instance so one variant's RS threshold cannot leak into another.
            return Optional.of(RelativeStrengthMomentumStrategy.fromParameters(config.params()));
        }
        return find(config.strategyType());
    }

    public List<String> availableNames() {
        return strategiesByName.keySet().stream().sorted().toList();
    }
}
