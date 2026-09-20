package com.swingtrade.api.service;

import com.swingtrade.api.dto.PortfolioBacktestRequest;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.PortfolioBacktestResult;
import com.swingtrade.strategy.StrategyRegistry;
import com.swingtrade.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioBacktestServiceTest {
    private final BacktestEngine engine = mock(BacktestEngine.class);
    private final StrategyRegistry registry = mock(StrategyRegistry.class);
    private final TradingStrategy strategy = mock(TradingStrategy.class);
    private final PortfolioBacktestService service = new PortfolioBacktestService(engine, registry);

    @Test
    void normalizesSymbolsAndUsesDefaultsBeforeCallingEngine() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        PortfolioBacktestResult result = new PortfolioBacktestResult(
                start, end, java.math.BigDecimal.valueOf(500_000), java.math.BigDecimal.valueOf(510_000), java.math.BigDecimal.valueOf(2), java.math.BigDecimal.valueOf(-1), 1, 8, 1.2, 2, 2, 1, 0, List.of(), List.of());
        when(registry.defaultStrategy()).thenReturn(strategy);
        when(engine.runPortfolioBacktest(any(), any(), any(), any(), any(), any())).thenReturn(result);

        var response = service.run(new PortfolioBacktestRequest(
                List.of(" tcs ", "INFY", "TCS"), "", start, end, null, null));

        assertEquals(0, java.math.BigDecimal.valueOf(510_000).compareTo(response.finalCapital()));
        verify(engine).runPortfolioBacktest(eq(List.of("TCS", "INFY")), eq("NSE"),
                any(BacktestConfig.class), eq(strategy), eq(start), eq(end));
    }

    @Test
    void rejectsMissingOrReversedEvaluationWindow() {
        assertThrows(IllegalArgumentException.class, () -> service.run(
                new PortfolioBacktestRequest(List.of("TCS"), "NSE", null, LocalDate.now(), null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.run(
                new PortfolioBacktestRequest(List.of("TCS"), "NSE",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null)));
    }
}
