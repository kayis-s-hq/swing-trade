package com.swingtrade.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.api.app.SwingTradeApiApplication;
import com.swingtrade.api.dto.ClosePositionRequest;
import com.swingtrade.api.dto.ErrorResponse;
import com.swingtrade.api.dto.SymbolRequest;
import com.swingtrade.api.dto.TradeRequest;
import com.swingtrade.broker.model.OrderType;
import com.swingtrade.broker.model.TradeDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for error handling and validation.
 * Tests DTO validation, error response formats, and HTTP status codes.
 */
@SpringBootTest(classes = SwingTradeApiApplication.class)
@AutoConfigureMockMvc
class ErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== DTO Validation Tests ====================

    @Test
    void testTradeRequest_WithBlankSymbol_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("   ");
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithNullSymbol_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol(null);
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithEmptySymbol_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("");
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithInvalidSymbolFormat_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("@INVALID@SYMBOL#");
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithZeroQuantity_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("AAPL");
        request.setQuantity(0);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithNegativeQuantity_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("AAPL");
        request.setQuantity(-1);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithNullDirection_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("AAPL");
        request.setQuantity(100);
        request.setDirection(null);
        request.setOrderType(OrderType.MARKET);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithNullOrderType_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("AAPL");
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(null);

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithNegativePrice_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("AAPL");
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);
        request.setPrice(new BigDecimal("-100.00"));

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTradeRequest_WithZeroPrice_ReturnsBadRequest() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setSymbol("AAPL");
        request.setQuantity(100);
        request.setDirection(TradeDirection.LONG);
        request.setOrderType(OrderType.MARKET);
        request.setPrice(new BigDecimal("0.00"));

        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testClosePositionRequest_WithBlankSymbol_ReturnsBadRequest() throws Exception {
        ClosePositionRequest request = new ClosePositionRequest();
        request.setSymbol("   ");

        mockMvc.perform(post("/api/trades/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testClosePositionRequest_WithNullSymbol_ReturnsBadRequest() throws Exception {
        ClosePositionRequest request = new ClosePositionRequest();
        request.setSymbol(null);

        mockMvc.perform(post("/api/trades/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testClosePositionRequest_WithEmptySymbol_ReturnsBadRequest() throws Exception {
        ClosePositionRequest request = new ClosePositionRequest();
        request.setSymbol("");

        mockMvc.perform(post("/api/trades/AAPL/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSymbolRequest_WithBlankSymbol_ReturnsBadRequest() throws Exception {
        SymbolRequest request = new SymbolRequest();
        request.setSymbol("   ");

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSymbolRequest_WithNullSymbol_ReturnsBadRequest() throws Exception {
        SymbolRequest request = new SymbolRequest();
        request.setSymbol(null);

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSymbolRequest_WithEmptySymbol_ReturnsBadRequest() throws Exception {
        SymbolRequest request = new SymbolRequest();
        request.setSymbol("");

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSymbolRequest_WithInvalidSymbolFormat_ReturnsBadRequest() throws Exception {
        SymbolRequest request = new SymbolRequest();
        request.setSymbol("@INVALID@");

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ==================== HTTP Status Code Tests ====================

    @Test
    void testGetNonExistentResource_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/signals/symbol/NOSUCHSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetPositionNonExistent_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/trades/NOSUCHSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCloseNonExistentPosition_ReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/trades/NOSUCHSYMBOL12345/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetSignalsByType_WithInvalidType_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/type/INVALID_TYPE")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetHighConfidenceSignals_WithNegativeThreshold_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "-0.1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetHighConfidenceSignals_WithThresholdAboveOne_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/high-confidence")
                .param("minConfidence", "1.1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetSignalsByDateRange_WithInvalidStartDate_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/date-range")
                .param("startDate", "invalid-date")
                .param("endDate", "2024-01-01")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetSignalsByDateRange_WithInvalidEndDate_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/date-range")
                .param("startDate", "2024-01-01")
                .param("endDate", "invalid-date")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetSignalsByDateRange_WithEndDateBeforeStartDate_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/signals/date-range")
                .param("startDate", "2024-01-10")
                .param("endDate", "2024-01-01")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ==================== Error Response Format Tests ====================

    @Test
    void testErrorHandling_ReturnsStructuredErrorResponse() throws Exception {
        mockMvc.perform(get("/api/signals/symbol/INVALID_ERROR")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testErrorHandling_WithInvalidInput_ReturnsBadRequestWithMessage() throws Exception {
        String request = "{\"symbol\": \"@INVALID@\"}";

        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void testErrorHandling_WithNotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/signals/symbol/NOSUCHSYMBOL12345")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ==================== Content-Type Tests ====================

    @Test
    void testPostWithoutJsonContentType_ReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/trades")
                .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void testPostWithInvalidJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Accept Header Tests ====================

    @Test
    void testGetWithJsonAcceptHeader_ReturnsJson() throws Exception {
        mockMvc.perform(get("/api/signals/latest")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetWithXmlAcceptHeader_ReturnsNotAcceptable() throws Exception {
        mockMvc.perform(get("/api/signals/latest")
                .accept(MediaType.valueOf("application/xml")))
                .andExpect(status().isNotAcceptable());
    }
}
