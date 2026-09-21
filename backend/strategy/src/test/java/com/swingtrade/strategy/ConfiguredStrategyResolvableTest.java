package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test (remediation plan Step 0.1): every strategy type a config can carry - the
 * {@link SignalStrategy} types (BREAKOUT, PULLBACK, SQUEEZE) and every legacy
 * {@link TradingStrategy} name - must resolve to a runnable strategy through the live
 * {@link StrategyResolver}. Reproduces the "unsupported strategy type PULLBACK (fail-closed)"
 * skip seen when the orchestrator resolved only through the legacy registry.
 */
@DisplayName("Every configurable strategy type resolves for live evaluation")
class ConfiguredStrategyResolvableTest {

    private final StrategyTypeRegistry typeRegistry = new StrategyTypeRegistry(List.of(
        new LegacyPriceActionAdapter(new PriceActionStrategy()), new PullbackStrategy(), new SqueezeStrategy()));
    private final StrategyRegistry legacyRegistry = new StrategyRegistry(
        List.of(new PriceActionStrategy(), new PriceActionConfluenceStrategy(),
            new RelativeStrengthMomentumStrategy()),
        new PriceActionStrategy());
    private final StrategyResolver resolver =
        new StrategyResolver(typeRegistry, legacyRegistry, new ParamSchemaValidator());

    @Test
    void everySignalStrategyTypeResolvesToSignalStrategy() {
        assertThat(typeRegistry.all()).extracting(SignalStrategy::type)
            .containsExactlyInAnyOrder("BREAKOUT", "PULLBACK", "SQUEEZE");
        for (SignalStrategy strategy : typeRegistry.all()) {
            ResolvedStrategy resolved = resolver.resolve(config(strategy.type(), Map.of()));
            assertThat(resolved).as(strategy.type()).isInstanceOf(ResolvedStrategy.Signal.class);
        }
    }

    @Test
    void everyLegacyStrategyNameResolvesToLegacy() {
        assertThat(legacyRegistry.availableNames()).isNotEmpty();
        for (String name : legacyRegistry.availableNames()) {
            ResolvedStrategy resolved = resolver.resolve(config(name, Map.of()));
            assertThat(resolved).as(name).isInstanceOf(ResolvedStrategy.Legacy.class);
        }
    }

    private static StrategyConfig config(String type, Map<String, Object> params) {
        return StrategyConfig.create(type.toLowerCase() + "-v1", 1, type, params, Map.of(),
            StrategyConfig.Mode.SHADOW, BigDecimal.valueOf(100000), true, null, LocalDateTime.now());
    }
}
