package com.swingtrade.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.api.test.integration.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TradingController endpoints.
 * Tests position creation, listing, and closing with real database.
 */
@DisplayName("TradingController Integration Tests")
class TradingControllerIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades lists positions")
    void testGetOpenPositions() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades/{symbol} gets position by symbol")
    void testGetPositionBySymbol() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades/RELIANCE")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades/{nonexistent} returns 404")
    void testGetPositionForNonExistent() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades/NOSUCHSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/trades creates new position")
    void testCreatePosition() throws Exception {
        // Arrange
        String request = "{\"symbol\": \"TCS\", \"quantity\": 100, \"direction\": \"LONG\", \"orderType\": \"MARKET\"}";

        // Act
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("TCS"));
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/trades with invalid symbol returns 400")
    void testCreatePositionInvalidSymbol() throws Exception {
        // Arrange
        String request = "{\"symbol\": \"@INVALID@\", \"quantity\": 100, \"direction\": \"LONG\", \"orderType\": \"MARKET\"}";

        // Act
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/trades/{symbol}/close closes position")
    void testClosePosition() throws Exception {
        // Act
        mockMvc.perform(post("/api/trades/RELIANCE/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/trades/{nonexistent}/close returns 404")
    void testCloseNonExistentPosition() throws Exception {
        // Act
        mockMvc.perform(post("/api/trades/NOSUCHSYMBOL12345/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/trades/{symbol}/history shows trade history")
    void testGetTradeHistory() throws Exception {
        // Act
        mockMvc.perform(get("/api/trades/RELIANCE/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/trades with missing quantity returns 400")
    void testCreatePositionMissingQuantity() throws Exception {
        // Arrange
        String request = "{\"symbol\": \"TCS\", \"direction\": \"LONG\", \"orderType\": \"MARKET\"}";

        // Act
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }
}
