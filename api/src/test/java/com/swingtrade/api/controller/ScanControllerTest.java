package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ScanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Scan endpoint tests.
 * Tests scan-related API endpoints.
 */
@WebMvcTest(SignalController.class)
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== POST /scan (Trigger Scan) ====================

    @Test
    void testTriggerScan_WithValidRequest_ReturnsSuccess() throws Exception {
        String request = "{\"symbols\": [\"AAPL\", \"TSLA\"], \"includeSentiment\": true, \"minConfidence\": 0.5}";

        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.scanTime").exists())
                .andExpect(jsonPath("$.symbolsScanned").exists());
    }

    @Test
    void testTriggerScan_WithEmptyRequest_ReturnsSuccess() throws Exception {
        String request = "{}";

        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testTriggerScan_WithNullRequest_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void testTriggerScan_WithOnlySymbols_ReturnsSuccess() throws Exception {
        String request = "{\"symbols\": [\"AAPL\", \"GOOGL\", \"MSFT\"]}";

        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbolsScanned").exists());
    }

    @Test
    void testTriggerScan_WithInvalidMinConfidence_ReturnsBadRequest() throws Exception {
        String request = "{\"minConfidence\": -0.5}";

        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTriggerScan_ServerError_ReturnsInternalServerError() throws Exception {
        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isInternalServerError());
    }

    // ==================== GET /scan/history (Get Scan History) ====================

    @Test
    void testGetScanHistory_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetScanHistory_ReturnsValidStructure() throws Exception {
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scanTime").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].signalsFound").exists());
    }

    @Test
    void testGetScanHistory_EmptyHistory_ReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testGetScanHistory_ServerError_ReturnsInternalServerError() throws Exception {
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }
}
