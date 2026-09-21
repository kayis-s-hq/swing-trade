package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StrategyResolver")
class StrategyResolverTest {

    private final StrategyTypeRegistry typeRegistry = new StrategyTypeRegistry(List.of(
        new LegacyPriceActionAdapter(new PriceActionStrategy()), new PullbackStrategy(), new SqueezeStrategy()));
    private final StrategyRegistry legacyRegistry = new StrategyRegistry(
        List.of(new PriceActionStrategy(), new PriceActionConfluenceStrategy()), new PriceActionStrategy());
    private final StrategyResolver resolver =
        new StrategyResolver(typeRegistry, legacyRegistry, new ParamSchemaValidator());

    @Test
    void signalStrategyTypeResolvesWithDefaultsFilledIn() {
        ResolvedStrategy resolved = resolver.resolve(config("PULLBACK", Map.of("trendEma", 60)));

        assertThat(resolved).isInstanceOf(ResolvedStrategy.Signal.class);
        var signal = (ResolvedStrategy.Signal) resolved;
        assertThat(signal.strategy().type()).isEqualTo("PULLBACK");
        assertThat(signal.params().getInt("trendEma")).isEqualTo(60);
        assertThat(signal.params().getInt("pullbackEma")).isEqualTo(20);
    }

    @Test
    void signalStrategyTypesAndLegacyNamesBothResolve() {
        assertThat(resolver.resolve(config("BREAKOUT", Map.of()))).isInstanceOf(ResolvedStrategy.Signal.class);
        assertThat(resolver.resolve(config(PriceActionStrategy.NAME, Map.of())))
            .isInstanceOf(ResolvedStrategy.Legacy.class);
    }

    @Test
    void unknownTypeIsTypedUnresolvedWithReason() {
        ResolvedStrategy resolved = resolver.resolve(config("NOPE", Map.of()));

        assertThat(resolved).isInstanceOf(ResolvedStrategy.Unresolved.class);
        assertThat(((ResolvedStrategy.Unresolved) resolved).reason()).contains("NOPE");
    }

    @Test
    void invalidParamsAreUnresolvedNotThrown() {
        ResolvedStrategy unknownKey = resolver.resolve(config("PULLBACK", Map.of("bogus", 1)));
        ResolvedStrategy outOfRange = resolver.resolve(config("PULLBACK", Map.of("trendEma", 100000)));

        assertThat(unknownKey).isInstanceOf(ResolvedStrategy.Unresolved.class);
        assertThat(((ResolvedStrategy.Unresolved) unknownKey).reason()).contains("bogus");
        assertThat(outOfRange).isInstanceOf(ResolvedStrategy.Unresolved.class);
    }

    @Test
    void nullConfigIsUnresolved() {
        assertThat(resolver.resolve(null)).isInstanceOf(ResolvedStrategy.Unresolved.class);
    }

    private static StrategyConfig config(String type, Map<String, Object> params) {
        return StrategyConfig.create(type.toLowerCase() + "-v1", 1, type, params, Map.of(),
            StrategyConfig.Mode.SHADOW, BigDecimal.valueOf(100000), true, null, LocalDateTime.now());
    }
}
