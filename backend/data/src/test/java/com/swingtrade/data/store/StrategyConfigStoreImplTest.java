package com.swingtrade.data.store;

import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategyConfigStoreImplTest {
    private static final StrategyConfig CONFIG = StrategyConfig.create("v1", 1, "momentum",
        Map.of("lookback", 20), Map.of(), StrategyConfig.Mode.SHADOW, BigDecimal.valueOf(100_000),
        true, "test", LocalDateTime.of(2026, 1, 1, 12, 0));

    @Test
    void readsVersionsAndSavesNewConfig() {
        StrategyConfigRepository repository = mock(StrategyConfigRepository.class);
        StrategyConfigEntity entity = StrategyConfigEntity.fromDomain(CONFIG);
        when(repository.findByVariantIdAndCurrentTrue("v1")).thenReturn(Optional.of(entity));
        when(repository.findByVariantIdAndVersion("v1", 1)).thenReturn(Optional.of(entity));
        when(repository.findByVariantIdOrderByVersionDesc("v1")).thenReturn(List.of(entity));
        when(repository.save(any(StrategyConfigEntity.class))).thenReturn(entity);
        StrategyConfigStoreImpl store = new StrategyConfigStoreImpl(repository);

        assertThat(store.findCurrentByVariantId("v1")).contains(CONFIG);
        assertThat(store.findByVariantIdAndVersion("v1", 1)).contains(CONFIG);
        assertThat(store.findByVariantId("v1")).containsExactly(CONFIG);
        assertThat(store.save(CONFIG)).isEqualTo(CONFIG);
        verify(repository).save(any(StrategyConfigEntity.class));
    }

    @Test
    void rejectsUpdatesToExistingConfig() {
        StrategyConfig existing = new StrategyConfig(7L, CONFIG.variantId(), CONFIG.version(), CONFIG.strategyType(),
            CONFIG.params(), CONFIG.overlays(), CONFIG.paramsHash(), CONFIG.mode(), CONFIG.paperCapital(),
            CONFIG.current(), CONFIG.notes(), CONFIG.createdAt());
        StrategyConfigRepository repository = mock(StrategyConfigRepository.class);

        assertThatThrownBy(() -> new StrategyConfigStoreImpl(repository).save(existing))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("append-only");
    }
}
