package com.swingtrade.data.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for setting up Upstox API mock endpoints.
 * Provides realistic JSON response templates matching the actual Upstox API format.
 */
public class UpstoxWireMockHelper {

    private static final Logger logger = LoggerFactory.getLogger(UpstoxWireMockHelper.class);

    private final WireMockServer wireMockServer;

    public UpstoxWireMockHelper(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
        wireMockServer.start();
    }

    /**
     * Mock the login endpoint for authentication.
     *
     * @param accessToken the access token to return in response
     * @param userId the user ID to return
     */
    public void mockLogin(String accessToken, String userId) {
        String response = String.format("{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": {\n" +
                "    \"access_token\": \"%s\",\n" +
                "    \"refresh_token\": \"%s\",\n" +
                "    \"user_id\": \"%s\",\n" +
                "    \"client_type\": \"WEB\",\n" +
                "    \"exchange_tokens\": {\n" +
                "      \"NSE\": \"nse_token_%s\",\n" +
                "      \"BSE\": \"bse_token_%s\"\n" +
                "    }\n" +
                "  }\n" +
                "}",
                accessToken,
                "refresh_" + accessToken,
                userId,
                userId,
                userId);

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/v2/login")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed POST /v2/login with access_token: {}", accessToken);
    }

    /**
     * Mock the token refresh endpoint.
     *
     * @param refreshToken the refresh token to expect
     * @param newAccessToken the new access token to return
     */
    public void mockTokenRefresh(String refreshToken, String newAccessToken) {
        String response = String.format("{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": {\n" +
                "    \"access_token\": \"%s\",\n" +
                "    \"refresh_token\": \"%s\"\n" +
                "  }\n" +
                "}",
                newAccessToken,
                refreshToken);

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/v2/token")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed POST /v2/token for refresh_token: {}", refreshToken);
    }

    /**
     * Mock the OHLCV data endpoint with sample candle data.
     *
     * @param symbol the stock symbol
     * @param candles the OHLCV candle data as JSON array
     */
    public void mockOhlcvData(String symbol, String candles) {
        String response = String.format("{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": %s\n" +
                "}",
                candles);

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/v2/market-data/ohlcv")
                .withQueryParam("symbol", com.github.tomakehurst.wiremock.client.WireMock.equalTo(symbol))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed GET /v2/market-data/ohlcv for symbol: {}", symbol);
    }

    /**
     * Mock the instruments endpoint with sample instrument list.
     *
     * @param instruments the instrument list as JSON array
     */
    public void mockInstruments(String instruments) {
        String response = String.format("{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": %s\n" +
                "}",
                instruments);

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/v2/instruments")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed GET /v2/instruments");
    }

    /**
     * Mock OHLCV data with realistic NSE equity candle data.
     *
     * @param symbol the stock symbol (e.g., RELIANCE, TCS)
     * @param startDate the start date (YYYY-MM-DD format)
     * @param endDate the end date (YYYY-MM-DD format)
     */
    public void mockRealisticOhlcvData(String symbol, String startDate, String endDate) {
        // Generate realistic-looking OHLCV data
        String candles = String.format("[\n" +
                "  {\"timestamp\": \"%sT09:15:00+05:30\", \"open\": 2500.00, \"high\": 2515.50, \"low\": 2495.25, \"close\": 2510.75, \"volume\": 1250000},\n" +
                "  {\"timestamp\": \"%sT09:20:00+05:30\", \"open\": 2510.75, \"high\": 2520.00, \"low\": 2508.00, \"close\": 2518.50, \"volume\": 980000},\n" +
                "  {\"timestamp\": \"%sT09:25:00+05:30\", \"open\": 2518.50, \"high\": 2525.75, \"low\": 2515.00, \"close\": 2522.25, \"volume\": 1100000},\n" +
                "  {\"timestamp\": \"%sT09:30:00+05:30\", \"open\": 2522.25, \"high\": 2530.00, \"low\": 2520.50, \"close\": 2528.00, \"volume\": 1350000},\n" +
                "  {\"timestamp\": \"%sT09:35:00+05:30\", \"open\": 2528.00, \"high\": 2535.25, \"low\": 2525.75, \"close\": 2532.50, \"volume\": 1200000}\n" +
                "]",
                startDate, startDate, startDate, startDate, startDate);

        mockOhlcvData(symbol, candles);
    }

    /**
     * Mock empty OHLCV response.
     *
     * @param symbol the stock symbol
     */
    public void mockEmptyOhlcvData(String symbol) {
        mockOhlcvData(symbol, "[]");
    }

    /**
     * Mock OHLCV with error response.
     *
     * @param symbol the stock symbol
     * @param errorCode the error code
     * @param errorMessage the error message
     */
    public void mockOhlcvError(String symbol, String errorCode, String errorMessage) {
        String response = String.format("{\n" +
                "  \"status\": \"error\",\n" +
                "  \"data\": {\n" +
                "    \"error_code\": \"%s\",\n" +
                "    \"message\": \"%s\"\n" +
                "  }\n" +
                "}",
                errorCode, errorMessage);

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/v2/market-data/ohlcv")
                .withQueryParam("symbol", com.github.tomakehurst.wiremock.client.WireMock.equalTo(symbol))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed GET /v2/market-data/ohlcv error for symbol: {}", symbol);
    }

    /**
     * Reset all stubs and requests.
     */
    public void resetAll() {
        wireMockServer.resetAll();
        logger.info("WireMock helper reset all stubs and requests");
    }
}
