package com.swingtrade.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.llm.service.LlmBackendSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ConnectTimeoutException;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with llama.cpp server via OpenAI-compatible API.
 * Used for both local and Pi SSH backends.
 * Reads base URL from AppSettingsStore so changes persist at runtime.
 */
@Component
public class LlamaCppClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(LlamaCppClient.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:8080/v1";
    private static final String DEFAULT_MODEL = "/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf";
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    private final WebClient webClient;
    private final AppSettingsStore appSettingsStore;
    private final LlmBackendSelector selector;
    private final String sshHost;
    private final int sshPort;

    public LlamaCppClient(WebClient.Builder webClientBuilder,
                          AppSettingsStore appSettingsStore,
                          LlmBackendSelector selector,
                          @Value("${llamacpp.ssh.host:192.168.0.100}") String sshHost,
                          @Value("${llamacpp.port:8090}") int sshPort) {
        this.webClient = webClientBuilder
                .defaultHeader("Content-Type", "application/json")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.appSettingsStore = appSettingsStore;
        this.selector = selector;
        this.sshHost = sshHost;
        this.sshPort = sshPort;
    }

    /**
     * Generates chat completion using the llama.cpp server.
     */
    public Mono<String> generateChatCompletion(List<Map<String, String>> messages,
                                                int maxTokens,
                                                double temperature) {
        logger.debug("Generating chat completion with {} messages (model: {}, maxTokens: {})",
                messages.size(), getModelName(), maxTokens);

        String baseUrl = resolveBaseUrl();

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
                .timeout(READ_TIMEOUT)
                .map(response -> {
                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        return response.getChoices().get(0).getMessage().getContent();
                    }
                    return null;
                })
                .doOnSuccess(result -> logger.debug("Chat completion complete, received {} chars",
                        result != null ? result.length() : 0))
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        logger.error("Chat completion timed out after {}s: {}", READ_TIMEOUT.toSeconds(), error.getMessage());
                    } else {
                        logger.error("Chat completion failed: {}", error.getMessage());
                    }
                });
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
                .orElse(DEFAULT_MODEL);
    }

    private String resolveBaseUrl() {
        var backend = selector.resolve();
        if (backend == LlmBackendSelector.Backend.PI_SSH) {
            String url = String.format("http://%s:%d/v1", sshHost, sshPort);
            logger.debug("Using Pi SSH base URL: {}", url);
            return url;
        }
        return appSettingsStore.get("llm.base_url")
                .orElse(DEFAULT_BASE_URL);
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
