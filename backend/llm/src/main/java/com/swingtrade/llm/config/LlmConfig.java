package com.swingtrade.llm.config;

import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.llm.client.SpringAiLlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration class for LLM module.
 * Provides three OpenAiChatModel beans (local, pi_ssh, openai),
 * a default ChatClient/SpringAiLlmClient, and a news executor.
 * Reads base URLs from AppSettingsStore (DB) so the settings UI controls endpoints.
 *
 * Spring AI 2.0.1 — uses official openai-java SDK with OkHttp transport.
 */
@Configuration
public class LlmConfig {

    @Bean
    public ExecutorService newsExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "news-fetcher");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public OpenAiChatModel localChatModel(
            AppSettingsStore appSettingsStore,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "llm.base_url",
                "http://localhost:8080/v1", "openai.model", "qwen3-4b", apiKey);
    }

    @Bean
    public OpenAiChatModel piSshChatModel(
            AppSettingsStore appSettingsStore,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "llm.base_url",
                "http://piworm.local:8090/v1", "openai.model", "qwen3-4b", apiKey);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(
            AppSettingsStore appSettingsStore,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "openai.base_url",
                "https://api.openai.com/v1", "openai.model", "gpt-4o", apiKey);
    }

    @Bean
    public OpenAiChatModel ollamaChatModel(
            AppSettingsStore appSettingsStore,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "ollama.base_url",
                "http://localhost:11434/v1", "ollama.model", "qwen3:4b", apiKey);
    }

    private OpenAiChatModel createChatModel(AppSettingsStore settings, String urlKey,
                                            String defaultUrl, String modelKey,
                                            String defaultModel, String apiKey) {
        String baseUrl = settings.get(urlKey)
                .orElse(defaultUrl);
        String model = settings.get(modelKey).orElse(defaultModel);
        String key = resolveApiKey(settings, apiKey);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(model)
            .baseUrl(baseUrl)
            .apiKey(key)
            .temperature(0.2)
            .build();

        return OpenAiChatModel.builder()
            .options(options)
            .build();
    }

    private String resolveApiKey(AppSettingsStore settings, String springDefault) {
        String key = settings.get("openai.api_key").orElse(springDefault);
        if ("none".equals(key) || key.isBlank()) {
            key = settings.get("gpuhub.api_key").orElse(key);
        }
        if ("none".equals(key) || key.isBlank()) {
            return "";
        }
        return key;
    }

    @Primary
    @Bean
    public ChatClient chatClient(@Qualifier("localChatModel") OpenAiChatModel localChatModel) {
        return ChatClient.create(localChatModel);
    }

    @Bean
    public SpringAiLlmClient springAiLlmClient(ChatClient chatClient,
                                               @Value("${llm.cot.enabled:false}") boolean enableCoT) {
        return new SpringAiLlmClient(chatClient, enableCoT);
    }
}