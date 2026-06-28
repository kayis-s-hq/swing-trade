package com.swingtrade.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.api.app.SwingTradeApiApplication;
import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.TradeRequest;
import com.swingtrade.api.test.integration.DatabaseTestContainer;
import com.swingtrade.broker.model.OrderType;
import com.swingtrade.broker.model.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TradingController REST endpoints.
 * Tests all trade-related API endpoints including position creation, management, and closure.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SwingTradeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper();
    }

    private TradeRequest createTradeRequest() {
        TradeRequest request = new TradeRequest();
        request.setSymbol("AAPL");
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);
        request.setPrice(new BigDecimal("150.00"));
        request.setEntryReason("Technical breakout");
        request.setRiskTolerance(0.02);
        return request;
    }

    private PositionResponse createPositionResponse() {
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
        return response;
    }

    // ==================== POST / (Create Position) ====================

    @Test
    void testCreatePosition_WithValidRequest_ReturnsCreated() throws Exception {
        TradeRequest request = createTradeRequest();

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void testCreatePosition_WithLowercaseSymbol_ReturnsCreated() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setSymbol("aapl");

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void testCreatePosition_WithMissingSymbol_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setSymbol(null);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithEmptySymbol_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setSymbol("");

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithInvalidSymbolFormat_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setSymbol("@INVALID@");

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithMissingQuantity_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setQuantity(null);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithZeroQuantity_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setQuantity(0);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithNegativeQuantity_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setQuantity(-1);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithMissingDirection_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setDirection(null);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithMissingOrderType_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setOrderType(null);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithLimitOrder_MissingLimitPrice_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setOrderType(OrderType.LIMIT);
        request.setLimitPrice(null);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithStopOrder_MissingStopPrice_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setOrderType(OrderType.STOP);
        request.setStopPrice(null);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithInvalidPrice_ReturnsBadRequest() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setPrice(new BigDecimal("-100.00"));

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePosition_WithMaxPositionsExceeded_ReturnsConflict() throws Exception {
        TradeRequest request = createTradeRequest();
        request.setSymbol("EXCEEDED");

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ==================== GET / (Get Open Positions) ====================

    @Test
    void testGetOpenPositions_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/trades")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    // ==================== GET /{symbol} (Get Position by Symbol) ====================

    @Test
    void testGetPosition_ValidSymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/trades/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void testGetPosition_InvalidSymbol_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/trades/NOSUCHSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ==================== POST /{symbol}/close (Close Position) ====================

    @Test
    void testClosePosition_WithValidRequest_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/trades/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exitReason\": \"Target reached\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void testClosePosition_WithEmptyExitReason_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/trades/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void testClosePosition_WithNullRequest_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/trades/AAPL/close")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testClosePosition_PositionNotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/trades/NOSUCHSYMBOL12345/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /{symbol}/history (Get Trade History) ====================

    @Test
    void testGetTradeHistory_ValidSymbol_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/trades/AAPL/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    void testGetTradeHistory_InvalidSymbol_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/trades/NOSUCHSYMBOL12345/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ==================== GET /performance (Get Portfolio Performance) ====================

    @Test
    void testGetPortfolioPerformance_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/trades/performance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").exists())
                .andExpect(jsonPath("$.totalPnL").exists())
                .andExpect(jsonPath("$.winRate").exists());
    }

    // ==================== GET /risk-summary (Get Risk Summary) ====================

    @Test
    void testGetRiskSummary_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/trades/risk-summary")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExposure").exists())
                .andExpect(jsonPath("$.numberOfPositions").exists());
    }
}
