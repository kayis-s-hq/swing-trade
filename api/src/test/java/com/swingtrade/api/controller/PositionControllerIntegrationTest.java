package com.swingtrade.api.controller;

import com.swingtrade.api.test.integration.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PositionController endpoints.
 * Tests position listing, filtering, and status queries.
 */
@DisplayName("PositionController Integration Tests")
class PositionControllerIntegrationTest extends ApiIntegrationTest {

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions lists open positions")
    void testGetOpenPositions() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/{symbol} gets position by symbol")
    void testGetPositionBySymbol() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/RELIANCE")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/closed shows closed positions")
    void testGetClosedPositions() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/closed")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/status/{status} filters by status")
    void testGetPositionsByStatus() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/status/OPEN")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/status/INVALID returns 400")
    void testGetPositionsByInvalidStatus() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/status/INVALID_STATUS")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/stats returns position statistics")
    void testGetPositionStats() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/stats")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/sector-allocation returns allocation")
    void testGetSectorAllocation() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/sector-allocation")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/{nonexistent} returns 404")
    void testGetNonExistentPosition() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/NOSUCHSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/positions/{symbol}/close closes position")
    void testClosePosition() throws Exception {
        // Act
        mockMvc.perform(post("/api/positions/RELIANCE/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/positions/{nonexistent}/close returns 404")
    void testCloseNonExistentPosition() throws Exception {
        // Act
        mockMvc.perform(post("/api/positions/NOSUCHSYMBOL12345/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/stats returns valid statistics")
    void testGetPositionStatsValidData() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/stats")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPositions").isNumber())
                .andExpect(jsonPath("$.openPositions").isNumber())
                .andExpect(jsonPath("$.closedPositions").isNumber());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/positions/sector-allocation returns valid allocation")
    void testGetSectorAllocationValidData() throws Exception {
        // Act
        mockMvc.perform(get("/api/positions/sector-allocation")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectors").exists())
                .andExpect(jsonPath("$.totalCapital").isNumber());
    }
}
