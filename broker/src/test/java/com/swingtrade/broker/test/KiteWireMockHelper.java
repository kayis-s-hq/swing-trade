package com.swingtrade.broker.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestTemplate;
import org.wiremock.core.WireMockConfiguration;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.WireMockBean;

import java.math.BigDecimal;

/**
 * WireMock helper for testing Kite Connect integration.
 * Provides mock endpoints for Zerodha Kite Connect API.
 */
@EnableWireMock
public class KiteWireMockHelper {

    protected WireMockBean wireMockBean;
    protected ObjectMapper objectMapper;
    protected RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        restTemplate = new RestTemplate();
    }

    @AfterEach
    public void tearDown() {
        // Cleanup if needed
    }

    /**
     * Mock the Kite Connect login URL endpoint.
     */
    protected void mockLoginUrl(String apiKey) {
        wireMockBean.stubFor(org.wiremock.core.WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .get("/login")
                .willReturn(org.wiremock.stubbing.ServeEvent::ok)
        );
    }

    /**
     * Create a mock order placement response.
     */
    protected String createOrderResponse(String orderId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"order_id\": \"").append(orderId).append("\",\n");
        sb.append("  \"exchange_order_id\": \"\",\n");
        sb.append("  \"status\": \"COMPLETE\",\n");
        sb.append("  \"order_type\": \"MARKET\",\n");
        sb.append("  \"product\": \"CNC\",\n");
        sb.append("  \"transaction_type\": \"BUY\",\n");
        sb.append("  \"quantity\": \"1\",\n");
        sb.append("  \"disclosed_quantity\": \"0\",\n");
        sb.append("  \"price\": \"0\",\n");
        sb.append("  \"trigger_price\": \"0\",\n");
        sb.append("  \"filled_quantity\": \"1\",\n");
        sb.append("  \"avg_price\": \"100.00\",\n");
        sb.append("  \"symbol\": \"RELIANCE\",\n");
        sb.append("  \"exchange\": \"NSE\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Create a mock holdings response.
     */
    protected String createHoldingsResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"data\": [\n");
        sb.append("    {\n");
        sb.append("      \"instrument_key\": \"RELIANCE-EQ\",\n");
        sb.append("      \"symbol\": \"RELIANCE\",\n");
        sb.append("      \"exchange\": \"NSE\",\n");
        sb.append("      \"quantity\": \"10\",\n");
        sb.append("      \"avg_price\": \"2500.00\",\n");
        sb.append("      \"ltp\": \"2550.00\",\n");
        sb.append("      \"pnl\": \"500.00\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Create a mock quote response.
     */
    protected String createQuoteResponse(String symbol, String ltp) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"data\": {\n");
        sb.append("    \"").append(symbol).append("-EQ\": {\n");
        sb.append("      \"instrument_key\": \"").append(symbol).append("-EQ\",\n");
        sb.append("      \"ltp\": \"").append(ltp).append("\",\n");
        sb.append("      \"oi\": \"0\",\n");
        sb.append("      \"ltt\": \"0\",\n");
        sb.append("      \"last_price\": \"").append(ltp).append("\",\n");
        sb.append("      \"last_qty\": \"1\",\n");
        sb.append("      \"total_buy_quantity\": \"100\",\n");
        sb.append("      \"total_sell_quantity\": \"100\"\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Create a mock user profile response.
     */
    protected String createUserProfileResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"data\": {\n");
        sb.append("    \"user_id\": \"USER123\",\n");
        sb.append("    \"name\": \"Test User\",\n");
        sb.append("    \"email\": \"test@example.com\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Get the mock server port.
     */
    protected int getMockPort() {
        return wireMockBean.getPort();
    }
}
