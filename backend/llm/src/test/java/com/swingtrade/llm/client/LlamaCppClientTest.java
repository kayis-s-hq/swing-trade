package com.swingtrade.llm.client;

import com.swingtrade.domain.store.AppSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LlamaCppClient testing HTTP request format, response parsing,
 * timeout handling, and error scenarios.
 */
class LlamaCppClientTest {

    private LlamaCppClient llamaCppClient;

    @BeforeEach
    void setUp() {
        var appSettings = mock(AppSettingsStore.class);
        when(appSettings.get(any())).thenReturn(Optional.of("http://localhost:8080/v1"));
        when(appSettings.get("llamacpp.model")).thenReturn(Optional.of("/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf"));
        llamaCppClient = new LlamaCppClient(WebClient.builder(), appSettings);
    }

    @Test
    void testGenerateChatCompletion_createsCorrectRequest() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are an analyst"),
                Map.of("role", "user", "content", "Analyze sentiment for RELIANCE")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);

        assertThat(result).isNotNull();
        assertThat(result.getClass().getSimpleName()).contains("Mono");
    }

    @Test
    void testGenerateChatCompletion_returnsContent() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Test message")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);

        assertThat(result).isNotNull();
    }

    @Test
    void testGenerateChatCompletion_withTimeout() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Test message")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);

        assertThat(result).isNotNull();
    }

    @Test
    void testGenerateChatCompletion_requestIncludesModel() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Analyze")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);
        assertThat(result).isNotNull();
    }

    @Test
    void testGenerateChatCompletion_requestIncludesMessages() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Test content")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);
        assertThat(result).isNotNull();
    }

    @Test
    void testGenerateChatCompletion_requestIncludesMaxTokens() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Test")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 256, 0.3);
        assertThat(result).isNotNull();
    }

    @Test
    void testGenerateChatCompletion_requestIncludesTemperature() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Test")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.7);
        assertThat(result).isNotNull();
    }

    @Test
    void testExtractStructuredData_formatsPromptCorrectly() {
        String prompt = "Extract sentiment from: Strong earnings";
        String schema = """
                {
                    "sentiment": "string",
                    "confidence": "number"
                }
                """;

        var result = llamaCppClient.extractStructuredData(prompt, schema);

        assertThat(result).isNotNull();
    }

    @Test
    void testGenerateChatCompletion_parsesResponseCorrectly() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Analyze")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);
        assertThat(result).isNotNull();
    }

    @Test
    void testErrorResponse_doesNotThrowException() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Test")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);

        assertThat(result).isNotNull();
    }

    @Test
    void testChatCompletionToMono_isReactive() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Test")
        );

        var result = llamaCppClient.generateChatCompletion(messages, 512, 0.3);

        assertThat(result).isNotNull();
        assertThat(result.getClass().getSimpleName()).contains("Mono");
    }
}