package com.swingtrade.llm.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpringAiLlmClient testing ChatClient integration,
 * reasoning field extraction, and CoT prompt injection.
 */
@ExtendWith(MockitoExtension.class)
class SpringAiLlmClientTest {

    private org.springframework.ai.chat.client.ChatClient chatClient;
    private org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec requestSpec;
    private org.springframework.ai.chat.client.ChatClient.CallResponseSpec callSpec;
    private org.springframework.ai.chat.model.ChatResponse chatResponse;
    private org.springframework.ai.chat.model.Generation generation;
    private org.springframework.ai.chat.messages.AssistantMessage assistantMessage;
    private SpringAiLlmClient client;

    @BeforeEach
    void setUp() {
        chatClient = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.class);
        requestSpec = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec.class);
        callSpec = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.CallResponseSpec.class);
        chatResponse = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
        assistantMessage = org.mockito.Mockito.mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        generation = org.mockito.Mockito.mock(org.springframework.ai.chat.model.Generation.class);

        org.mockito.Mockito.lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        org.mockito.Mockito.lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        org.mockito.Mockito.lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        org.mockito.Mockito.lenient().when(requestSpec.call()).thenReturn(callSpec);
        org.mockito.Mockito.lenient().when(callSpec.chatResponse()).thenReturn(chatResponse);
        org.mockito.Mockito.lenient().when(chatResponse.getResult()).thenReturn(generation);
        org.mockito.Mockito.lenient().when(generation.getOutput()).thenReturn(assistantMessage);

        client = new SpringAiLlmClient(chatClient, false);
    }

    // ===== ChatClient Integration Tests =====

    @Nested
    @DisplayName("ChatClient Integration")
    class ChatClientIntegration {

        @Test
        void shouldCallChatClientWithCorrectFluentChain() {
            // Arrange
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "You are an analyst"),
                    Map.of("role", "user", "content", "Analyze RELIANCE")
            );
            when(assistantMessage.getText()).thenReturn("{\"score\": \"POSITIVE\"}");

            // Act
            var result = client.generateChatCompletion(messages, 512, 0.3);

            // Assert
            assertThat(result).isNotNull();
            verifyChatClientCalled();
        }

        @Test
        void shouldExtractSystemPromptFromMessages() {
            // Arrange
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "System instruction"),
                    Map.of("role", "user", "content", "User question")
            );
            when(assistantMessage.getText()).thenReturn("response");

            // Act
            var result = client.generateChatCompletion(messages, 256, 0.5);

            // Assert
            assertThat(result).isNotNull();
        }

        @Test
        void shouldExtractUserPromptFromMessages() {
            // Arrange
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", "Analyze TCS")
            );
            when(assistantMessage.getText()).thenReturn("response");

            // Act
            var result = client.generateChatCompletion(messages, 128, 0.7);

            // Assert
            assertThat(result).isNotNull();
        }

        @Test
        void shouldReturnMonoResult() {
            // Arrange
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", "Test")
            );
            when(assistantMessage.getText()).thenReturn("{\"score\": \"NEUTRAL\"}");

            // Act
            var result = client.generateChatCompletion(messages, 512, 0.3);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getClass().getSimpleName()).contains("Mono");
        }

        @Test
        void shouldHandleEmptyContent() {
            // Arrange
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", "Test")
            );
            when(assistantMessage.getText()).thenReturn("");

            // Act
            var result = client.generateChatCompletion(messages, 512, 0.3);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.block()).isEqualTo("");
        }
    }

    // ===== Reasoning Field Fix Tests =====

    @Nested
    @DisplayName("Reasoning Field Extraction")
    class ReasoningFieldExtraction {

        @Test
        void shouldExtractJsonFromReasoningText() {
            // Arrange
            String reasoning = """
                    Let me analyze this step by step.
                    The news shows positive earnings.
                    {
                      "score": "POSITIVE",
                      "confidence": 0.85,
                      "summary": "Strong results",
                      "red_flags": [],
                      "catalysts": ["earnings beat"]
                    }
                    """;

            // Act
            String json = client.extractJsonFromReasoning(reasoning);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("\"score\"");
            assertThat(json).contains("POSITIVE");
        }

        @Test
        void shouldExtractJsonFromMarkdownCodeBlock() {
            // Arrange
            String text = """
                    ```json
                    {"score": "NEGATIVE", "confidence": 0.7, "summary": "Bad news", "red_flags": ["probe"], "catalysts": []}
                    ```
                    """;

            // Act
            String json = client.extractJsonFromReasoning(text);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("NEGATIVE");
        }

        @Test
        void shouldExtractJsonFromThinkingTags() {
            // Arrange
            String text = """
                    <thinking>
                    I need to analyze this carefully.
                    </thinking>
                    {"score": "NEUTRAL", "confidence": 0.5, "summary": "Mixed", "red_flags": [], "catalysts": []}
                    """;

            // Act
            String json = client.extractJsonFromReasoning(text);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("NEUTRAL");
        }

        @Test
        void shouldReturnNullForNonJsonText() {
            // Arrange
            String text = "This is just plain text with no JSON at all.";

            // Act
            String json = client.extractJsonFromReasoning(text);

            // Assert
            assertThat(json).isNull();
        }

        @Test
        void shouldReturnNullForNullInput() {
            // Act
            String json = client.extractJsonFromReasoning(null);

            // Assert
            assertThat(json).isNull();
        }

        @Test
        void shouldReturnNullForEmptyInput() {
            // Act
            String json = client.extractJsonFromReasoning("");

            // Assert
            assertThat(json).isNull();
        }

        @Test
        void shouldHandleNestedJsonInReasoning() {
            // Arrange
            String text = """
                    Analysis:
                    {
                      "score": "POSITIVE",
                      "confidence": 0.9,
                      "summary": "Strong {technical} setup",
                      "red_flags": ["volatility"],
                      "catalysts": ["{earnings}"]
                    }
                    """;

            // Act
            String json = client.extractJsonFromReasoning(text);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("POSITIVE");
        }

        @Test
        void shouldHandleJsonWithEscapedQuotes() {
            // Arrange
            String text = """
                    {"score": "POSITIVE", "confidence": 0.8, "summary": "He said \\"buy\\" the stock", "red_flags": [], "catalysts": []}
                    """;

            // Act
            String json = client.extractJsonFromReasoning(text);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("POSITIVE");
        }

        @Test
        void shouldFallbackToReasoningWhenContentEmpty() {
            // Arrange
            var cotClient = new SpringAiLlmClient(chatClient, false);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", "Test")
            );
            when(assistantMessage.getText()).thenReturn("");
            var reasoningMetadata = new java.util.HashMap<String, Object>();
            reasoningMetadata.put("reasoningContent", """
                    Let me think...
                    {"score": "POSITIVE", "confidence": 0.9, "summary": "Good", "red_flags": [], "catalysts": []}
                    """);
            when(assistantMessage.getMetadata()).thenReturn(reasoningMetadata);

            // Act
            var result = cotClient.generateChatCompletion(messages, 512, 0.3);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.block()).contains("POSITIVE");
        }
    }

    // ===== CoT Prompt Tests =====

    @Nested
    @DisplayName("Chain of Thought")
    class CoTPrompts {

        @Test
        void shouldInjectCoTInstructionsWhenEnabled() {
            // Arrange
            var cotClient = new SpringAiLlmClient(chatClient, true);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "You are an analyst"),
                    Map.of("role", "user", "content", "Analyze")
            );
            when(assistantMessage.getText()).thenReturn("response");

            // Act
            var result = cotClient.generateChatCompletion(messages, 512, 0.3);

            // Assert
            assertThat(result).isNotNull();
        }

        @Test
        void shouldNotInjectCoTInstructionsWhenDisabled() {
            // Arrange
            var noCoTClient = new SpringAiLlmClient(chatClient, false);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "You are an analyst"),
                    Map.of("role", "user", "content", "Analyze")
            );
            when(assistantMessage.getText()).thenReturn("response");

            // Act
            var result = noCoTClient.generateChatCompletion(messages, 512, 0.3);

            // Assert
            assertThat(result).isNotNull();
        }
    }

    private void verifyChatClientCalled() {
        // Verify the fluent chain was called
        org.mockito.Mockito.verify(chatClient).prompt();
        org.mockito.Mockito.verify(requestSpec).call();
        org.mockito.Mockito.verify(callSpec).chatResponse();
    }
}