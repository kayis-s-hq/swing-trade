package com.swingtrade.llm.service;

import com.swingtrade.llm.SentimentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Helper component that formats prompts for sentiment analysis using LLM.
 * Provides structured prompt templates for analyzing stock sentiment from news articles.
 */
@Component
public class SentimentAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(SentimentAnalyzer.class);

    /**
     * System prompt that establishes the role and task for sentiment analysis.
     */
    private static final String SYSTEM_PROMPT = """
            You are an expert financial analyst specializing in stock market sentiment analysis.
            Your task is to analyze news articles and corporate announcements about Indian stocks (NSE/BSE)
            and determine the overall sentiment: POSITIVE, NEUTRAL, or NEGATIVE.

            Guidelines:
            - POSITIVE: Strong earnings, revenue growth, new contracts, analyst upgrades, positive market outlook,
              successful product launches, expansion plans, favorable regulatory changes
            - NEGATIVE: Poor earnings, revenue decline, losses, analyst downgrades, legal issues, management changes,
              unfavorable regulatory changes, market headwinds
            - NEUTRAL: Mixed signals, routine announcements, no material impact, stable performance

            Provide a clear classification with supporting reasoning.
            """;

    /**
     * Creates a chat message list for sentiment analysis.
     *
     * @param stockSymbol the stock symbol being analyzed
     * @param newsContent the news content to analyze
     * @return list of chat messages
     */
    public List<Map<String, String>> createSentimentAnalysisPrompt(String stockSymbol, String newsContent) {
        String userMessage = """
                Analyze the sentiment for the following stock news:

                Stock Symbol: %s
                Market: NSE/BSE (Indian Equities)

                News Content:
                %s

                Please provide your analysis in the following JSON format:
                {
                    "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
                    "confidence": 0.0-1.0,
                    "reasoning": "Brief explanation of the sentiment classification (max 200 words)",
                    "keyFactors": ["factor1", "factor2"],
                    "tradingImplication": "Brief note on how this sentiment might affect trading decisions"
                }
                """;

        String formattedMessage = String.format(userMessage, stockSymbol, newsContent);

        Map<String, String> systemMessage = Map.of("role", "system", "content", SYSTEM_PROMPT);
        Map<String, String> userMessageObj = Map.of("role", "user", "content", formattedMessage);

        return List.of(systemMessage, userMessageObj);
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
    private int countKeywords(List<String> articles, List<String> keywords) {
        int count = 0;
        for (String article : articles) {
            String lowerArticle = article.toLowerCase();
            for (String keyword : keywords) {
                if (lowerArticle.contains(keyword.toLowerCase())) {
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
