package com.swingtrade.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Looks up a {@link TradingStrategy} by name for backtest/testing use. Spring supplies
 * every {@code TradingStrategy} bean here automatically - adding a new strategy means
 * registering another {@code @Component} implementation, nothing else.
 *
 * <p>Only backtest/testing paths (e.g. {@code BacktestController}) select a strategy by
 * name through this registry. Live signal generation and the daily job orchestration stay
 * pinned to {@link #defaultStrategy()}.
 */
@Component
public class StrategyRegistry {

    private final Map<String, TradingStrategy> strategiesByName;
    private final TradingStrategy defaultStrategy;

    public StrategyRegistry(List<TradingStrategy> strategies, PriceActionStrategy defaultStrategy) {
        this.strategiesByName = strategies.stream()
            .collect(Collectors.toMap(TradingStrategy::name, Function.identity()));
        this.defaultStrategy = defaultStrategy;
    }

    /** The strategy live signal generation and job orchestration are pinned to. */
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
