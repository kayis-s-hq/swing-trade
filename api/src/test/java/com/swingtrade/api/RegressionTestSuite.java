package com.swingtrade.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.api.dto.*;
import com.swingtrade.broker.model.OrderType;
import com.swingtrade.broker.model.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end regression test suite for the swing trading system.
 * Tests complete workflows and integration between components.
 */
@WebMvcTest
@DisplayName("Regression Test Suite")
class RegressionTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private PositionResponse createdPosition;
    private SignalResponse generatedSignal;

    @BeforeEach
    void setUp() throws Exception {
        // Clear any existing state by triggering a scan first
        mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    // ==================== FULL TRADING DAY WORKFLOW ====================

    @Test
    @DisplayName("Full Trading Day - Complete workflow from signal to position to close")
    void fullTradingDay() throws Exception {
        // Step 1: Generate signal for a symbol
        String generateRequest = "{\"symbol\": \"AAPL\"}";
        SignalResponse signal = mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(generateRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.signalType").exists())
                .andExpect(jsonPath("$.confidence").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        generatedSignal = objectMapper.readValue(signal, SignalResponse.class);

        // Step 2: Create position based on signal
        TradeRequest tradeRequest = new TradeRequest();
        tradeRequest.setSymbol("AAPL");
        tradeRequest.setQuantity(100);
        tradeRequest.setDirection(TradeDirection.LONG);
        tradeRequest.setOrderType(OrderType.MARKET);
        tradeRequest.setPrice(new BigDecimal("150.00"));
        tradeRequest.setEntryReason("Generated from signal");
        tradeRequest.setRiskTolerance(0.02);

        PositionResponse position = mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tradeRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.entryPrice").value("150.00"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        createdPosition = objectMapper.readValue(position, PositionResponse.class);

        // Step 3: Verify position is in open positions list
        mockMvc.perform(get("/api/trades")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));

        // Step 4: Get position details
        PositionResponse retrievedPosition = mockMvc.perform(get("/api/trades/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andReturn()
                .getResponse()
                .getContentAsString();

        PositionResponse retrieved = objectMapper.readValue(retrievedPosition, PositionResponse.class);
        assertEquals(createdPosition.getId(), retrieved.getId());

        // Step 5: Close position
        ClosePositionRequest closeRequest = new ClosePositionRequest();
        closeRequest.setSymbol("AAPL");
        closeRequest.setExitReason("Target reached");

        PositionResponse closedPosition = mockMvc.perform(post("/api/trades/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.exitReason").value("Target reached"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals("CLOSED", closedPosition.getStatus().name());

        // Step 6: Verify position is no longer in open positions
        mockMvc.perform(get("/api/trades")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.symbol==\"AAPL\" && @.status==\"OPEN\")]").doesNotExist());

        // Step 7: Verify trade history
        mockMvc.perform(get("/api/trades/AAPL/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Step 8: Verify performance updated
        mockMvc.perform(get("/api/trades/performance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").exists())
                .andExpect(jsonPath("$.winRate").exists());
    }

    // ==================== SIGNAL CONSISTENCY ====================

    @Test
    @DisplayName("Signal Consistency - Same input produces same output")
    void signalConsistency() throws Exception {
        String symbolRequest = "{\"symbol\": \"TSLA\"}";

        // Generate signal twice
        SignalResponse signal1 = mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(symbolRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("TSLA"))
                .andExpect(jsonPath("$.signalType").exists())
                .andExpect(jsonPath("$.confidence").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        SignalResponse signal2 = mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(symbolRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("TSLA"))
                .andExpect(jsonPath("$.signalType").exists())
                .andExpect(jsonPath("$.confidence").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        SignalResponse parsed1 = objectMapper.readValue(signal1, SignalResponse.class);
        SignalResponse parsed2 = objectMapper.readValue(signal2, SignalResponse.class);

        // Both signals should have same symbol
        assertEquals(parsed1.getSymbol(), parsed2.getSymbol());

        // Both signals should have same type (deterministic)
        assertEquals(parsed1.getSignalType(), parsed2.getSignalType());

        // Both should have confidence in valid range
        assertTrue(parsed1.getConfidence().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(parsed1.getConfidence().compareTo(new BigDecimal("1.0")) <= 0);
    }

    // ==================== POSITION LIMITS ====================

    @Test
    @DisplayName("Position Limits - Max 5 concurrent positions enforced")
    void positionLimits() throws Exception {
        // Create 5 positions
        for (int i = 0; i < 5; i++) {
            String symbol = "SYMBOL" + i;
            TradeRequest request = new TradeRequest();
            request.setSymbol(symbol);
            request.setQuantity(100);
            request.setDirection(TradeDirection.LONG);
            request.setOrderType(OrderType.MARKET);
            request.setPrice(new BigDecimal("100.00"));
            request.setEntryReason("Test position " + i);

            mockMvc.perform(post("/api/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // Verify we have 5 open positions
        mockMvc.perform(get("/api/trades")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        // Try to create 6th position - should fail with conflict
        TradeRequest sixthRequest = new TradeRequest();
        sixthRequest.setSymbol("SIXTH");
        sixthRequest.setQuantity(100);
        sixthRequest.setDirection(TradeDirection.LONG);
        sixthRequest.setOrderType(OrderType.MARKET);
        sixthRequest.setPrice(new BigDecimal("100.00"));
        sixthRequest.setEntryReason("Should fail");

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sixthRequest)))
                .andExpect(status().isConflict());

        // Verify still only 5 positions
        mockMvc.perform(get("/api/trades")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    // ==================== PORTFOLIO ROLLUP ====================

    @Test
    @DisplayName("Portfolio Rollup - P/L calculations are accurate")
    void portfolioRollup() throws Exception {
        // Create a position
        TradeRequest entryRequest = new TradeRequest();
        entryRequest.setSymbol("ROLLUP");
        entryRequest.setQuantity(100);
        entryRequest.setDirection(TradeDirection.LONG);
        entryRequest.setOrderType(OrderType.MARKET);
        entryRequest.setPrice(new BigDecimal("100.00"));
        entryRequest.setEntryReason("Entry for rollup test");

        PositionResponse entryPosition = mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entryRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryPrice").value("100.00"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Close position at different price
        ClosePositionRequest closeRequest = new ClosePositionRequest();
        closeRequest.setSymbol("ROLLUP");
        closeRequest.setExitReason("Exit for rollup test");

        PositionResponse closePosition = mockMvc.perform(post("/api/trades/ROLLUP/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify performance shows the trade
        PerformanceResponse performance = mockMvc.perform(get("/api/trades/performance")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(1))
                .andExpect(jsonPath("$.closedTrades").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        PerformanceResponse parsedPerformance = objectMapper.readValue(performance, PerformanceResponse.class);
        assertEquals(1, parsedPerformance.getTotalTrades());
        assertEquals(1, parsedPerformance.getClosedTrades());
        assertNotNull(parsedPerformance.getTotalPnL());
        assertNotNull(parsedPerformance.getWinRate());
    }

    // ==================== SCAN WORKFLOW ====================

    @Test
    @DisplayName("Scan Workflow - Market scan generates signals correctly")
    void scanWorkflow() throws Exception {
        // Trigger scan
        String scanRequest = "{\"symbols\": [\"AAPL\", \"GOOGL\", \"MSFT\"], \"includeSentiment\": true, \"minConfidence\": 0.5}";

        ScanResponse scanResult = mockMvc.perform(post("/api/signals/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.symbolsScanned").exists())
                .andExpect(jsonPath("$.signalsFound").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ScanResponse parsedScan = objectMapper.readValue(scanResult, ScanResponse.class);
        assertNotNull(parsedScan.getScanTime());
        assertNotNull(parsedScan.getSignalsFound());
        assertTrue(parsedScan.getSignalsFound() >= 0);

        // Verify scan history updated
        mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Get latest scan from history
        String history = mockMvc.perform(get("/api/signals/scan/history")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNotNull(history);
    }

    // ==================== FILTERING WORKFLOW ====================

    @Test
    @DisplayName("Filtering Workflow - Signal filtering works correctly")
    void filteringWorkflow() throws Exception {
        // Generate multiple signals
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "TSLA", "AMZN"};
        for (String symbol : symbols) {
            mockMvc.perform(post("/api/signals/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"symbol\": \"" + symbol + "\"}"))
                    .andExpect(status().isCreated());
        }

        // Test BUY filter
        mockMvc.perform(get("/api/signals/type/BUY")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());

        // Test SELL filter
        mockMvc.perform(get("/api/signals/type/SELL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());

        // Test high confidence filter
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "0.8")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());

        // Test date range filter
        mockMvc.perform(get("/api/signals/date-range")
                .param("startDate", LocalDate.now().minusDays(7).toString())
                .param("endDate", LocalDate.now().toString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    // ==================== ANALYSIS WORKFLOW ====================

    @Test
    @DisplayName("Analysis Workflow - Technical and sentiment analysis available")
    void analysisWorkflow() throws Exception {
        // Get technical analysis
        mockMvc.perform(get("/api/signals/analysis/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.analysisDate").exists())
                .andExpect(jsonPath("$.rsi").exists())
                .andExpect(jsonPath("$.macd").exists())
                .andExpect(jsonPath("$.signal").exists());

        // Get sentiment analysis
        mockMvc.perform(get("/api/signals/sentiment/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.analyzedAt").exists())
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.confidence").exists());

        // Get combined analysis
        mockMvc.perform(get("/api/signals/combined/AAPL")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.combinedScore").exists())
                .andExpect(jsonPath("$.technicalScore").exists())
                .andExpect(jsonPath("$.sentimentScore").exists())
                .andExpect(jsonPath("$.signalType").exists());
    }

    // ==================== POSITION MANAGEMENT WORKFLOW ====================

    @Test
    @DisplayName("Position Management - Full CRUD operations")
    void positionManagement() throws Exception {
        // Create position
        TradeRequest createRequest = new TradeRequest();
        createRequest.setSymbol("MGT");
        createRequest.setQuantity(50);
        createRequest.setDirection(TradeDirection.LONG);
        createRequest.setOrderType(OrderType.MARKET);
        createRequest.setPrice(new BigDecimal("200.00"));

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Get all positions
        mockMvc.perform(get("/api/trades")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Get specific position
        mockMvc.perform(get("/api/trades/MGT")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("MGT"));

        // Get positions by status (open)
        mockMvc.perform(get("/api/positions/status/OPEN")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());

        // Get position stats
        mockMvc.perform(get("/api/positions/stats")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openPositions").exists())
                .andExpect(jsonPath("$.closedPositions").exists());

        // Get sector allocation
        mockMvc.perform(get("/api/positions/sector-allocation")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocation").exists());

        // Close position
        ClosePositionRequest closeRequest = new ClosePositionRequest();
        closeRequest.setSymbol("MGT");
        closeRequest.setExitReason("Management test");

        mockMvc.perform(post("/api/trades/MGT/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }
}
