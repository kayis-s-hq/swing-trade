package com.swingtrade.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swingtrade.domain.store.AppSettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Client for OpenAI-compatible LLM endpoint via OpenAI API.
 * Used when llm.backend=gpuhub in settings.
 */
@Component
public class GpuHubLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(GpuHubLlmClient.class);

    private final WebClient webClient;
    private final AppSettingsStore appSettingsStore;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    public GpuHubLlmClient(WebClient.Builder webClientBuilder,
                           AppSettingsStore appSettingsStore) {
        this.webClient = webClientBuilder
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.appSettingsStore = appSettingsStore;
    }

    @Override
    public Mono<String> generateChatCompletion(List<Map<String, String>> messages,
                                                int maxTokens,
                                                double temperature) {
        String baseUrl = appSettingsStore.get("openai.base_url")
                .orElse(openaiBaseUrl);

        String model = appSettingsStore.get("openai.model")
                .orElse("gpt-4o");

        String apiKey = appSettingsStore.get("openai.api_key").orElse("");

        WebClient webClient = this.webClient;
        if (apiKey != null && !apiKey.isBlank()) {
            webClient = webClient.mutate()
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
        }

        Map<String, Object> request = Map.of(
                "model", model,
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
                .doOnSuccess(result -> logger.debug("OpenAI chat completion complete, received {} chars",
                        result != null ? result.length() : 0))
                .doOnError(error -> logger.error("OpenAI chat completion failed: {}", error.getMessage()));
    }

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