package com.swingtrade.llm.config;

import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.llm.client.SpringAiLlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration class for LLM module.
 * Provides four OpenAiChatModel beans (local, pi_ssh, openai, mlx),
 * a default ChatClient/SpringAiLlmClient, and a news executor.
 * Reads base URLs from AppSettingsStore (DB) so the settings UI controls endpoints.
 */
@Configuration
@EnableAutoConfiguration(exclude = {
    OpenAiAudioSpeechAutoConfiguration.class,
    OpenAiAudioTranscriptionAutoConfiguration.class,
    OpenAiChatAutoConfiguration.class,
    OpenAiEmbeddingAutoConfiguration.class,
    OpenAiImageAutoConfiguration.class,
    OpenAiModerationAutoConfiguration.class
})
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
                "http://localhost:8080", "openai.model", "qwen3-4b", apiKey);
    }

    @Bean
    public OpenAiChatModel piSshChatModel(
            AppSettingsStore appSettingsStore,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "llm.base_url",
                "http://piworm.local:8090", "openai.model", "qwen3-4b", apiKey);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(
            AppSettingsStore appSettingsStore,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "openai.base_url",
                "https://api.openai.com", "openai.model", "gpt-4o", apiKey);
    }

    @Bean
    public OpenAiChatModel mlxChatModel(
            AppSettingsStore appSettingsStore,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "mlx.server.url",
                "http://192.168.1.50:8081", "mlx.model", "Qwen/Qwen2.5-3B-Instruct", apiKey);
    }

    private OpenAiChatModel createChatModel(AppSettingsStore settings, String urlKey,
                                            String defaultUrl, String modelKey,
                                            String defaultModel, String apiKey) {
        String baseUrl = settings.get(urlKey)
                .map(this::stripTrailingV1)
                .orElse(defaultUrl);
        String model = settings.get(modelKey).orElse(defaultModel);
        String key = resolveApiKey(settings, apiKey);

        // Use RestClient with SimpleClientHttpRequestFactory (JDK HttpURLConnection)
        // to avoid Spring's JettyClientHttpRequestFactory which requires Jetty 11 API
        RestClient.Builder rcBuilder = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory());

        OpenAiApi openAiApi = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(key)
            .restClientBuilder(rcBuilder)
            .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.2)
            .build();

        return OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(options)
            .build();
    }

    private String resolveApiKey(AppSettingsStore settings, String springDefault) {
        String key = settings.get("openai.api_key").orElse(springDefault);
        if ("none".equals(key) || key.isBlank()) {
            return "";
        }
        return key;
    }

    private String stripTrailingV1(String url) {
        if (url == null) return null;
        String normalized = url.endsWith("/v1") ? url.substring(0, url.length() - 3) : url;
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
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