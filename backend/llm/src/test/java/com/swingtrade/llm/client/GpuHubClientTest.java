package com.swingtrade.llm.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuHubClientTest {

    private MockWebServer server;
    private GpuHubClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new GpuHubClient(WebClient.builder(), server.url("/").toString(), "gpu-model");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void validatesRequestAndMapsAssistantContent() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}"));

        assertThat(client.generateChatCompletion(
                List.of(Map.of("role", "user", "content", "question")), 64, 0.4).block())
                .isEqualTo("answer");
        String body = server.takeRequest().getBody().readUtf8();
        assertThat(body).contains("\"model\":\"gpu-model\"")
                .contains("\"max_tokens\":64")
                .contains("\"temperature\":0.4")
                .contains("\"response_format\":{\"type\":\"json_object\"}");
    }

    @Test
    void usesReasoningFallbackAndReturnsNullForNoChoices() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"reasoning\":\"thinking\"}}]}"));
        assertThat(client.generateChatCompletion(List.of(), 1, 0).block()).isEqualTo("thinking");

        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[]}"));
        assertThat(client.generateChatCompletion(List.of(), 1, 0).block()).isNull();
    }

    @Test
    void propagatesServerErrors() {
        server.enqueue(new MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "text/plain")
                .setBody("upstream failure"));

        assertThatThrownBy(() -> client.generateChatCompletion(List.of(), 1, 0).block())
                .hasMessageContaining("500");
    }
}
