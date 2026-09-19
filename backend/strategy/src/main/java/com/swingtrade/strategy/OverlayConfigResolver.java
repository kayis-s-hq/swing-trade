package com.swingtrade.strategy;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves a {@link com.swingtrade.domain.StrategyConfig#overlays()} map into the typed overlay
 * configs {@link GateEvaluator} consumes (plan §7.1 orchestrator wiring).
 *
 * <p>Overlay JSON shape: a key ({@code "regimeGate"}/{@code "sentimentGate"}) is present and
 * either {@code true} or a non-empty map to enable that gate with defaults
 * ({@link RegimeGateConfig#defaults()}/{@link SentimentGateConfig#defaults()}); {@code false},
 * absent, or {@code null} disables it. Per-gate parameter overrides are deferred - no variant
 * configured through the API today needs anything other than the default thresholds, and adding
 * override parsing here can be done without touching callers once it's needed.
 */
public final class OverlayConfigResolver {

    private OverlayConfigResolver() {
    }

    public static Optional<RegimeGateConfig> regimeGate(Map<String, Object> overlays) {
        return enabled(overlays, "regimeGate") ? Optional.of(RegimeGateConfig.defaults()) : Optional.empty();
    }

    public static Optional<SentimentGateConfig> sentimentGate(Map<String, Object> overlays) {
        return enabled(overlays, "sentimentGate") ? Optional.of(SentimentGateConfig.defaults()) : Optional.empty();
    }

    private static boolean enabled(Map<String, Object> overlays, String key) {
        if (overlays == null) {
            return false;
        }
        Object value = overlays.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("enabled")) {
                return Boolean.TRUE.equals(map.get("enabled"));
            }
            return !map.isEmpty();
        }
        return false;
    }
}
