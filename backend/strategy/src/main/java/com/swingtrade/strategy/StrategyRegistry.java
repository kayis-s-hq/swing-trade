package com.swingtrade.strategy;

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
        Rsi2MeanReversionStrategy.NAME
    );

    private final Map<String, TradingStrategy> strategiesByName;
    private final TradingStrategy defaultStrategy;

    public StrategyRegistry(List<TradingStrategy> strategies, PriceActionStrategy defaultStrategy) {
        this.strategiesByName = strategies.stream()
            .filter(strategy -> EXPLICITLY_ENABLED_NAMES.contains(strategy.name()))
            .collect(Collectors.toMap(TradingStrategy::name, Function.identity()));
        this.defaultStrategy = defaultStrategy;
    }

    /** The compatibility strategy used when no live configuration is active. */
    public TradingStrategy defaultStrategy() {
        return defaultStrategy;
    }

    public Optional<TradingStrategy> find(String name) {
        return Optional.ofNullable(strategiesByName.get(name));
    }

    public List<String> availableNames() {
        return strategiesByName.keySet().stream().sorted().toList();
    }
}
