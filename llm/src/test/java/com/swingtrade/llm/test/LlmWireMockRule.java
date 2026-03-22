package com.swingtrade.llm.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 extension for WireMock server setup.
 * Provides a mock LLM API server for integration tests.
 */
public class LlmWireMockRule implements BeforeAllCallback, AfterAllCallback {

    private static final Logger logger = LoggerFactory.getLogger(LlmWireMockRule.class);

    private static final int DEFAULT_PORT = 0; // Random port

    private final WireMockServer wireMockServer;
    private final LlmWireMockHelper wireMockHelper;

    public LlmWireMockRule() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .port(DEFAULT_PORT)
                .dynamicPort());
        wireMockHelper = new LlmWireMockHelper(wireMockServer);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        logger.info("=== LLM WireMock: Starting server ===");

        wireMockServer.start();

        // Reset all mappings before each test run
        wireMockServer.resetAll();

        logger.info("LLM WireMock server started on port: {}", wireMockServer.port());
        logger.info("Mock API base URL: http://localhost:{}", wireMockServer.port());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        logger.info("=== LLM WireMock: Stopping server ===");

        wireMockServer.stop();
        wireMockServer.resetAll();

        logger.info("LLM WireMock server stopped");
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
     * Get the LlmWireMockHelper for setting up mock endpoints.
     *
     * @return LlmWireMockHelper instance
     */
    public LlmWireMockHelper getHelper() {
        return wireMockHelper;
    }

    /**
     * Get the base URL for the mock LLM API.
     *
     * @return base URL
     */
    public String getBaseUrl() {
        return String.format("http://localhost:%s", wireMockServer.port());
    }

    /**
     * Get the URL for the chat completions endpoint.
     *
     * @return chat completions endpoint URL
     */
    public String getChatCompletionsUrl() {
        return getBaseUrl() + "/v1/chat/completions";
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
        return wireMockServer.findAll(com.github.tomakehurst.wiremock.client.WireMock
                .anyRequestedFor(com.github.tomakehurst.wiremock.client.WireMock
                        .urlPathMatching(".*" + endpoint + ".*"))).size();
    }
}
