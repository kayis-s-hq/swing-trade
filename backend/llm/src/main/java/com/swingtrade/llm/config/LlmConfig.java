package com.swingtrade.llm.config;

import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.llm.client.SpringAiLlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration class for LLM module.
 * Provides four OpenAiChatModel beans (local, pi_ssh, openai, ollama),
 * a default ChatClient/SpringAiLlmClient, and a news executor.
 * Reads base URLs from AppSettingsStore (DB) so the settings UI controls endpoints.
 *
 * Spring AI 2.0.1 — uses official openai-java SDK with OkHttp transport.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    /**
     * Spring AI's {@code AbstractOpenAiOptions.DEFAULT_TIMEOUT} is 60 seconds with
     * {@code DEFAULT_MAX_RETRIES} of 3 (i.e. up to 4 total HTTP attempts) when a
     * chat model is built without an explicit timeout/maxRetries — see
     * {@code org.springframework.ai.openai.AbstractOpenAiOptions}. That default is
     * fine for GPU-backed endpoints (openai, pi_ssh, gpuhub) that reliably respond
     * within tens of seconds, but a local, CPU-bound Ollama instance can easily take
     * several minutes to generate a large completion. Left unconfigured, 4 attempts
     * x 60s each burns ~4 minutes before failing — which is exactly what caused the
     * silent 243s "Stage 9" hang: every attempt was independently timing out and
     * being retried, not a single slow-but-working call.
     *
     * Confirmed by testing: a single attempt with a 300s timeout still failed with
     * okhttp3 SocketTimeoutException while awaiting response headers (see
     * SynthesisService's WARN/DEBUG logs) — qwen3:4b on CPU genuinely needs several
     * minutes of wall time to generate a maxTokens:1024 response for a synthesis
     * prompt this size, it is not hanging/erroring on the Ollama side. 570s gives it
     * the most runway possible while staying under SynthesisService's outer 600s
     * Mono#block deadline, so a true failure still surfaces within ~10 minutes.
     *
     * Only the Ollama bean gets this override; the other three backends keep Spring
     * AI's defaults untouched so their (intentionally tighter) behavior is unaffected.
     */
    private static final Duration OLLAMA_TIMEOUT = Duration.ofSeconds(570);
    private static final int OLLAMA_MAX_RETRIES = 0;

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
            LlmProperties properties,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "llm.base_url", "openai.model",
                properties.getProviders().getLocal(), apiKey);
    }

    @Bean
    public OpenAiChatModel piSshChatModel(
            AppSettingsStore appSettingsStore,
            LlmProperties properties,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "llm.base_url", "openai.model",
                properties.getProviders().getPiSsh(), apiKey);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(
            AppSettingsStore appSettingsStore,
            LlmProperties properties,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        return createChatModel(appSettingsStore, "openai.base_url", "openai.model",
                properties.getProviders().getOpenai(), apiKey);
    }

    @Bean
    public OpenAiChatModel ollamaChatModel(
            AppSettingsStore appSettingsStore,
            LlmProperties properties,
            @Value("${spring.ai.openai.api-key:none}") String apiKey) {
        // CPU-bound local inference: generous per-attempt timeout, no retries (see
        // OLLAMA_TIMEOUT/OLLAMA_MAX_RETRIES javadoc above) — a slow-but-working
        // request should be given time to finish rather than be replayed.
        return createChatModel(appSettingsStore, "ollama.base_url", "ollama.model",
                properties.getProviders().getOllama(), apiKey, OLLAMA_TIMEOUT, OLLAMA_MAX_RETRIES);
    }

    private OpenAiChatModel createChatModel(AppSettingsStore settings, String urlKey,
                                            String modelKey, LlmProperties.Provider defaults,
                                            String apiKey) {
        return createChatModel(settings, urlKey, modelKey, defaults, apiKey, null, null);
    }

    private OpenAiChatModel createChatModel(AppSettingsStore settings, String urlKey,
                                            String modelKey, LlmProperties.Provider defaults,
                                            String apiKey, Duration timeout, Integer maxRetries) {
        String baseUrl = settings.get(urlKey)
                .orElseGet(() -> defaults.getBaseUrl().toString());
        String model = settings.get(modelKey).orElseGet(defaults::getModel);
        String key = resolveApiKey(settings, apiKey);

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
            .model(model)
            .baseUrl(baseUrl)
            .apiKey(key)
            .temperature(0.2);
        if (timeout != null) {
            optionsBuilder.timeout(timeout);
        }
        if (maxRetries != null) {
            optionsBuilder.maxRetries(maxRetries);
        }

        return OpenAiChatModel.builder()
            .options(optionsBuilder.build())
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