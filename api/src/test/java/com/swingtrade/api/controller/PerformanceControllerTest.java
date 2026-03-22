package com.swingtrade.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Performance endpoint tests.
 * Tests performance-related API endpoints.
 */
@WebMvcTest(TradingController.class)
class PerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== GET /performance (Get Portfolio Performance) ====================

    @Test
    void testGetPortfolioPerformance_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/trades/performance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").exists())
                .andExpect(jsonPath("$.totalPnL").exists())
                .andExpect(jsonPath("$.winRate").exists())
                .andExpect(jsonPath("$.averageHoldingPeriod").exists());
    }

    @Test
    void testGetPortfolioPerformance_ReturnsValidStructure() throws Exception {
        mockMvc.perform(get("/api/trades/performance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").isNumber())
                .andExpect(jsonPath("$.winRate").isNumber())
                .andExpect(jsonPath("$.averageWinPnL").isNumber())
                .andExpect(jsonPath("$.averageLossPnL").isNumber());
    }

    @Test
    void testGetPortfolioPerformance_ReturnsNonNegativeValues() throws Exception {
        mockMvc.perform(get("/api/trades/performance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.winRate").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.winRate").value(org.hamcrest.Matchers.lessThanOrEqualTo(100)));
    }

    @Test
    void testGetPortfolioPerformance_ServerError_ReturnsInternalServerError() throws Exception {
        mockMvc.perform(get("/api/trades/performance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }
}
