package com.swingtrade.api.controller;

import com.swingtrade.api.service.PortfolioBacktestService;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.StrategyRegistry;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BacktestControllerPortfolioTest {
    private final PortfolioBacktestService service = mock(PortfolioBacktestService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BacktestController(
            mock(BacktestEngine.class), mock(StrategyRegistry.class), mock(ObjectMapper.class),
            "reports", service)).build();

    @Test
    void delegatesPortfolioEndpointToApiService() throws Exception {
        when(service.run(any())).thenReturn(null);

        mockMvc.perform(post("/api/backtest/portfolio")
                .contentType("application/json")
                .content("""
                        {"symbols":["TCS","INFY"],"evaluationStart":"2026-01-01","evaluationEnd":"2026-03-31"}
                        """))
                .andExpect(status().isOk());

        verify(service).run(any());
    }

    @Test
    void mapsInvalidRequestToBadRequest() throws Exception {
        when(service.run(any())).thenThrow(new IllegalArgumentException("bad request"));

        mockMvc.perform(post("/api/backtest/portfolio")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
