package com.swingtrade.llm.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.llm.config.LlmProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlamaCppClientTest {

    private MockWebServer server;
    private AppSettingsStore settings;
    private LlamaCppClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        settings = mock(AppSettingsStore.class);
        when(settings.get("llm.base_url")).thenReturn(Optional.of(server.url("/").toString()));
        when(settings.get("llamacpp.model")).thenReturn(Optional.of("runtime-model"));

        LlmProperties properties = new LlmProperties();
        properties.getProviders().getPiSsh().setBaseUrl(URI.create("http://unused.example"));
        properties.getLlamaCpp().setModel("default-model");
        client = new LlamaCppClient(WebClient.builder(), settings, properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void sendsValidatedOpenAiCompatibleRequestAndReturnsContent() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"));

        String result = client.generateChatCompletion(
                List.of(Map.of("role", "user", "content", "Analyze ABC")), 128, 0.25).block();

        assertThat(result).isEqualTo("{\"ok\":true}");
        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"runtime-model\"")
                .contains("\"max_tokens\":128")
                .contains("\"temperature\":0.25")
                .contains("\"stream\":false")
                .contains("\"enable_thinking\":false");
    }

    @Test
    void fallsBackToReasoningWhenContentIsBlank() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"   \",\"reasoning\":\"reasoned\"}}]}"));

        assertThat(client.generateChatCompletion(List.of(Map.of("role", "user", "content", "x")), 1, 0).block())
                .isEqualTo("reasoned");
    }

    @Test
    void propagatesHttpErrorsAndHandlesEmptyChoices() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[]}"));
        assertThat(client.generateChatCompletion(List.of(), 1, 0).block()).isNull();

        server.enqueue(new MockResponse().setResponseCode(503)
                .setHeader("Content-Type", "text/plain")
                .setBody("service unavailable"));
        assertThatThrownBy(() -> client.generateChatCompletion(List.of(), 1, 0).block())
                .hasMessageContaining("503");
    }
}
