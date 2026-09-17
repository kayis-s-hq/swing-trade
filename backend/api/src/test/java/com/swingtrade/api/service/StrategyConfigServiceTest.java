package com.swingtrade.api.service;

import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.LegacyPriceActionAdapter;
import com.swingtrade.strategy.ParamSchemaValidator;
import com.swingtrade.strategy.PriceActionStrategy;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyConfigHasher;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyConfigServiceTest {

    @Mock
    private StrategyConfigStore store;

    @Mock
    private PaperPortfolioService paperPortfolioService;

    private StrategyConfigService service;

    @BeforeEach
    void setUp() {
        SignalStrategy breakout = new LegacyPriceActionAdapter(mock(PriceActionStrategy.class));
        StrategyTypeRegistry registry = new StrategyTypeRegistry(List.of(breakout));
        ParamSchemaValidator validator = new ParamSchemaValidator();
        StrategyConfigHasher hasher = new StrategyConfigHasher(new ObjectMapper());
        service = new StrategyConfigService(store, registry, validator, hasher, paperPortfolioService);
    }

    private StrategyConfig config(String variantId, int version, StrategyMode mode) {
        return new StrategyConfig(
            1L, variantId, version, "BREAKOUT", Map.of("emaFast", 20, "emaSlow", 50), Map.of(),
            "hash", mode, new BigDecimal("500000"), true, null, null, LocalDateTime.now());
    }

    @Test
    void createVariantRejectsUnknownStrategyType() {
        when(store.findCurrent("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVariant("X", "NOT_A_TYPE", Map.of(), Map.of(), null, null))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createVariantRejectsDuplicateVariantId() {
        when(store.findCurrent("BREAKOUT_STRICT")).thenReturn(Optional.of(config("BREAKOUT_STRICT", 1, StrategyMode.CHAMPION)));

        assertThatThrownBy(() -> service.createVariant(
            "BREAKOUT_STRICT", "BREAKOUT", Map.of(), Map.of(), null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void changeModeToChampionRequiresConfirm() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)));

        assertThatThrownBy(() -> service.changeMode("V1", "CHAMPION", false))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
    }

    @Test
    void changeModeToChampionRejects409WhenAnotherChampionExists() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)));
        when(store.findCurrentChampion()).thenReturn(Optional.of(config("OTHER", 1, StrategyMode.CHAMPION)));

        assertThatThrownBy(() -> service.changeMode("V1", "CHAMPION", true))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void changeModeToShadowRejects409WhenAtActiveCap() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)));
        when(store.countActive()).thenReturn(12L);

        assertThatThrownBy(() -> service.changeMode("V1", "SHADOW", false))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void changeModeToShadowSucceedsUnderCap() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)))
            .thenReturn(Optional.of(config("V1", 1, StrategyMode.SHADOW)));
        when(store.countActive()).thenReturn(3L);

        StrategyConfig result = service.changeMode("V1", "SHADOW", false);

        assertThat(result.mode()).isEqualTo(StrategyMode.SHADOW);
    }

    @Test
    void changeModeToShadowProvisionsPaperPortfolio() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)))
            .thenReturn(Optional.of(config("V1", 1, StrategyMode.SHADOW)));
        when(store.countActive()).thenReturn(3L);

        service.changeMode("V1", "SHADOW", false);

        verify(paperPortfolioService).ensurePortfolio("V1", new BigDecimal("500000"));
    }

    @Test
    void changeModeToChampionProvisionsPaperPortfolio() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)))
            .thenReturn(Optional.of(config("V1", 1, StrategyMode.CHAMPION)));
        when(store.countActive()).thenReturn(3L);
        when(store.findCurrentChampion()).thenReturn(Optional.empty());

        service.changeMode("V1", "CHAMPION", true);

        verify(paperPortfolioService).ensurePortfolio("V1", new BigDecimal("500000"));
    }

    @Test
    void changeModeToOffDoesNotProvisionPaperPortfolio() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.SHADOW)))
            .thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)));

        service.changeMode("V1", "OFF", false);

        verify(paperPortfolioService, never()).ensurePortfolio(anyString(), any());
    }

    @Test
    void createVersionDedupesOnIdenticalParamsHash() {
        StrategyConfig current = config("V1", 1, StrategyMode.OFF);
        when(store.findCurrent("V1")).thenReturn(Optional.of(current));
        when(store.findByParamsHash(anyString(), anyString())).thenReturn(Optional.of(current));

        StrategyConfig result = service.createVersion("V1", Map.of("emaFast", 20, "emaSlow", 50), Map.of(), null, null, null);

        assertThat(result.version()).isEqualTo(1);
    }

    @Test
    void createVersionRejectsInvalidParams() {
        when(store.findCurrent("V1")).thenReturn(Optional.of(config("V1", 1, StrategyMode.OFF)));

        assertThatThrownBy(() -> service.createVersion("V1", Map.of("emaFast", 1000), Map.of(), null, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
    }
}
