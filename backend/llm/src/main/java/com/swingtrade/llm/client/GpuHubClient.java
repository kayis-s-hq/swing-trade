package com.swingtrade.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with external GPUHub vLLM serving LLM APIs.
 * Used only for adhoc "super analysis" synthesis (SynthesisService).
 */
@Component
public class GpuHubClient {

    private static final Logger logger = LoggerFactory.getLogger(GpuHubClient.class);

    static final Duration LLM_READ_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient webClient;
    private final String baseUrl;
    private final String modelName;

    public GpuHubClient(
            WebClient.Builder webClientBuilder,
            @Value("${llm.gpuhub.base-url:https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1}") String baseUrl,
            @Value("${llm.gpuhub.model-name:gemma-3-27b-it}") String modelName) {
        this.webClient = webClientBuilder
                .defaultHeader("Content-Type", "application/json")
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(LLM_READ_TIMEOUT)))
                .build();
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    /**
     * Generates chat completion using the GPUHub vLLM endpoint.
     * Intended for high-quality composite analysis synthesis.
     */
    public Mono<String> generateChatCompletion(List<Map<String, String>> messages,
                                                int maxTokens,
                                                double temperature) {
        logger.debug("Generating GPUHub chat completion with {} messages (model: {}, maxTokens: {})",
                messages.size(), modelName, maxTokens);

        Map<String, Object> request = Map.of(
                "model", modelName,
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
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> {
                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        return response.getChoices().get(0).getMessage().getContent();
                    }
                    return null;
                })
                .doOnSuccess(result -> logger.debug("GPUHub chat completion complete, received {} chars",
                        result != null ? result.length() : 0))
                .doOnError(error -> logger.error("GPUHub chat completion failed: {}", error.getMessage()));
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
