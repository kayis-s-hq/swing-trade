package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ApiResponse;
import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.data.service.AppSettingsService;
import com.swingtrade.data.service.MarketDataClientProvider;
import com.swingtrade.llm.config.LlmProperties;
import com.swingtrade.llm.service.LlamaCppServerManager;
import com.swingtrade.llm.service.LlmBackendSelector;
import com.swingtrade.llm.service.LlmClientProvider;
import com.swingtrade.llm.service.PiLlamaServerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerDefaultsTest {

    @Mock
    private MarketDataClientProvider marketDataClientProvider;
    @Mock
    private AppSettingsService appSettingsService;
    @Mock
    private DiscordNotificationService discordNotificationService;
    @Mock
    private LlmBackendSelector selector;
    @Mock
    private LlmClientProvider llmClientProvider;
    @Mock
    private LlamaCppServerManager localServerManager;
    @Mock
    private PiLlamaServerManager piServerManager;

    private SettingsController controller;

    @BeforeEach
    void setUp() {
        lenient().when(appSettingsService.get(anyString(), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(appSettingsService.get(anyString())).thenReturn(Optional.empty());
        controller = new SettingsController(
            marketDataClientProvider,
            appSettingsService,
            properties(),
            discordNotificationService,
            selector,
            llmClientProvider,
            localServerManager,
            piServerManager
        );
    }

    private LlmProperties properties() {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl(URI.create("http://active-default.test/v1"));
        properties.setBackend("pi_ssh");
        configureProvider(properties.getProviders().getOpenai(),
            "https://openai-default.test/v1", "openai-default");
        configureProvider(properties.getProviders().getOllama(),
            "http://ollama-default.test/v1", "ollama-default");
        configureProvider(properties.getProviders().getPiSsh(),
            "http://pi-default.test/v1", "pi-default");
        properties.getLlamaCpp().setModel("/models/default.gguf");
        properties.getPdf().setBaseUrl(URI.create("http://pdf-default.test/v1"));
        properties.getPdf().setModel("pdf-default");
        return properties;
    }

    private void configureProvider(LlmProperties.Provider provider, String baseUrl, String model) {
        provider.setBaseUrl(URI.create(baseUrl));
        provider.setModel(model);
    }

    @Nested
    @DisplayName("GET /api/settings/llm")
    class GetLlmSettings {

        @Test
        void shouldReturnConfiguredDefaultsWhenDatabaseHasNoOverride() {
            Map<String, String> settings = controller.getLlmSettings().getBody().data();

            assertThat(settings)
                .containsEntry("llm.base_url", "http://active-default.test/v1")
                .containsEntry("llm.backend", "pi_ssh")
                .containsEntry("openai.base_url", "https://openai-default.test/v1")
                .containsEntry("openai.model", "openai-default")
                .containsEntry("ollama.base_url", "http://ollama-default.test/v1")
                .containsEntry("ollama.model", "ollama-default")
                .containsEntry("llamacpp.model", "/models/default.gguf")
                .containsEntry("llm.pdf.base_url", "http://pdf-default.test/v1")
                .containsEntry("llm.pdf.model", "pdf-default")
                .doesNotContainKeys("openai.api_key", "ollama.api_key", "gpuhub.api_key");
        }

        @Test
        void shouldPreferDatabaseOverrides() {
            when(appSettingsService.get("ollama.model", "ollama-default"))
                .thenReturn("database-model");

            Map<String, String> settings = controller.getLlmSettings().getBody().data();

            assertThat(settings).containsEntry("ollama.model", "database-model");
        }

        @Test
        void shouldReturnSecretConfigurationStateWithoutSecretValue() {
            when(appSettingsService.get("openai.api_key"))
                .thenReturn(Optional.of("top-secret"));

            Map<String, String> settings = controller.getLlmSettings().getBody().data();

            assertThat(settings)
                .containsEntry("openai.api_key.configured", "true")
                .doesNotContainValue("top-secret")
                .doesNotContainKey("openai.api_key");
        }

        @Test
        void shouldHandleNullPdfBaseUrlWithoutThrowingNullPointerException() {
            // Build a properties fixture with PDF baseUrl left null (simulate unset env var)
            LlmProperties props = new LlmProperties();
            props.setBaseUrl(URI.create("http://active-default.test/v1"));
            props.setBackend("pi_ssh");
            configureProvider(props.getProviders().getOpenai(),
                "https://openai-default.test/v1", "openai-default");
            configureProvider(props.getProviders().getOllama(),
                "http://ollama-default.test/v1", "ollama-default");
            configureProvider(props.getProviders().getPiSsh(),
                "http://pi-default.test/v1", "pi-default");
            props.getLlamaCpp().setModel("/models/default.gguf");
            props.getPdf().setModel("pdf-default");
            // Intentionally do NOT set PDF baseUrl — it will stay null

            SettingsController testController = new SettingsController(
                marketDataClientProvider,
                appSettingsService,
                props,
                discordNotificationService,
                selector,
                llmClientProvider,
                localServerManager,
                piServerManager
            );

            Map<String, String> settings = testController.getLlmSettings().getBody().data();

            // Should return empty string as default, not throw NPE
            assertThat(settings)
                .containsEntry("llm.pdf.base_url", "")
                .containsEntry("llm.pdf.model", "pdf-default");
        }
    }

    @Nested
    @DisplayName("PUT /api/settings/llm")
    class SetLlmSettings {

        @Test
        void shouldPersistSecretWithoutEchoingIt() {
            when(appSettingsService.get("openai.api_key"))
                .thenReturn(Optional.of("top-secret"));

            ResponseEntity<ApiResponse<Map<String, String>>> response = controller.setLlmSettings(
                Map.of("openai.api_key", "top-secret"));

            verify(appSettingsService).set("openai.api_key", "top-secret");
            assertThat(response.getBody().data())
                .containsEntry("openai.api_key.configured", "true")
                .doesNotContainKey("openai.api_key")
                .doesNotContainValue("top-secret");
        }
    }

    @Nested
    @DisplayName("Inference endpoint URI")
    class InferenceEndpointUri {

        @Test
        void shouldUseServerControlledPiUrlInsteadOfRuntimeSetting() {
            lenient().when(appSettingsService.get(eq("llm.base_url"), anyString()))
                .thenReturn("http://attacker.test/v1");

            assertThat(controller.piInferenceBaseUrl())
                .isEqualTo(URI.create("http://pi-default.test/v1"));
            verify(appSettingsService, never()).get(eq("llm.base_url"), anyString());
        }

        @Test
        void shouldRejectOllamaUrlWithUnapprovedOrigin() {
            when(appSettingsService.get(
                "ollama.base_url", "http://ollama-default.test/v1"))
                .thenReturn("http://attacker.test/v1");

            assertThat(controller.ollamaInferenceBaseUrl())
                .isEqualTo(URI.create("http://ollama-default.test/v1"));
        }

        @Test
        void shouldAppendChatCompletionsPathWithoutStringConcatenation() {
            assertThat(SettingsController.chatCompletionsUri(
                URI.create("http://localhost:11434/v1")))
                .isEqualTo(URI.create("http://localhost:11434/v1/chat/completions"));
        }
    }
}
