package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.llm.SentimentOutput;
import com.swingtrade.llm.SentimentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
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

                Respond in this exact JSON format only, no other text:
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
                    SentimentType.valueOf(score.toUpperCase(Locale.ROOT)),
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

        String lower = text.toLowerCase(Locale.ROOT);
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
     * Creates a prompt for analyzing multiple news articles about a stock.
     *
     * @param stockSymbol the stock symbol being analyzed
     * @param articles list of news article texts
     * @return formatted prompt message
     */
    public String createMultiArticleSentimentPrompt(String stockSymbol, List<String> articles) {
        StringBuilder articleBuilder = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            articleBuilder.append("Article ").append(i + 1).append(":").append(System.lineSeparator());
            articleBuilder.append(articles.get(i)).append(System.lineSeparator());
            articleBuilder.append("---").append(System.lineSeparator());
        }

        String userMessage = """
                Analyze the overall sentiment by considering the following news articles about stock %s:

                %s

                Please provide a comprehensive sentiment analysis in JSON format:
                {
                    "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
                    "confidence": 0.0-1.0,
                    "reasoning": "Analysis considering all articles (max 250 words)",
                    "keyFactors": ["factor1", "factor2"],
                    "articleCount": %d,
                    "positiveArticles": %d,
                    "negativeArticles": %d,
                    "tradingImplication": "How this combined sentiment affects trading decisions"
                }
                """;

        // Count positive and negative keywords for preliminary analysis
        int positiveCount = countKeywords(articles, List.of(
            "positive", "bullish", "upgrade", "growth", "surge", "strong", "gain",
            "profit", "beat", "better", "success", "opportunity", "expansion"
        ));
        int negativeCount = countKeywords(articles, List.of(
            "negative", "bearish", "downgrade", "decline", "drop", "weak", "loss",
            "miss", "worse", "risk", "concern", "challenge", "headwind"
        ));

        return String.format(userMessage, stockSymbol, articleBuilder.toString(),
                            articles.size(), positiveCount, negativeCount);
    }

    /**
     * Creates a prompt for comparing sentiment with technical indicators.
     *
     * @param stockSymbol the stock symbol
     * @param sentimentResult the sentiment analysis result
     * @param technicalSummary summary of technical indicators
     * @return combined analysis prompt
     */
    public String createCombinedAnalysisPrompt(
            String stockSymbol,
            String sentimentResult,
            String technicalSummary) {

        String userMessage = """
                Combine sentiment and technical analysis for stock %s.

                SENTIMENT ANALYSIS:
                %s

                TECHNICAL ANALYSIS SUMMARY:
                %s

                Based on both analyses, determine the overall trading signal:
                - If sentiment and technicals align (both positive/negative), confidence should be higher
                - If they conflict, note the disagreement and provide a weighted recommendation

                Provide your combined analysis in JSON format:
                {
                    "overallSignal": "BUY|SELL|HOLD",
                    "sentimentScore": "POSITIVE|NEUTRAL|NEGATIVE",
                    "technicalScore": "BULLISH|BEARISH|NEUTRAL",
                    "alignment": "ALIGNED|MIXED|CONFLICTING",
                    "confidence": 0.0-1.0,
                    "reasoning": "Combined analysis reasoning (max 300 words)",
                    "entryPrice": "Recommended entry zone if applicable",
                    "stopLoss": "Recommended stop loss level if applicable",
                    "target": "Recommended target if applicable",
                    "riskReward": "Calculated risk-reward ratio if applicable"
                }
                """;

        return String.format(userMessage, stockSymbol, sentimentResult, technicalSummary);
    }

    /**
     * Creates a prompt for generating a trading recommendation based on sentiment.
     *
     * @param stockSymbol the stock symbol
     * @param sentimentType the analyzed sentiment
     * @param reasoning the sentiment reasoning
     * @param currentPrice the current stock price
     * @return trading recommendation prompt
     */
    public String createTradingRecommendationPrompt(
            String stockSymbol,
            SentimentType sentimentType,
            String reasoning,
            Double currentPrice) {

        String sentimentDescription = switch (sentimentType) {
            case POSITIVE -> "Positive sentiment indicates potential upward price movement. " +
                            "This suggests buyers are more active than sellers.";
            case NEGATIVE -> "Negative sentiment indicates potential downward price movement. " +
                            "This suggests sellers are more active than buyers.";
            case NEUTRAL -> "Neutral sentiment indicates balanced market conditions. " +
                           "No strong directional bias at this time.";
        };

        String userMessage = """
                Based on the sentiment analysis, provide a concrete trading recommendation for %s.

                SENTIMENT ANALYSIS RESULTS:
                - Sentiment: %s
                - Confidence: N/A
                - Reasoning: %s
                - Current Price: Rs. %s

                %s

                Provide trading recommendation in JSON format:
                {
                    "signal": "BUY|SELL|HOLD",
                    "signalStrength": "STRONG|MODERATE|WEAK",
                    "timeHorizon": "SHORT_TERM|MEDIUM_TERM|LONG_TERM",
                    "confidence": 0.0-1.0,
                    "entryStrategy": "EXACT_PRICE|ZONE|LIMITED_ENTRY",
                    "entryPrice": "Recommended entry price",
                    "entryZone": {
                        "low": "Lower bound of entry zone",
                        "high": "Upper bound of entry zone"
                    },
                    "stopLoss": "Price level for stop loss",
                    "target1": "First target price",
                    "target2": "Second target price",
                    "target3": "Third target price (optional)",
                    "riskRewardRatio": "Calculated risk-reward ratio",
                    "positionSizing": "Recommended position size as percentage of capital",
                    "reasoning": "Detailed reasoning for this recommendation (max 300 words)",
                    "riskFactors": ["factor1", "factor2"],
                    "catalysts": ["potential positive events", "potential negative events"]
                }
                """;

        return String.format(userMessage, stockSymbol, sentimentType, reasoning,
                            currentPrice, sentimentDescription);
    }

    /**
     * Creates a simple classification prompt for basic sentiment tagging.
     *
     * @param stockSymbol the stock symbol
     * @param newsText the news text to classify
     * @return simple classification prompt
     */
    public String createSimpleClassificationPrompt(String stockSymbol, String newsText) {
        String userMessage = """
                Classify the sentiment of this news about stock %s:

                %s

                Respond with ONLY the sentiment classification: POSITIVE, NEUTRAL, or NEGATIVE.
                """;

        return String.format(userMessage, stockSymbol, newsText);
    }

    /**
     * Counts occurrences of keywords in a list of articles.
     *
     * @param articles list of article texts
     * @param keywords list of keywords to count
     * @return total count of keyword matches
     */
    /**
     * Extracts JSON from reasoning text that wraps it (common with reasoning models).
     * Scans for the first '{', then tries to find the matching '}' that produces valid JSON.
     * Falls back to first/last brace pair, then returns raw text.
     */
    private String extractJsonFromReasoning(String text) {
        if (text == null || text.isBlank()) return text;

        // Try: find first '{', then scan for valid JSON end
        int firstOpen = findBrace(text, '{');
        if (firstOpen < 0) return text;

        for (int i = firstOpen + 1; i < text.length(); i++) {
            if (text.charAt(i) == '}') {
                String candidate = text.substring(firstOpen, i + 1);
                if (isValidJson(candidate)) return candidate;
            }
        }

        // Fallback: first '{' and last '}'
        int lastClose = findBrace(text, '}');
        if (lastClose > firstOpen) {
            return text.substring(firstOpen, lastClose + 1);
        }
        return text;
    }

    /**
     * Finds the index of a character that is not inside quotes or nested braces.
     * Returns -1 if not found.
     */
    private int findBrace(String text, char c) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
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

    private int countKeywords(List<String> articles, List<String> keywords) {
        int count = 0;
        for (String article : articles) {
            String lowerArticle = article.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lowerArticle.contains(keyword.toLowerCase(Locale.ROOT))) {
                    count++;
                }
            }
        }
        return count;
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
        String lowerResponse = jsonResponse.toLowerCase(Locale.ROOT);
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
