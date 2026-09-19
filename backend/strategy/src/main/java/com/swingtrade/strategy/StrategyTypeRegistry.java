package com.swingtrade.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Looks up registered {@link SignalStrategy} beans by {@link SignalStrategy#type()} - backs
 * {@code GET /api/strategy-types} and param validation (plan §4.4). Phase 2 has exactly one
 * registered type ({@code BREAKOUT}, via {@link LegacyPriceActionAdapter}); PULLBACK/SQUEEZE/
 * RS_NIFTY are added as {@link SignalStrategy} beans in a later phase with no change needed
 * here.
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
