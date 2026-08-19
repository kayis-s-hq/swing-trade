package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.llm.SentimentOutput;
import com.swingtrade.llm.SentimentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper component that formats prompts for sentiment analysis using LLM.
 * Provides structured prompt templates for analyzing stock sentiment from news articles.
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

            CRITICAL: Respond with ONLY a JSON object. No explanation, no reasoning, no other text.
            Do NOT include <thinking> tags, reasoning text, or any prose.
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

                CRITICAL: Respond with ONLY a JSON object. No explanation, no reasoning, no other text.
                Do NOT include <thinking> tags, reasoning text, or any prose.
                Start your response with { and end with }.

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

                CRITICAL: Respond with ONLY a JSON object. No explanation, no reasoning, no other text.
                Do NOT include <thinking> tags, reasoning text, or any prose.
                Start your response with { and end with }.

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
     * Parses LLM JSON response into SentimentOutput using Jackson.
     * Handles: reasoning text wrapping JSON, plain text fallback, and empty responses.
     */
    public SentimentOutput parseResponse(String jsonResponse) {
        try {
            String content = extractJsonFromReasoning(jsonResponse);
            logger.debug("extracted JSON: {} chars, preview: {}", content != null ? content.length() : 0, content != null ? content.substring(0, Math.min(100, content.length())) : "null");
            JsonNode root = objectMapper.readTree(content);

            if (!root.has("score")) throw new IllegalArgumentException("Missing score field");

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
     * Strips reasoning tags, markdown code blocks, then scans for valid JSON.
     * Tries each '{' in the text until one produces valid JSON.
     */
    private String extractJsonFromReasoning(String text) {
        if (text == null || text.isBlank()) return text;

        String cleaned = text;
        // Strip reasoning tags (Sonnet, Claude, etc.)
        cleaned = cleaned.replaceAll("(?s)<thinking>.*?</thinking>\\s*", "");
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>\\s*", "");
        cleaned = cleaned.replaceAll("(?s)<reasoning>.*?</reasoning>\\s*", "");
        // Strip markdown code block markers: ```json and ```
        cleaned = cleaned.replaceAll("(?m)^```(?:json)?$\\s*", "");

        // Try each '{' in the text until one produces valid JSON
        int pos = 0;
        while (pos < cleaned.length()) {
            int firstOpen = findBrace(cleaned, '{', pos);
            if (firstOpen < 0) break;

            for (int i = firstOpen + 1; i < cleaned.length(); i++) {
                if (cleaned.charAt(i) == '}') {
                    String candidate = cleaned.substring(firstOpen, i + 1);
                    if (isValidJson(candidate)) return candidate;
                }
            }
            // This '{' didn't produce valid JSON — try the next one
            pos = firstOpen + 1;
        }

        // Fallback: first '{' and last '}'
        int firstOpen = findBrace(cleaned, '{', 0);
        int lastClose = findBrace(cleaned, '}', 0);
        if (lastClose > firstOpen && lastClose > 0) {
            return cleaned.substring(firstOpen, lastClose + 1);
        }
        return text;
    }

    /**
     * Finds the index of a character that is not inside quotes or nested braces,
     * starting from the given position. Returns -1 if not found.
     */
    private int findBrace(String text, char c, int from) {
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
