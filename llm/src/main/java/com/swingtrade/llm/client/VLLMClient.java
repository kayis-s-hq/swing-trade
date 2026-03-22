package com.swingtrade.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with vLLM serving LLM APIs.
 * Uses OpenAI-compatible interface for sentiment analysis.
 */
@Component
public class VLLMClient {

    private static final Logger logger = LoggerFactory.getLogger(VLLMClient.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String modelName;

    /**
     * Constructs VLLM client with required dependencies.
     *
     * @param webClientBuilder WebClient builder
     * @param vllmBaseUrl Base URL of vLLM server (e.g., http://localhost:8000/v1)
     * @param modelName Model name to use (e.g., qwen3)
     */
    public VLLMClient(WebClient.Builder webClientBuilder,
                      @Value("${llm.vllm.base-url:http://localhost:8000/v1}") String vllmBaseUrl,
                      @Value("${llm.vllm.model-name:qwen3}") String modelName) {

        this.baseUrl = vllmBaseUrl;
        this.modelName = modelName;

        this.webClient = webClientBuilder
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Generates text completion using the LLM.
     *
     * @param prompt the input prompt
     * @param maxTokens maximum tokens to generate
     * @param temperature temperature for sampling (0.0 = deterministic, 1.0 = random)
     * @return Mono containing the completion text
     */
    public Mono<String> generateCompletion(String prompt, int maxTokens, double temperature) {
        logger.debug("Generating completion with prompt: {} (truncated)",
                     prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt);

        Map<String, Object> request = Map.of(
                "model", modelName,
                "prompt", prompt,
                "max_tokens", maxTokens,
                "temperature", temperature,
                "top_p", 0.9,
                "n", 1,
                "stop", null,
                "stream", false
        );

        return webClient.post()
                .uri(baseUrl + "/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CompletionResponse.class)
                .map(response -> {
                    if (response != null && response.getChoices() != null &&
                            !response.getChoices().isEmpty()) {
                        return response.getChoices().get(0).getText();
                    }
                    return null;
                })
                .doOnSuccess(result -> logger.debug("Generation complete, received {} chars",
                                                    result != null ? result.length() : 0))
                .doOnError(error -> logger.error("Generation failed: {}", error.getMessage()));
    }

    /**
     * Generates chat completion using the LLM.
     *
     * @param messages list of chat messages (system, user, assistant)
     * @param maxTokens maximum tokens to generate
     * @param temperature temperature for sampling
     * @return Mono containing the assistant response
     */
    public Mono<String> generateChatCompletion(List<Map<String, String>> messages,
                                                int maxTokens,
                                                double temperature) {
        logger.debug("Generating chat completion with {} messages", messages.size());

        Map<String, Object> request = Map.of(
                "model", modelName,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", temperature,
                "top_p", 0.9,
                "n", 1,
                "stream", false
        );

        return webClient.post()
                .uri(baseUrl + "/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> {
                    if (response != null && response.getChoices() != null &&
                            !response.getChoices().isEmpty()) {
                        return response.getChoices().get(0).getMessage().getContent();
                    }
                    return null;
                })
                .doOnSuccess(result -> logger.debug("Chat completion complete, received {} chars",
                                                    result != null ? result.length() : 0))
                .doOnError(error -> logger.error("Chat completion failed: {}", error.getMessage()));
    }

    /**
     * Extracts structured data from text using LLM.
     *
     * @param prompt the prompt asking for structured output
     * @param responseFormat expected response format (JSON schema as string)
     * @return Mono containing the JSON response
     */
    public Mono<String> extractStructuredData(String prompt, String responseFormat) {
        Map<String, String> systemMessage = Map.of(
                "role", "system",
                "content", "You are a helpful assistant that provides structured JSON responses."
        );
        Map<String, String> userMessage = Map.of(
                "role", "user",
                "content", prompt + "\n\nPlease respond with valid JSON matching the following format:\n" + responseFormat
        );

        List<Map<String, String>> messages = List.of(
                systemMessage,
                userMessage
        );

        return generateChatCompletion(messages, 512, 0.1) // Low temperature for deterministic output
                .doOnSuccess(result -> logger.debug("Structured extraction complete"))
                .doOnError(error -> logger.error("Structured extraction failed: {}", error.getMessage()));
    }

    // ========== Response DTOs ==========

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CompletionResponse {
        @JsonProperty("choices")
        private List<Choice> choices;

        public List<Choice> getChoices() {
            return choices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatCompletionResponse {
        @JsonProperty("choices")
        private List<ChatChoice> choices;

        public List<ChatChoice> getChoices() {
            return choices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Choice {
        @JsonProperty("text")
        private String text;

        public String getText() {
            return text;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatChoice {
        @JsonProperty("message")
        private Message message;

        public Message getMessage() {
            return message;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {
        @JsonProperty("content")
        private String content;

        public String getContent() {
            return content;
        }
    }
}
