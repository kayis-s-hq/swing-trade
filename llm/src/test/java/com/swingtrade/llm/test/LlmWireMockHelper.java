package com.swingtrade.llm.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for setting up LLM API mock endpoints.
 * Provides realistic JSON response templates for sentiment analysis.
 */
public class LlmWireMockHelper {

    private static final Logger logger = LoggerFactory.getLogger(LlmWireMockHelper.class);

    private final WireMockServer wireMockServer;

    public LlmWireMockHelper(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    /**
     * Mock the chat completions endpoint for bullish sentiment analysis.
     *
     * @param reasoning the reasoning text to return
     * @param confidence the confidence score (0.0 to 1.0)
     */
    public void mockBullishSentiment(String reasoning, double confidence) {
        String response = String.format("{\n" +
                "  \"id\": \"chatcmpl-1234567890\",\n" +
                "  \"object\": \"chat.completion\",\n" +
                "  \"created\": 1709876543,\n" +
                "  \"model\": \"gpt-4-turbo\",\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"message\": {\n" +
                "        \"role\": \"assistant\",\n" +
                "        \"content\": \"{\\\"sentiment\\\": \\\"POSITIVE\\\", \\\"reasoning\\\": \\\"%s\\\", \\\"confidence\\\": %.2f}\"\n" +
                "      },\n" +
                "      \"finish_reason\": \"stop\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"prompt_tokens\": 150,\n" +
                "    \"completion_tokens\": 85,\n" +
                "    \"total_tokens\": 235\n" +
                "  }\n" +
                "}",
                reasoning.replace("\"", "\\\""), confidence);

        wireMockServer.stubFor(WireMock.post("/v1/chat/completions")
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.containing("sentiment analysis"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed POST /v1/chat/completions for bullish sentiment (confidence: {})", confidence);
    }

    /**
     * Mock the chat completions endpoint for bearish sentiment analysis.
     *
     * @param reasoning the reasoning text to return
     * @param confidence the confidence score (0.0 to 1.0)
     */
    public void mockBearishSentiment(String reasoning, double confidence) {
        String response = String.format("{\n" +
                "  \"id\": \"chatcmpl-1234567890\",\n" +
                "  \"object\": \"chat.completion\",\n" +
                "  \"created\": 1709876543,\n" +
                "  \"model\": \"gpt-4-turbo\",\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"message\": {\n" +
                "        \"role\": \"assistant\",\n" +
                "        \"content\": \"{\\\"sentiment\\\": \\\"NEGATIVE\\\", \\\"reasoning\\\": \\\"%s\\\", \\\"confidence\\\": %.2f}\"\n" +
                "      },\n" +
                "      \"finish_reason\": \"stop\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"prompt_tokens\": 150,\n" +
                "    \"completion_tokens\": 85,\n" +
                "    \"total_tokens\": 235\n" +
                "  }\n" +
                "}",
                reasoning.replace("\"", "\\\""), confidence);

        wireMockServer.stubFor(WireMock.post("/v1/chat/completions")
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.containing("sentiment analysis"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed POST /v1/chat/completions for bearish sentiment (confidence: {})", confidence);
    }

    /**
     * Mock the chat completions endpoint for neutral sentiment analysis.
     *
     * @param reasoning the reasoning text to return
     * @param confidence the confidence score (0.0 to 1.0)
     */
    public void mockNeutralSentiment(String reasoning, double confidence) {
        String response = String.format("{\n" +
                "  \"id\": \"chatcmpl-1234567890\",\n" +
                "  \"object\": \"chat.completion\",\n" +
                "  \"created\": 1709876543,\n" +
                "  \"model\": \"gpt-4-turbo\",\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"message\": {\n" +
                "        \"role\": \"assistant\",\n" +
                "        \"content\": \"{\\\"sentiment\\\": \\\"NEUTRAL\\\", \\\"reasoning\\\": \\\"%s\\\", \\\"confidence\\\": %.2f}\"\n" +
                "      },\n" +
                "      \"finish_reason\": \"stop\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"prompt_tokens\": 150,\n" +
                "    \"completion_tokens\": 85,\n" +
                "    \"total_tokens\": 235\n" +
                "  }\n" +
                "}",
                reasoning.replace("\"", "\\\""), confidence);

        wireMockServer.stubFor(WireMock.post("/v1/chat/completions")
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.containing("sentiment analysis"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed POST /v1/chat/completions for neutral sentiment (confidence: {})", confidence);
    }

    /**
     * Mock the chat completions endpoint with a custom sentiment response.
     *
     * @param sentiment the sentiment type (POSITIVE, NEGATIVE, NEUTRAL)
     * @param reasoning the reasoning text
     * @param confidence the confidence score (0.0 to 1.0)
     */
    public void mockSentimentResponse(String sentiment, String reasoning, double confidence) {
        String response = String.format("{\n" +
                "  \"id\": \"chatcmpl-1234567890\",\n" +
                "  \"object\": \"chat.completion\",\n" +
                "  \"created\": 1709876543,\n" +
                "  \"model\": \"gpt-4-turbo\",\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"index\": 0,\n" +
                "      \"message\": {\n" +
                "        \"role\": \"assistant\",\n" +
                "        \"content\": \"{\\\"sentiment\\\": \\\"%s\\\", \\\"reasoning\\\": \\\"%s\\\", \\\"confidence\\\": %.2f}\"\n" +
                "      },\n" +
                "      \"finish_reason\": \"stop\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"prompt_tokens\": 150,\n" +
                "    \"completion_tokens\": 85,\n" +
                "    \"total_tokens\": 235\n" +
                "  }\n" +
                "}",
                sentiment, reasoning.replace("\"", "\\\""), confidence);

        wireMockServer.stubFor(WireMock.post("/v1/chat/completions")
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed POST /v1/chat/completions for custom sentiment: {}", sentiment);
    }

    /**
     * Mock the chat completions endpoint with an error response.
     *
     * @param errorCode the error code
     * @param errorMessage the error message
     */
    public void mockError(String errorCode, String errorMessage) {
        String response = String.format("{\n" +
                "  \"error\": {\n" +
                "    \"code\": \"%s\",\n" +
                "    \"message\": \"%s\",\n" +
                "    \"type\": \"api_error\"\n" +
                "  }\n" +
                "}",
                errorCode, errorMessage);

        wireMockServer.stubFor(WireMock.post("/v1/chat/completions")
                .willReturn(WireMock.aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        logger.info("Stubbed POST /v1/chat/completions error: {}", errorCode);
    }

    /**
     * Mock the chat completions endpoint with a timeout response.
     */
    public void mockTimeout() {
        wireMockServer.stubFor(WireMock.post("/v1/chat/completions")
                .willReturn(WireMock.aResponse()
                        .withStatus(408)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": \"TIMEOUT\",\n" +
                                "    \"message\": \"Request timed out\"\n" +
                                "  }\n" +
                                "}")));

        logger.info("Stubbed POST /v1/chat/completions timeout");
    }

    /**
     * Verify that the chat completions endpoint was called.
     *
     * @return true if chat completions was called at least once
     */
    public boolean verifyChatCompletionsCalled() {
        try {
            WireMock.verify(WireMock.postRequestedFor(
                    WireMock.urlEqualTo("/v1/chat/completions")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all requests made to the chat completions endpoint.
     *
     * @return list of requests
     */
    public java.util.List<com.github.tomakehurst.wiremock.verification.LoggedRequest>
    getAllChatCompletionsRequests() {
        return wireMockServer.findAll(
                WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/chat/completions")));
    }

    /**
     * Reset all stubs and requests.
     */
    public void resetAll() {
        wireMockServer.resetAll();
        logger.info("WireMock helper reset all stubs and requests");
    }
}
