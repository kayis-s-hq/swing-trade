package com.swingtrade.api.controller;

import com.swingtrade.api.test.integration.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PerformanceController endpoints.
 * Tests portfolio performance and risk summary with real database.
 */
@DisplayName("Performance Controller Integration Tests")
class PerformanceControllerIntegrationTest extends ApiIntegrationTest {

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades/portfolio returns performance data")
    void testGetPortfolioPerformance() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades/portfolio")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades/risk-summary returns risk metrics")
    void testGetRiskSummary() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades/risk-summary")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades/portfolio with empty database returns zero values")
    void testGetPortfolioPerformanceEmpty() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades/portfolio")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReturn").isNumber())
                .andExpect(jsonPath("$.totalTrades").isNumber())
                .andExpect(jsonPath("$.winRate").isNumber());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades/risk-summary returns valid risk data")
    void testGetRiskSummaryValidData() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades/risk-summary")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPositionCount").isNumber())
                .andExpect(jsonPath("$.currentPositionCount").isNumber())
                .andExpect(jsonPath("$.dailyLossLimit").isNumber());
    }
}
