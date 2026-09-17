package com.swingtrade.llm.config;

import com.swingtrade.domain.store.AppSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmConfigDefaultsTest {

    @Mock
    private AppSettingsStore appSettingsStore;

    private LlmConfig config;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        config = new LlmConfig();
        properties = properties();
        when(appSettingsStore.get(anyString())).thenReturn(Optional.empty());
    }

    private LlmProperties properties() {
        LlmProperties result = new LlmProperties();
        configureProvider(result.getProviders().getLocal(), "http://local.test/v1", "local-model");
        configureProvider(result.getProviders().getPiSsh(), "http://pi.test/v1", "pi-model");
        result.getLlamaCpp().setModel("/models/pi-model.gguf");
        configureProvider(result.getProviders().getOpenai(), "https://openai.test/v1", "openai-model");
        configureProvider(result.getProviders().getOllama(), "http://ollama.test/v1", "ollama-model");
        return result;
    }

    private void configureProvider(LlmProperties.Provider provider, String baseUrl, String model) {
        provider.setBaseUrl(URI.create(baseUrl));
        provider.setModel(model);
    }

    @Nested
    @DisplayName("Application property defaults")
    class ApplicationPropertyDefaults {

        @Test
        void shouldConfigureEveryProviderFromTypedDefaults() {
            OpenAiChatOptions local = config.localChatModel(appSettingsStore, properties, "test-key").getOptions();
            OpenAiChatOptions pi = config.piSshChatModel(appSettingsStore, properties, "test-key").getOptions();
            OpenAiChatOptions openai = config.openAiChatModel(appSettingsStore, properties, "test-key").getOptions();
            OpenAiChatOptions ollama = config.ollamaChatModel(appSettingsStore, properties, "test-key").getOptions();

            assertThat(local.getBaseUrl()).isEqualTo("http://local.test/v1");
            assertThat(local.getModel()).isEqualTo("local-model");
            assertThat(pi.getBaseUrl()).isEqualTo("http://pi.test/v1");
            assertThat(pi.getModel()).isEqualTo("/models/pi-model.gguf");
            assertThat(openai.getBaseUrl()).isEqualTo("https://openai.test/v1");
            assertThat(openai.getModel()).isEqualTo("openai-model");
            assertThat(ollama.getBaseUrl()).isEqualTo("http://ollama.test/v1");
            assertThat(ollama.getModel()).isEqualTo("ollama-model");
            assertThat(local.getTemperature()).isZero();
            assertThat(pi.getTemperature()).isZero();
            assertThat(openai.getTemperature()).isZero();
            assertThat(ollama.getTemperature()).isZero();
        }
    }

    @Nested
    @DisplayName("App settings overrides")
    class AppSettingsOverrides {

        @Test
        void shouldPreferDatabaseValuesOverApplicationDefaults() {
            when(appSettingsStore.get("openai.base_url"))
                .thenReturn(Optional.of("https://database.test/v1"));
            when(appSettingsStore.get("openai.model"))
                .thenReturn(Optional.of("database-model"));

            OpenAiChatOptions options = config.openAiChatModel(
                appSettingsStore, properties, "test-key").getOptions();

            assertThat(options.getBaseUrl()).isEqualTo("https://database.test/v1");
            assertThat(options.getModel()).isEqualTo("database-model");
        }

        @Test
        void shouldUseTheLoadedLlamaCppModelForPiRequests() {
            when(appSettingsStore.get("llamacpp.model"))
                .thenReturn(Optional.of("/models/stage-qwen.gguf"));

            OpenAiChatOptions options = config.piSshChatModel(
                appSettingsStore, properties, "test-key").getOptions();

            assertThat(options.getModel()).isEqualTo("/models/stage-qwen.gguf");
        }
    }
}
