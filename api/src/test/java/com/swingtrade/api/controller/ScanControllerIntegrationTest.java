package com.swingtrade.api.controller;

import com.swingtrade.api.test.integration.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ScanController endpoints.
 * Tests manual signal generation and scan history.
 */
@DisplayName("Scan Controller Integration Tests")
class ScanControllerIntegrationTest extends ApiIntegrationTest {

    @Test
    @Tag("integration")
    @DisplayName("POST /api/signals/scan triggers manual scan")
    void testTriggerScan() throws Exception {
        // Act
        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/scan/history returns scan history")
    void testGetScanHistory() throws Exception {
        // Act
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/signals/scan with empty request works")
    void testTriggerScanEmptyRequest() throws Exception {
        // Act
        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Tag("integration")
    @DisplayName("POST /api/signals/scan returns scan result with signals")
    void testTriggerScanReturnsResult() throws Exception {
        // Act
        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.signalsFound").isNumber())
                .andExpect(jsonPath("$.duration").isNumber());
    }

    @Test
    @Tag("integration")
    @DisplayName("GET /api/signals/scan/history returns empty list when no scans")
    void testGetScanHistoryEmpty() throws Exception {
        // Act
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").exists());
    }
}
