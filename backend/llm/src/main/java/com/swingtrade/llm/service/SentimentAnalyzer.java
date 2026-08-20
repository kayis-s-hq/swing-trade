package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.llm.SentimentOutput;
import com.swingtrade.llm.SentimentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper component that formats prompts for sentiment analysis using LLM.
 * Provides structured prompt templates for analyzing stock sentiment from news articles.
 *
 * Uses Spring AI BeanOutputConverter for structured output parsing
 * (falls back to manual JSON parsing when the LLM returns non-JSON).
 */
@Component
public class SentimentAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(SentimentAnalyzer.class);

    private final ObjectMapper objectMapper;

    public SentimentAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the system prompt template for hashing/A-B tracking.
     */
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * System prompt that establishes the role and task for sentiment analysis.
     */
    public static final String SYSTEM_PROMPT = """
            You are a financial analyst specialising in Indian equity markets.
            Analyse the following for a swing trade entry decision on {symbol}.
            Consider: earnings momentum, regulatory news, management changes,
            sector tailwinds, FII/DII activity, promoter actions.

            Respond with a JSON object containing sentiment analysis fields.
            Start your response with { and end with }.

            {
              "score": "POSITIVE|NEUTRAL|NEGATIVE",
              "confidence": 0.0-1.0,
              "summary": "2 sentence max reasoning",
              "red_flags": ["list any specific risks"],
              "catalysts": ["list any upcoming catalysts"]
            }

            Examples:
            POSITIVE: Strong quarterly results, FII buying, sector tailwind
            NEUTRAL: Mixed results, no major news
            NEGATIVE: Promoter pledge, SEBI action, earnings miss
            """;

    /**
     * Creates a chat message list for sentiment analysis.
     */
    public List<Map<String, String>> createSentimentAnalysisPrompt(String stockSymbol, String newsContent) {
        String userMessage = """
                Analyse the following for a swing trade entry decision on %s.

                Recent news headlines (last 7 days):
                %s

                Task: Determine if news sentiment supports a 1-4 week swing trade entry.

                Respond with ONLY a JSON object. Start with { and end with }.

                {
                  "score": "POSITIVE|NEUTRAL|NEGATIVE",
                  "confidence": 0.0-1.0,
                  "summary": "2 sentence max reasoning",
                  "red_flags": ["list any specific risks"],
                  "catalysts": ["list any upcoming catalysts"]
                }
                """;

        String formattedMessage = String.format(userMessage, stockSymbol, newsContent);

        Map<String, String> systemMessage = Map.of("role", "system", "content", SYSTEM_PROMPT);
        Map<String, String> userMessageObj = Map.of("role", "user", "content", formattedMessage);

        return List.of(systemMessage, userMessageObj);
    }

    /**
     * Creates a prompt for multi-article sentiment analysis with earnings data.
     */
    public List<Map<String, String>> createSentimentAnalysisPrompt(
            String stockSymbol, List<String> headlines, String earningsSummary) {
        String userMessage = """
                Analyse the following for a swing trade entry decision on %s.

                Recent news headlines (last 7 days):
                %s

                Latest earnings summary:
                %s

                Task: Determine if news sentiment supports a 1-4 week swing trade entry.

                Respond with ONLY a JSON object. Start with { and end with }.

                {
                  "score": "POSITIVE|NEUTRAL|NEGATIVE",
                  "confidence": 0.0-1.0,
                  "summary": "2 sentence max reasoning",
                  "red_flags": ["list any specific risks"],
                  "catalysts": ["list any upcoming catalysts"]
                }
                """;

        String earningsText = earningsSummary != null && !earningsSummary.isBlank()
                ? earningsSummary
                : "No earnings data available";

        String formattedMessage = String.format(userMessage, stockSymbol,
                String.join("\n", headlines), earningsText);

        Map<String, String> systemMessage = Map.of("role", "system", "content", SYSTEM_PROMPT);
        Map<String, String> userMessageObj = Map.of("role", "user", "content", formattedMessage);

        return List.of(systemMessage, userMessageObj);
    }

    /**
     * Parses LLM response into SentimentOutput using Spring AI BeanOutputConverter.
     * Falls back to manual JSON parsing when the LLM returns non-JSON.
     */
    public SentimentOutput parseResponse(String jsonResponse) {
        try {
            BeanOutputConverter<SentimentOutput> converter = new BeanOutputConverter<>(SentimentOutput.class);
            SentimentOutput result = converter.convert(jsonResponse);
            logger.debug("BeanOutputConverter parsed: {} (confidence: {})",
                    result.getSentiment(), result.getConfidence());
            return result;
        } catch (Exception e) {
            logger.debug("BeanOutputConverter failed, falling back to Jackson parsing: {}", e.getMessage());
            return parseWithJackson(jsonResponse);
        }
    }

    /**
     * Falls back to Jackson-based JSON parsing when BeanOutputConverter fails.
     */
    private SentimentOutput parseWithJackson(String jsonResponse) {
        try {
            String content = extractJsonFromReasoning(jsonResponse);
            logger.debug("extracted JSON: {} chars, preview: {}",
                    content != null ? content.length() : 0,
                    content != null ? content.substring(0, Math.min(100, content.length())) : "null");
            JsonNode root = objectMapper.readTree(content);

            if (!root.has("score")) {
                throw new IllegalArgumentException("Missing score field");
            }

            String score = root.get("score").asText();
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : 0.5;
            String summary = root.has("summary") ? root.get("summary").asText() : "Analysis incomplete";

            List<String> redFlags = new ArrayList<>();
            if (root.has("red_flags") && root.get("red_flags").isArray()) {
                root.get("red_flags").forEach(node -> redFlags.add(node.asText()));
            }

            List<String> catalysts = new ArrayList<>();
            if (root.has("catalysts") && root.get("catalysts").isArray()) {
                root.get("catalysts").forEach(node -> catalysts.add(node.asText()));
            }

            return new SentimentOutput(
                    SentimentType.valueOf(score.toUpperCase()),
                    summary,
                    confidence,
                    redFlags,
                    catalysts
            );
        } catch (Exception e) {
            logger.debug("JSON parse failed, trying plain text: {}", e.getMessage());
            return parsePlainText(jsonResponse);
        }
    }

    /**
     * Falls back to plain text parsing when the LLM returns no JSON at all.
     * Extracts sentiment from keywords in the reasoning text.
     */
    private SentimentOutput parsePlainText(String text) {
        if (text == null || text.isBlank()) {
            return new SentimentOutput(SentimentType.NEUTRAL, "Empty response", 0.1, List.of(), List.of());
        }

        String lower = text.toLowerCase();
        SentimentType sentiment;
        double confidence;
        String summary;

        if (lower.contains("positive") && !lower.contains("negative")) {
            sentiment = SentimentType.POSITIVE;
            confidence = 0.4;
        } else if (lower.contains("negative") && !lower.contains("positive")) {
            sentiment = SentimentType.NEGATIVE;
            confidence = 0.4;
        } else {
            sentiment = SentimentType.NEUTRAL;
            confidence = 0.2;
        }

        // Truncate to first meaningful sentence as summary
        summary = text.length() > 150 ? text.substring(0, 150).replaceAll("\\.$", "") : text.trim();

        logger.warn("Parsed plain text response: {} (confidence: {})", sentiment, confidence);
        return new SentimentOutput(sentiment, summary, confidence, List.of(), List.of());
    }

    /**
     * Extracts JSON from reasoning text that wraps it (common with reasoning models).
     * Strips reasoning tags and markdown code blocks, then uses brace-counting
     * to find the matching closing brace of the JSON object.
     */
    String extractJsonFromReasoning(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text;
        cleaned = cleaned.replaceAll("(?s)<thinking>.*?</thinking>\\s*", "");
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>\\s*", "");
        cleaned = cleaned.replaceAll("(?s)<reasoning>.*?</reasoning>\\s*", "");
        cleaned = cleaned.replaceAll("(?m)^```(?:json)?$\\s*", "");

        int open = findFirstBrace(cleaned, '{', 0);
        if (open < 0) {
            return text;
        }

        int balance = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = open; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (ch == '{') {
                    balance++;
                } else if (ch == '}') {
                    balance--;
                    if (balance == 0) {
                        String candidate = cleaned.substring(open, i + 1);
                        if (isValidJson(candidate)) {
                            return candidate;
                        }
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the first occurrence of a character outside of strings and escaped characters.
     */
    private int findFirstBrace(String text, char c, int from) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = from; i < text.length(); i++) {
            if (escaped) {
                escaped = false;
                continue;
            }
            char ch = text.charAt(i);
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (!inString && ch == c) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if a string is valid JSON (minimal check: parseable by Jackson).
     */
    private boolean isValidJson(String text) {
        try {
            objectMapper.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates the LLM response by checking for required JSON fields.
     *
     * @param jsonResponse the JSON response to validate
     * @param requiredFields list of required field names
     * @return true if all required fields are present
     */
    public boolean validateSentimentResponse(String jsonResponse, List<String> requiredFields) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            logger.warn("Empty JSON response received");
            return false;
        }

        for (String field : requiredFields) {
            if (jsonResponse.contains("\"" + field + "\"")) {
                logger.trace("Required field '{}' present in response", field);
            } else {
                logger.warn("Required field '{}' missing from response", field);
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts sentiment from LLM response.
     *
     * @param jsonResponse the JSON response from LLM
     * @return the sentiment type extracted from response
     */
    public SentimentType extractSentimentFromResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            logger.warn("Cannot extract sentiment from empty response");
            return SentimentType.NEUTRAL;
        }

        // Look for sentiment field
        if (jsonResponse.contains("\"sentiment\"")) {
            if (jsonResponse.contains("\"sentiment\": \"POSITIVE\"") ||
                jsonResponse.contains("\"sentiment\":\"POSITIVE\"")) {
                return SentimentType.POSITIVE;
            } else if (jsonResponse.contains("\"sentiment\": \"NEGATIVE\"") ||
                      jsonResponse.contains("\"sentiment\":\"NEGATIVE\"")) {
                return SentimentType.NEGATIVE;
            } else if (jsonResponse.contains("\"sentiment\": \"NEUTRAL\"") ||
                      jsonResponse.contains("\"sentiment\":\"NEUTRAL\"")) {
                return SentimentType.NEUTRAL;
            }
        }

        // Fallback: look for keywords in reasoning
        String lowerResponse = jsonResponse.toLowerCase();
        if (lowerResponse.contains("positive") &&
            !lowerResponse.contains("negative")) {
            return SentimentType.POSITIVE;
        } else if (lowerResponse.contains("negative") &&
                  !lowerResponse.contains("positive")) {
            return SentimentType.NEGATIVE;
        }

        return SentimentType.NEUTRAL;
    }
}