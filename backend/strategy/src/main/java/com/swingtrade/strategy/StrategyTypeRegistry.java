package com.swingtrade.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Looks up registered {@link SignalStrategy} beans by {@link SignalStrategy#type()}. Backs
 * {@code GET /api/strategy-types} ({@code StrategyTypeController}: type, param schema, warmup
 * bars, required indicators), param validation and {@link StrategyResolver}. Registered types
 * today: {@code BREAKOUT} ({@link LegacyPriceActionAdapter}), {@code PULLBACK}
 * ({@link PullbackStrategy}) and {@code SQUEEZE} ({@link SqueezeStrategy}); a new type is just a
 * new {@link SignalStrategy} bean, with no change needed here.
 */
@Component
public class StrategyTypeRegistry {

    private final Map<String, SignalStrategy> byType;

    public StrategyTypeRegistry(List<SignalStrategy> strategies) {
        this.byType = strategies.stream()
            .collect(Collectors.toUnmodifiableMap(SignalStrategy::type, s -> s));
    }

    public Optional<SignalStrategy> findByType(String type) {
        return Optional.ofNullable(byType.get(type));
    }

    public List<SignalStrategy> all() {
        return List.copyOf(byType.values());
    }
}
