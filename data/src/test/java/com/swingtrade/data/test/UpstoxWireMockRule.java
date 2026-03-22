package com.swingtrade.data.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * JUnit 5 extension for WireMock server setup.
 * Provides a mock Upstox API server for integration tests.
 */
public class UpstoxWireMockRule implements BeforeAllCallback, AfterAllCallback {

    private static final Logger logger = LoggerFactory.getLogger(UpstoxWireMockRule.class);

    private static final int DEFAULT_PORT = 0; // Random port

    private final WireMockServer wireMockServer;
    private final UpstoxWireMockHelper wireMockHelper;

    public UpstoxWireMockRule() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .port(DEFAULT_PORT)
                .dynamicPort());
        wireMockHelper = new UpstoxWireMockHelper(wireMockServer);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        logger.info("=== Upstox WireMock: Starting server ===");

        wireMockServer.start();

        // Reset all mappings before each test run
        wireMockServer.resetAll();

        logger.info("Upstox WireMock server started on port: {}", wireMockServer.port());
        logger.info("Mock API base URL: http://localhost:{}", wireMockServer.port());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        logger.info("=== Upstox WireMock: Stopping server ===");

        wireMockServer.stop();
        wireMockServer.resetAll();

        logger.info("Upstox WireMock server stopped");
    }

    /**
     * Get the WireMock server instance.
     *
     * @return WireMockServer instance
     */
    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    /**
     * Get the UpstoxWireMockHelper for setting up mock endpoints.
     *
     * @return UpstoxWireMockHelper instance
     */
    public UpstoxWireMockHelper getHelper() {
        return wireMockHelper;
    }

    /**
     * Get the base URL for the mock Upstox API.
     *
     * @return base URL
     */
    public String getBaseUrl() {
        return String.format("http://localhost:%s", wireMockServer.port());
    }

    /**
     * Get the URL for the login endpoint.
     *
     * @return login endpoint URL
     */
    public String getLoginUrl() {
        return getBaseUrl() + "/v2/login";
    }

    /**
     * Get the URL for the token refresh endpoint.
     *
     * @return token endpoint URL
     */
    public String getTokenUrl() {
        return getBaseUrl() + "/v2/token";
    }

    /**
     * Get the URL for the OHLCV data endpoint.
     *
     * @param symbol the stock symbol
     * @return OHLCV endpoint URL
     */
    public String getOhlcvUrl(String symbol) {
        return getBaseUrl() + String.format("/v2/market-data/ohlcv?symbol=%s", symbol);
    }

    /**
     * Get the URL for the instruments endpoint.
     *
     * @return instruments endpoint URL
     */
    public String getInstrumentsUrl() {
        return getBaseUrl() + "/v2/instruments";
    }

    /**
     * Reset all mock interactions.
     */
    public void resetAll() {
        wireMockServer.resetAll();
        logger.info("WireMock server reset");
    }

    /**
     * Get the count of interactions for a specific endpoint.
     *
     * @param endpoint the endpoint path
     * @return number of interactions
     */
    public int getInteractionCount(String endpoint) {
        // WireMock 3.x API - count actual served requests to the endpoint
        return (int) wireMockServer.getAllServeEvents().stream()
                .filter(e -> e.getRequest().getUrl().equals(endpoint))
                .count();
    }
}
