package com.swingtrade.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.api.test.integration.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SignalController endpoints.
 * Tests all signal-related API endpoints with real database.
 */
@DisplayName("SignalController Integration Tests")
class SignalControllerIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/latest returns signals list")
    void testGetLatestSignals() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/latest")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/symbol/{symbol} returns signals for symbol")
    void testGetSignalsBySymbol() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/symbol/RELIANCE")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/type/{type} filters by signal type")
    void testGetSignalsByType() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/type/BUY")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/high-confidence filters by confidence")
    void testGetHighConfidenceSignals() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "0.7")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/signals/generate creates new signal")
    void testGenerateSignal() throws Exception {
        // Arrange
        String request = objectMapper.writeValueAsString(
                new com.swingtrade.api.dto.SymbolRequest("RELIANCE"));

        // Act
        ResultActions result = mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/symbol/{invalid} returns 404")
    void testGetSignalsForInvalidSymbol() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/symbol/INVALIDSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isNotFound());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/type/INVALID returns 400")
    void testGetSignalsByInvalidType() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/type/INVALID")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isBadRequest());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/high-confidence with invalid confidence returns 400")
    void testGetHighConfidenceSignalsInvalid() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "1.5")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isBadRequest());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/analysis/{symbol} returns technical analysis")
    void testGetTechnicalAnalysis() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/analysis/RELIANCE")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/sentiment/{symbol} returns sentiment analysis")
    void testGetSentimentAnalysis() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/signals/sentiment/RELIANCE")
                .accept(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
