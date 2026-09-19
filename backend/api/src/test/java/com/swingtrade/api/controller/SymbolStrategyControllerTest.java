package com.swingtrade.api.controller;

import com.swingtrade.api.service.SignalArbiter;
import com.swingtrade.domain.ShadowPositionView;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SymbolStrategyControllerTest {

    private static ShadowPositionView position(String portfolio, String status, String pnl) {
        return new ShadowPositionView(portfolio, "SBIN", LocalDate.of(2026, 9, 1), new BigDecimal("100"),
            new BigDecimal("95"), new BigDecimal("110"), 10, status,
            "CLOSED".equals(status) ? LocalDate.of(2026, 9, 5) : null, null, null,
            pnl == null ? null : new BigDecimal(pnl));
    }

    @Test
    void aggregatesClosedTradesOpenPositionAndSelectedBook() {
        StrategyConfigStore configs = mock(StrategyConfigStore.class);
        SignalStore signals = mock(SignalStore.class);
        PaperPortfolioService portfolios = mock(PaperPortfolioService.class);
        SignalArbiter arbiter = mock(SignalArbiter.class);

        StrategyConfig config = new StrategyConfig(1L, "breakout-v1", 1, "BREAKOUT", Map.of(), Map.of(), "hash",
            StrategyMode.SHADOW, new BigDecimal("100000"), true, null, null, LocalDateTime.now());
        when(configs.findAllCurrent()).thenReturn(List.of(config));
        when(signals.findLatestBySymbolAndStrategy(anyString(), anyString())).thenReturn(Optional.empty());
        when(portfolios.listShadowPositionsForSymbol("SBIN")).thenReturn(List.of(
            position("breakout-v1", "OPEN", null),
            position("breakout-v1", "CLOSED", "50"),
            position("breakout-v1", "CLOSED", "-20"),
            position("selected", "CLOSED", "30")));
        when(arbiter.findForSymbol("SBIN")).thenReturn(List.of());

        var response = new SymbolStrategyController(configs, signals, portfolios, arbiter).matrix("SBIN");

        var breakout = response.strategies().get(0);
        assertThat(breakout.variantId()).isEqualTo("breakout-v1");
        assertThat(breakout.openPosition()).isNotNull();
        assertThat(breakout.closedTrades()).isEqualTo(2);
        assertThat(breakout.realizedPnl()).isEqualByComparingTo("30");
        assertThat(breakout.winRate()).isEqualTo(0.5);

        var selected = response.strategies().get(1);
        assertThat(selected.variantId()).isEqualTo("selected");
        assertThat(selected.closedTrades()).isEqualTo(1);
        assertThat(selected.latestSignal()).isNull();
    }
}
