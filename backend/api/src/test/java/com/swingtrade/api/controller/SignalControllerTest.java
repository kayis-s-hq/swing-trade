package com.swingtrade.api.controller;

import com.swingtrade.api.app.SwingTradeApiApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SignalController REST endpoints.
 * Tests all signal-related API endpoints including retrieval, filtering, generation, and analysis.
 */
@Disabled("Fails to boot: no Postgres reachable at localhost:5432 in this environment and this "
    + "class never starts a Testcontainer (only ApiIntegrationTest subclasses do). Needs "
    + "conversion to @WebMvcTest + @MockBean services - see plan at "
    + "~/.claude/plans/task-notification-task-id-b53i523h8-tas-snoopy-quail.md. Revisit later.")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SwingTradeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== GET /latest ====================

    @Test
    void testGetLatestSignals_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/latest")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    // ==================== GET /symbol/{symbol} ====================

    @Test
    void testGetSignalsBySymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/symbol/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetSignalsBySymbol_WithSpecialCharacters() throws Exception {
        mockMvc.perform(get("/api/signals/symbol/INVALID@SYMBOL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /date-range ====================

    @Test
    void testGetSignalsByDateRange_ReturnsSuccess() throws Exception {
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();

        mockMvc.perform(get("/api/signals/date-range")
                .param("startDate", startDate.toString())
                .param("endDate", endDate.toString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetSignalsByDateRange_WithInvalidDates() throws Exception {
        mockMvc.perform(get("/api/signals/date-range")
                .param("startDate", "invalid-date")
                .param("endDate", LocalDate.now().toString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetSignalsByDateRange_EndDateBeforeStartDate() throws Exception {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().minusDays(7);

        mockMvc.perform(get("/api/signals/date-range")
                .param("startDate", startDate.toString())
                .param("endDate", endDate.toString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /type/{type} ====================

    @Test
    void testGetSignalsByType_BUY_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/type/BUY")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetSignalsByType_SELL_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/type/SELL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetSignalsByType_HOLD_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/type/HOLD")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetSignalsByType_InvalidType_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/type/INVALID")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /high-confidence ====================

    @Test
    void testGetHighConfidenceSignals_DefaultThreshold_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetHighConfidenceSignals_CustomThreshold_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "0.9")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetHighConfidenceSignals_ZeroThreshold_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "0.0")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetHighConfidenceSignals_OneThreshold_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "1.0")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetHighConfidenceSignals_NegativeThreshold_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "-0.1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetHighConfidenceSignals_AboveOneThreshold_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "1.1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST /generate ====================

    @Test
    void testGenerateSignal_WithValidSymbol_ReturnsCreated() throws Exception {
        String request = "{\"symbol\": \"AAPL\"}";

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void testGenerateSignal_WithLowercaseSymbol_ReturnsCreated() throws Exception {
        String request = "{\"symbol\": \"aapl\"}";

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isCreated());
    }

    @Test
    void testGenerateSignal_WithInvalidSymbol_ReturnsBadRequest() throws Exception {
        String request = "{\"symbol\": \"@INVALID@\"}";

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGenerateSignal_WithEmptySymbol_ReturnsBadRequest() throws Exception {
        String request = "{\"symbol\": \"\"}";

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGenerateSignal_WithNullSymbol_ReturnsBadRequest() throws Exception {
        String request = "{\"symbol\": null}";

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST /scan ====================

    @Test
    void testTriggerScan_WithValidRequest_ReturnsSuccess() throws Exception {
        String request = "{\"symbols\": [\"AAPL\", \"TSLA\"], \"includeSentiment\": true, \"minConfidence\": 0.5}";

        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testTriggerScan_WithEmptyRequest_ReturnsSuccess() throws Exception {
        String request = "{}";

        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk());
    }

    @Test
    void testTriggerScan_WithNullRequest_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    // ==================== GET /scan/history ====================

    @Test
    void testGetScanHistory_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    // ==================== GET /analysis/{symbol} ====================

    @Test
    void testGetTechnicalAnalysis_ValidSymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/analysis/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testGetTechnicalAnalysis_InvalidSymbol_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/signals/analysis/INVALIDSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /sentiment/{symbol} ====================

    @Test
    void testGetSentimentAnalysis_ValidSymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/sentiment/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testGetSentimentAnalysis_InvalidSymbol_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/signals/sentiment/INVALIDSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /combined/{symbol} ====================

    @Test
    void testGetCombinedSignal_ValidSymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/signals/combined/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testGetCombinedSignal_InvalidSymbol_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/signals/combined/INVALIDSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
