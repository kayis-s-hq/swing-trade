package com.swingtrade.llm.service;

import com.swingtrade.llm.SentimentOutput;
import com.swingtrade.llm.SentimentType;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SentimentAnalyzer BeanOutputConverter integration.
 * Validates that structured output parsing works correctly with both
 * BeanOutputConverter (primary) and Jackson fallback (secondary).
 */
class SentimentAnalyzerBeanOutputConverterTest {

    private SentimentAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SentimentAnalyzer(new ObjectMapper());
    }

    // ===== BeanOutputConverter Tests =====

    @Nested
    @DisplayName("BeanOutputConverter parsing")
    class BeanOutputConverterParsing {

        @Test
        void shouldParseValidJsonWithBeanOutputConverter() {
            // Arrange
            String json = """
                    {
                      "score": "POSITIVE",
                      "confidence": 0.85,
                      "summary": "Strong earnings beat and revenue growth",
                      "red_flags": ["valuation concerns"],
                      "catalysts": ["new product launch"]
                    }
                    """;

            // Act
            SentimentOutput result = analyzer.parseResponse(json);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getSentiment()).isEqualTo(SentimentType.POSITIVE);
            assertThat(result.getConfidence()).isEqualTo(0.85);
            assertThat(result.getReasoning()).isEqualTo("Strong earnings beat and revenue growth");
            assertThat(result.getRedFlags()).hasSize(1);
            assertThat(result.getRedFlags()).contains("valuation concerns");
            assertThat(result.getCatalysts()).hasSize(1);
            assertThat(result.getCatalysts()).contains("new product launch");
        }

        @Test
        void shouldParseNegativeSentiment() {
            // Arrange
            String json = """
                    {
                      "score": "NEGATIVE",
                      "confidence": 0.7,
                      "summary": "Earnings miss and guidance cut",
                      "red_flags": ["debt concerns", "management exit"],
                      "catalysts": []
                    }
                    """;

            // Act
            SentimentOutput result = analyzer.parseResponse(json);

            // Assert
            assertThat(result.getSentiment()).isEqualTo(SentimentType.NEGATIVE);
            assertThat(result.getConfidence()).isEqualTo(0.7);
            assertThat(result.getReasoning()).isEqualTo("Earnings miss and guidance cut");
        }

        @Test
        void shouldParseNeutralSentiment() {
            // Arrange
            String json = """
                    {
                      "score": "NEUTRAL",
                      "confidence": 0.5,
                      "summary": "Mixed signals from earnings",
                      "red_flags": [],
                      "catalysts": ["quarterly results next month"]
                    }
                    """;

            // Act
            SentimentOutput result = analyzer.parseResponse(json);

            // Assert
            assertThat(result.getSentiment()).isEqualTo(SentimentType.NEUTRAL);
            assertThat(result.getConfidence()).isEqualTo(0.5);
        }

        @Test
        void shouldFallBackToJacksonWhenBeanOutputConverterFails() {
            // Arrange — JSON with field name mismatch for BeanOutputConverter
            // BeanOutputConverter expects fields matching SentimentOutput class
            // This uses "sentiment" instead of "score" which may cause issues
            String json = """
                    {
                      "sentiment": "POSITIVE",
                      "confidence": 0.9,
                      "summary": "Very positive outlook",
                      "red_flags": [],
                      "catalysts": ["strong demand"]
                    }
                    """;

            // Act
            SentimentOutput result = analyzer.parseResponse(json);

            // Assert — should fall back to Jackson parsing
            assertThat(result).isNotNull();
            assertThat(result.getSentiment()).isEqualTo(SentimentType.POSITIVE);
        }

        @Test
        void shouldFallBackToPlainWhenJsonParsingFails() {
            // Arrange
            String text = "The stock looks positive with strong momentum";

            // Act
            SentimentOutput result = analyzer.parseResponse(text);

            // Assert
            assertThat(result.getSentiment()).isEqualTo(SentimentType.POSITIVE);
            assertThat(result.getConfidence()).isEqualTo(0.4);
        }

        @Test
        void shouldHandleEmptyResponse() {
            // Act
            SentimentOutput result = analyzer.parseResponse("");

            // Assert
            assertThat(result.getSentiment()).isEqualTo(SentimentType.UNKNOWN);
            assertThat(result.getConfidence()).isEqualTo(0.0);
        }

        @Test
        void shouldHandleNullResponse() {
            // Act
            SentimentOutput result = analyzer.parseResponse(null);

            // Assert
            assertThat(result.getSentiment()).isEqualTo(SentimentType.UNKNOWN);
            assertThat(result.getConfidence()).isEqualTo(0.0);
        }

        @Test
        void shouldHandleMalformedJson() {
            // Arrange
            String json = "{ invalid json content }";

            // Act
            SentimentOutput result = analyzer.parseResponse(json);

            // Assert — should fall back to plain text parsing
            assertThat(result).isNotNull();
        }
    }

    // ===== Reasoning Field Extraction Tests =====

    @Nested
    @DisplayName("Reasoning field extraction fallback")
    class ReasoningFieldExtraction {

        @Test
        void shouldExtractJsonWrappedInThinkingTags() {
            // Arrange
            String text = """
                    <thinking>
                    Let me analyze the sentiment...
                    </thinking>
                    {"score": "POSITIVE", "confidence": 0.8, "summary": "Good news", "red_flags": [], "catalysts": []}
                    """;

            // Act
            SentimentOutput result = analyzer.parseResponse(text);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getSentiment()).isEqualTo(SentimentType.POSITIVE);
        }

        @Test
        void shouldExtractJsonFromReasoningWithBraceCounting() {
            // Arrange
            String text = """
                    Analysis:
                    The company shows strong fundamentals.
                    Here is the structured output:
                    {
                      "score": "NEUTRAL",
                      "confidence": 0.6,
                      "summary": "Mixed performance",
                      "red_flags": ["competition"],
                      "catalysts": ["new market entry"]
                    }
                    End of analysis.
                    """;

            // Act
            SentimentOutput result = analyzer.parseResponse(text);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getSentiment()).isEqualTo(SentimentType.NEUTRAL);
            assertThat(result.getReasoning()).isEqualTo("Mixed performance");
        }

        @Test
        void shouldExtractJsonFromMarkdownCodeBlock() {
            // Arrange
            String text = """
                    ```json
                    {"score": "NEGATIVE", "confidence": 0.9, "summary": "SEBI probe", "red_flags": ["regulatory risk"], "catalysts": []}
                    ```
                    """;

            // Act
            SentimentOutput result = analyzer.parseResponse(text);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getSentiment()).isEqualTo(SentimentType.NEGATIVE);
        }
    }

    // ===== Prompt Creation Tests =====

    @Nested
    @DisplayName("Prompt creation")
    class PromptCreationTests {

        @Test
        void shouldCreatePromptWithStockSymbol() {
            // Act
            var messages = analyzer.createSentimentAnalysisPrompt("RELIANCE", "News content");

            // Assert
            assertThat(messages).hasSize(2);
            assertThat(messages.get(1).get("content")).contains("RELIANCE");
        }

        @Test
        void shouldCreatePromptWithNewsContent() {
            // Act
            var messages = analyzer.createSentimentAnalysisPrompt("TCS", "TCS wins contract");

            // Assert
            assertThat(messages.get(1).get("content")).contains("TCS wins contract");
        }

        @Test
        void shouldCreateMultiArticlePrompt() {
            // Arrange
            List<String> headlines = List.of("Headline 1", "Headline 2");

            // Act
            var messages = analyzer.createSentimentAnalysisPrompt("INFY", headlines, "Earnings summary");

            // Assert
            assertThat(messages).hasSize(2);
            assertThat(messages.get(1).get("content")).contains("INFY");
            assertThat(messages.get(1).get("content")).contains("Earnings summary");
        }

        @Test
        void shouldIncludeSentimentGuidelinesInSystemPrompt() {
            // Act
            var messages = analyzer.createSentimentAnalysisPrompt("RELIANCE", "News");

            // Assert
            String systemPrompt = messages.get(0).get("content");
            assertThat(systemPrompt).contains("financial analyst");
            assertThat(systemPrompt).contains("Indian equity");
            assertThat(systemPrompt).contains("POSITIVE");
            assertThat(systemPrompt).contains("NEGATIVE");
        }
    }
}
