package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.llm.SentimentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SentimentAnalyzer testing prompt creation and response parsing.
 *
 * Validates:
 * - Prompt creation includes stock symbol and market context
 * - JSON format with sentiment, confidence, reasoning, keyFactors fields
 * - Response parsing for all sentiment types (POSITIVE, NEUTRAL, NEGATIVE)
 * - Graceful fallback handling for malformed JSON
 * - Defaults to NEUTRAL for empty/null responses
 */
class SentimentAnalyzerTest {

    private SentimentAnalyzer sentimentAnalyzer;

    @BeforeEach
    void setUp() {
        sentimentAnalyzer = new SentimentAnalyzer(new ObjectMapper());
    }

    // ===== Prompt Creation Tests =====

    @Test
    void testCreateSentimentAnalysisPrompt_includesStockSymbol() {
        // Arrange
        String stockSymbol = "RELIANCE";
        String newsContent = "Company reports 20% revenue growth";

        // Act
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, newsContent);

        // Assert
        assertThat(messages).hasSize(2);  // System + User messages
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(1).get("role")).isEqualTo("user");
        assertThat(messages.get(1).get("content")).contains("RELIANCE");
    }

    @Test
    void testCreateSentimentAnalysisPrompt_includesNewsContent() {
        // Arrange
        String stockSymbol = "RELIANCE";
        String newsContent = "Company reports 20% revenue growth";

        // Act
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, newsContent);

        // Assert
        String userMessage = messages.get(1).get("content");
        assertThat(userMessage).contains("20% revenue growth");
    }

    @Test
    void testCreateSentimentAnalysisPrompt_includesMarketContext() {
        // Arrange
        String stockSymbol = "TCS";
        String newsContent = "TCS wins Microsoft contract";

        // Act
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, newsContent);

        // Assert
        String userMessage = messages.get(1).get("content");
        assertThat(userMessage).contains("swing trade");
    }

    @Test
    void testCreateSentimentAnalysisPrompt_requestsJsonFormat() {
        // Arrange
        String stockSymbol = "TCS";
        String newsContent = "TCS wins Microsoft contract";

        // Act
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, newsContent);

        // Assert - verify prompt asks for JSON with required fields
        String userMessage = messages.get(1).get("content");
        assertThat(userMessage).contains("\"score\"");
        assertThat(userMessage).contains("\"confidence\"");
        assertThat(userMessage).contains("\"summary\"");
        assertThat(userMessage).contains("\"red_flags\"");
        assertThat(userMessage).contains("\"catalysts\"");
    }

    @Test
    void testCreateSentimentAnalysisPrompt_specifyesSentimentOptions() {
        // Arrange
        String stockSymbol = "HDFCBANK";
        String newsContent = "Bank reports loan growth";

        // Act
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, newsContent);

        // Assert - should specify sentiment types
        String userMessage = messages.get(1).get("content");
        assertThat(userMessage).contains("POSITIVE");
        assertThat(userMessage).contains("NEUTRAL");
        assertThat(userMessage).contains("NEGATIVE");
    }

    @Test
    void testCreateSentimentAnalysisPrompt_includesSystemInstructions() {
        // Arrange
        String stockSymbol = "HDFCBANK";
        String newsContent = "Bank reports loan growth";

        // Act
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, newsContent);

        // Assert
        String systemMessage = messages.get(0).get("content");
        assertThat(systemMessage).contains("financial analyst");
        assertThat(systemMessage).contains("Indian equity");
        assertThat(systemMessage).contains("POSITIVE");
        assertThat(systemMessage).contains("NEGATIVE");
        assertThat(systemMessage).contains("NEUTRAL");
    }

    @Test
    void testCreateSentimentAnalysisPrompt_definesSentimentGuidelines() {
        // Arrange
        String stockSymbol = "INFY";
        String newsContent = "Infosys Q4 results";

        // Act
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, newsContent);

        // Assert - should define what constitutes each sentiment
        String systemMessage = messages.get(0).get("content");
        assertThat(systemMessage).contains("earnings");
        assertThat(systemMessage).contains("sector");
        assertThat(systemMessage).contains("FII");
    }

    // ===== Response Parsing Tests =====

    @Test
    void testExtractSentimentFromResponse_parsesValidJson_POSITIVE() {
        // Arrange
        String jsonResponse = """
                {
                    "sentiment": "POSITIVE",
                    "confidence": 0.85,
                    "reasoning": "Strong earnings beat and revenue growth",
                    "keyFactors": ["earnings", "revenue growth"]
                }
                """;

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(jsonResponse);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.POSITIVE);
    }

    @Test
    void testExtractSentimentFromResponse_parsesValidJson_NEGATIVE() {
        // Arrange
        String jsonResponse = """
                {
                    "sentiment": "NEGATIVE",
                    "confidence": 0.75,
                    "reasoning": "Earnings miss and guidance cut",
                    "keyFactors": ["earnings miss", "guidance cut"]
                }
                """;

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(jsonResponse);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.NEGATIVE);
    }

    @Test
    void testExtractSentimentFromResponse_parsesValidJson_NEUTRAL() {
        // Arrange
        String jsonResponse = """
                {
                    "sentiment": "NEUTRAL",
                    "confidence": 0.60,
                    "reasoning": "Mixed signals from earnings"
                }
                """;

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(jsonResponse);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.NEUTRAL);
    }

    @Test
    void testExtractSentimentFromResponse_handlesMalformedJson_fallbackToKeyword() {
        // Arrange - non-JSON response with keyword
        String malformedResponse = "The sentiment is POSITIVE - strong quarterly results";

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(malformedResponse);

        // Assert - should fallback to keyword detection
        assertThat(sentiment).isEqualTo(SentimentType.POSITIVE);
    }

    @Test
    void testExtractSentimentFromResponse_malformedJson_detectionNEGATIVE() {
        // Arrange - malformed response with negative keywords
        String malformedResponse = "This is a negative development with declining revenues";

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(malformedResponse);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.NEGATIVE);
    }

    @Test
    void testExtractSentimentFromResponse_malformedJson_defaultsToNeutral() {
        // Arrange - malformed response without clear sentiment keywords
        String malformedResponse = "Mixed performance with some gains and losses";

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(malformedResponse);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.NEUTRAL);
    }

    @Test
    void testExtractSentimentFromResponse_defaultsToNeutralForEmpty() {
        // Arrange
        String emptyResponse = "";

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(emptyResponse);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.NEUTRAL);
    }

    @Test
    void testExtractSentimentFromResponse_defaultsToNeutralForNull() {
        // Arrange
        String nullResponse = null;

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(nullResponse);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.NEUTRAL);
    }

    @Test
    void testExtractSentimentFromResponse_handlesWhitespaceVariations() {
        // Arrange - JSON with different spacing
        String spaceVariations = """
                {
                  "sentiment" : "POSITIVE" ,
                  "confidence": 0.9
                }
                """;

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(spaceVariations);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.POSITIVE);
    }

    @Test
    void testExtractSentimentFromResponse_caseInsensitiveKeywordMatching() {
        // Arrange
        String lowercasePositive = "Positive sentiment with good results";

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(lowercasePositive);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.POSITIVE);
    }

    @Test
    void testExtractSentimentFromResponse_ignoresExtraFields() {
        // Arrange - JSON with extra fields
        String extraFields = """
                {
                    "sentiment": "POSITIVE",
                    "confidence": 0.85,
                    "reasoning": "Good",
                    "keyFactors": ["earnings"],
                    "tradingImplication": "Buy signal",
                    "extraField": "ignored"
                }
                """;

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(extraFields);

        // Assert
        assertThat(sentiment).isEqualTo(SentimentType.POSITIVE);
    }

    @Test
    void testExtractSentimentFromResponse_precedenceWhenConflicting() {
        // Arrange - response with "negative" in reasoning but "POSITIVE" sentiment field
        String conflictingResponse = """
                {
                    "sentiment": "POSITIVE",
                    "confidence": 0.85,
                    "reasoning": "Despite negative outlook, stock shows positive momentum"
                }
                """;

        // Act
        SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(conflictingResponse);

        // Assert - should prefer the explicit sentiment field
        assertThat(sentiment).isEqualTo(SentimentType.POSITIVE);
    }

    // ===== Additional Prompt Tests =====

    // @Test commented out: tests for deleted dead-code methods
    // void testCreateMultiArticleSentimentPrompt_formatsMultipleArticles() {
    //     // Arrange
    //     String stockSymbol = "WIPRO";
    //     List<String> articles = List.of(
    //             "Article 1: Wipro announces new contracts",
    //             "Article 2: Wipro reports Q4 results"
    //     );
    //     String prompt = sentimentAnalyzer.createMultiArticleSentimentPrompt(stockSymbol, articles);
    //     assertThat(prompt).contains("WIPRO");
    //     assertThat(prompt).contains("Article 1");
    //     assertThat(prompt).contains("Article 2");
    // }
    //
    // @Test
    // void testCreateSimpleClassificationPrompt_requestsOnlySentiment() {
    //     String stockSymbol = "MARUTI";
    //     String newsText = "Maruti reports strong auto sales";
    //     String prompt = sentimentAnalyzer.createSimpleClassificationPrompt(stockSymbol, newsText);
    //     assertThat(prompt).contains("MARUTI");
    //     assertThat(prompt).contains("sentiment");
    //     assertThat(prompt).contains("POSITIVE");
    //     assertThat(prompt).contains("NEGATIVE");
    // }

    @Test
    void testValidateSentimentResponse_verifyRequiredFields() {
        // Arrange
        String validResponse = """
                {
                    "score": "POSITIVE",
                    "confidence": 0.85,
                    "summary": "Good"
                }
                """;
        List<String> requiredFields = List.of("score", "confidence", "summary");

        // Act
        boolean isValid = sentimentAnalyzer.validateSentimentResponse(validResponse, requiredFields);

        // Assert
        assertThat(isValid).isTrue();
    }

    @Test
    void testValidateSentimentResponse_detectsMissingFields() {
        // Arrange
        String invalidResponse = """
                {
                    "score": "POSITIVE"
                }
                """;
        List<String> requiredFields = List.of("score", "confidence", "summary");

        // Act
        boolean isValid = sentimentAnalyzer.validateSentimentResponse(invalidResponse, requiredFields);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    void testValidateSentimentResponse_handlesNullResponse() {
        // Arrange
        String nullResponse = null;
        List<String> requiredFields = List.of("sentiment");

        // Act
        boolean isValid = sentimentAnalyzer.validateSentimentResponse(nullResponse, requiredFields);

        // Assert
        assertThat(isValid).isFalse();
    }
}
