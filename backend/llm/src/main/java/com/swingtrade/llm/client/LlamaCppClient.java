package com.swingtrade.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.llm.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with local llama.cpp server via OpenAI-compatible API.
 * Primary endpoint for sentiment analysis.
 * Reads model config from AppSettingsStore so changes persist at runtime.
 */
@Component
public class LlamaCppClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(LlamaCppClient.class);

    static final Duration LLAMA_READ_TIMEOUT = Duration.ofSeconds(2850);

    private final WebClient webClient;
    private final AppSettingsStore appSettingsStore;
    private final LlmProperties properties;

    public LlamaCppClient(WebClient.Builder webClientBuilder,
                          AppSettingsStore appSettingsStore,
                          LlmProperties properties) {
        this.webClient = webClientBuilder
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(LLAMA_READ_TIMEOUT)))
                .build();
        this.appSettingsStore = appSettingsStore;
        this.properties = properties;
    }

    /**
     * Generates chat completion using the local llama.cpp server.
     */
    @Override
    public Mono<String> generateChatCompletion(List<Map<String, String>> messages,
                                                int maxTokens,
                                                double temperature) {
        logger.debug("Generating chat completion with {} messages (model: {}, maxTokens: {})",
                messages.size(), getModelName(), maxTokens);

        String baseUrl = appSettingsStore.get("llm.base_url")
                .orElseGet(() -> properties.getProviders().getPiSsh().getBaseUrl().toString());

        Map<String, Object> request = Map.of(
                "model", getModelName(),
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", temperature,
                "top_p", 0.9,
                "n", 1,
                "stream", false,
                "response_format", Map.of("type", "json_object")
        );

        return webClient
                .post()
                .uri(baseUrl + "/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> {
                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
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

        List<Map<String, String>> messages = List.of(systemMessage, userMessage);

        return generateChatCompletion(messages, 512, 0.1)
                .doOnSuccess(result -> logger.debug("Structured extraction complete"))
                .doOnError(error -> logger.error("Structured extraction failed: {}", error.getMessage()));
    }

    private String getModelName() {
        return appSettingsStore.get("llamacpp.model")
                .orElseGet(() -> properties.getLlamaCpp().getModel());
    }

    // ========== Response DTOs ==========

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatCompletionResponse {
        @JsonProperty("choices")
        private List<ChatChoice> choices;

        public List<ChatChoice> getChoices() { return choices; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatChoice {
        @JsonProperty("message")
        private Message message;

        public Message getMessage() { return message; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {
        @JsonProperty("content")
        private String content;
        @JsonProperty("reasoning")
        private String reasoning;

        public String getContent() {
            return content != null ? content : reasoning;
        }
    }
}
