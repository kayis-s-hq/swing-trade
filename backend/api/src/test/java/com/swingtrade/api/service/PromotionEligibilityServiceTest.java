package com.swingtrade.api.service;

import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.PromotionEligibilityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionEligibilityServiceTest {
    private StrategyConfigStore store;
    private StrategyConfigRepository repository;
    private PromotionEligibilityService service;

    @BeforeEach
    void setUp() {
        store = mock(StrategyConfigStore.class);
        repository = mock(StrategyConfigRepository.class);
        service = new PromotionEligibilityService(store, repository, new PromotionEligibilityChecker());
    }

    private static StrategyConfig config(String variantId, StrategyConfig.Mode mode, LocalDateTime createdAt) {
        return StrategyConfig.create(variantId, 1, "BREAKOUT", Map.of(), Map.of(), mode,
            BigDecimal.valueOf(500_000), true, null, createdAt);
    }

    @Test
    void returns404EquivalentWhenChallengerVariantUnknown() {
        when(store.findCurrentByVariantId("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.check("GHOST"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GHOST");
    }

    @Test
    void returns404EquivalentWhenNoChampionConfigured() {
        StrategyConfig challenger = config("CHALLENGER", StrategyConfig.Mode.SHADOW, LocalDateTime.now().minusDays(90));
        when(store.findCurrentByVariantId("CHALLENGER")).thenReturn(Optional.of(challenger));
        when(repository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.check("CHALLENGER"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CHAMPION");
    }

    @Test
    void degradedDataYieldsInsufficientSampleAndDocumentsLimitations() {
        StrategyConfig challenger = config("CHALLENGER", StrategyConfig.Mode.SHADOW, LocalDateTime.now().minusDays(90));
        StrategyConfig champion = config("CHAMPION", StrategyConfig.Mode.CHAMPION, LocalDateTime.now().minusYears(1));
        when(store.findCurrentByVariantId("CHALLENGER")).thenReturn(Optional.of(challenger));
        when(repository.findAll()).thenReturn(List.of(
            StrategyConfigEntity.fromDomain(challenger), StrategyConfigEntity.fromDomain(champion)));

        var response = service.check("CHALLENGER");

        assertThat(response.challengerVariantId()).isEqualTo("CHALLENGER");
        assertThat(response.championVariantId()).isEqualTo("CHAMPION");
        // No closed-trade data is attributable per variant yet, so the sample-size gate fails
        // even though tenure alone (90 days) would satisfy condition 1.
        assertThat(response.status()).isEqualTo(PromotionEligibilityChecker.Status.INSUFFICIENT_SAMPLE);
        assertThat(response.dataLimitations()).isNotEmpty();
        assertThat(response.dataLimitations().get(0)).contains("not yet attributable per strategy variant");
    }
}
