package com.swingtrade.api.controller;

import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.PaginatedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.swingtrade.api.app.SwingTradeApiApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PositionController REST endpoints.
 * Tests all position-related API endpoints including retrieval, filtering, and closure.
 */
@SpringBootTest(classes = SwingTradeApiApplication.class)
@AutoConfigureMockMvc
class PositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private PositionResponse createOpenPosition() {
        PositionResponse response = new PositionResponse();
        response.setId(1L);
        response.setSymbol("AAPL");
        response.setEntryPrice(new BigDecimal("150.00"));
        response.setEntryDate(LocalDate.now());
        response.setQuantity(100);
        response.setStopLoss(new BigDecimal("145.00"));
        response.setTarget(new BigDecimal("160.00"));
        response.setStatus(PositionResponse.PositionStatus.OPEN);
        response.setEntryReason("Technical breakout");
        response.setCurrentPrice(new BigDecimal("152.00"));
        response.setUnrealizedPnL(new BigDecimal("200.00"));
        response.setUnrealizedPnLPercent(new BigDecimal("1.33"));
        response.setAveragePrice(new BigDecimal("150.00"));
        response.setTotalValue(new BigDecimal("15200.00"));
        return response;
    }

    // ==================== GET / (Get Open Positions) ====================

    @Test
    void testGetPositions_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions")
                .param("page", "0")
                .param("size", "20")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ==================== GET /{symbol} (Get Position by Symbol) ====================

    @Test
    void testGetPosition_ValidSymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void testGetPosition_InvalidSymbol_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/positions/NOSUCHSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /closed (Get Closed Positions) ====================

    @Test
    void testGetClosedPositions_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/closed")
                .param("page", "0")
                .param("size", "20")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ==================== GET /status/{status} (Get Positions by Status) ====================

    @Test
    void testGetPositionsByStatus_Open_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/status/OPEN")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetPositionsByStatus_Closed_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/status/CLOSED")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetPositionsByStatus_Stopped_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/status/STOPPED")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetPositionsByStatus_TargetHit_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/status/TARGET_HIT")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetPositionsByStatus_InvalidStatus_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/positions/status/INVALID")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /symbol/{symbol} (Get Positions by Symbol) ====================

    @Test
    void testGetPositionsBySymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/symbol/AAPL")
                .param("page", "0")
                .param("size", "20")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ==================== GET /sector/{sector} (Get Positions by Sector) ====================

    @Test
    void testGetPositionsBySector_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/sector/TECHNOLOGY")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ==================== GET /stats (Get Position Statistics) ====================

    @Test
    void testGetPositionStats_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/stats")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPositions").exists())
                .andExpect(jsonPath("$.openPositions").exists());
    }

    // ==================== GET /sector-allocation (Get Sector Allocation) ====================

    @Test
    void testGetSectorAllocation_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/positions/sector-allocation")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocation").exists())
                .andExpect(jsonPath("$.totalExposure").exists());
    }

    // ==================== POST /{symbol}/close (Close Position) ====================

    @Test
    void testClosePosition_WithValidRequest_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/positions/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exitReason\": \"Target reached\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void testClosePosition_WithEmptyExitReason_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/positions/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void testClosePosition_WithNullRequest_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/positions/AAPL/close")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testClosePosition_PositionNotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/positions/NOSUCHSYMBOL12345/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ==================== Error Handling ====================

    @Test
    void testGetPositions_WithServerError_ReturnsInternalServerError() throws Exception {
        mockMvc.perform(get("/api/positions")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetPosition_WithServerError_ReturnsInternalServerError() throws Exception {
        mockMvc.perform(get("/api/positions/SERVER_ERROR")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testClosePosition_WithServerError_ReturnsInternalServerError() throws Exception {
        mockMvc.perform(post("/api/positions/SERVER_ERROR/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isInternalServerError());
    }
}
