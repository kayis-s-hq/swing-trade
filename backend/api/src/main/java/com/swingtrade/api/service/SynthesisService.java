package com.swingtrade.api.service;

import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.domain.SynthesisResult;
import com.swingtrade.llm.client.VLLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SynthesisService {

    private static final Logger logger = LoggerFactory.getLogger(SynthesisService.class);
    private static final int MAX_TOKENS = 1024;
    private static final double TEMPERATURE = 0.2;
    private static final long TIMEOUT_SECONDS = 60;

    private final VLLMClient vllmClient;
    private final String modelName;

    public SynthesisService(VLLMClient vllmClient,
                            @Value("${llm.vllm.model-name:gemma-3-27b-it}") String modelName) {
        this.vllmClient = vllmClient;
        this.modelName = modelName;
    }

    public SynthesisResult synthesize(CompositeAnalysis composite) {
        String symbol = composite.symbol();
        logger.info("Generating LLM synthesis for {} (model: {})", symbol, modelName);

        String systemPrompt = """
            You are a senior equity analyst specializing in Indian equity markets.
            Given the results of 9 analysis stages for a stock, produce a final investment recommendation.
            Be concise, data-driven, and specific. Reference actual numbers from the analysis.
            Return ONLY a valid JSON object with this exact structure:
            {
              "narrative": "2-3 paragraph summary of the overall outlook",
              "recommendation": "BUY or SELL or HOLD",
              "confidence": 0.0 to 1.0,
              "keyDrivers": ["top 3 factors driving the recommendation"],
              "bullishFactors": ["specific bullish points with data"],
              "bearishFactors": ["specific bearish points with data"]
            }
        """;

        String userPrompt = buildPrompt(composite);

        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        );

        try {
            String llmResponse = vllmClient.generateChatCompletion(messages, MAX_TOKENS, TEMPERATURE)
                .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (llmResponse == null || llmResponse.isBlank()) {
                logger.warn("Empty LLM response for synthesis: {}", symbol);
                return fallbackSynthesis(composite);
            }

            return parseResponse(llmResponse, composite);
        } catch (Exception e) {
            logger.warn("LLM synthesis failed for {}: {}, using fallback", symbol, e.getMessage());
            return fallbackSynthesis(composite);
        }
    }

    private String buildPrompt(CompositeAnalysis c) {
        return String.format("""
            Stock: %s | Analysis Date: %s

            === NEWS SENTIMENT ===
            Score: %d/100 | Articles analyzed: %d
            Summary: %s
            Catalysts: %s
            Red Flags: %s

            === TECHNICAL ANALYSIS ===
            Signal: %s | Score: %d/100 | Confidence: %.0f%%
            Indicators: %s

            === FUNDAMENTALS ===
            Score: %d/100 | Signal: %s
            Factors: %s

            === BACKTEST ===
            Total Trades: %d | Win Rate: %.1f%% | Profit Factor: %.2f
            Max Drawdown: %.1f%% | Total Return: %.1f%% | Expectancy: %.1f%%

            === COMPOSITE ===
            Score: %d/100 | Signal: %s | Confidence: %.0f%%
            Reasoning: %s
        """,
            c.symbol(), c.date(),
            c.news().score(), c.news().articleCount(),
            c.news().summary(), c.news().catalysts(), c.news().redFlags(),
            c.technical().signal(), c.technical().score(), c.technical().confidence() * 100, c.technical().indicators(),
            c.fundamentals().score(),
            c.fundamentals().score() > 0 ? "BULLISH" : c.fundamentals().score() < 0 ? "BEARISH" : "NEUTRAL",
            c.fundamentals().factors(),
            c.backtest().totalTrades(), c.backtest().winRate(), c.backtest().profitFactor(),
            c.backtest().maxDrawdown(), c.backtest().totalReturn(), c.backtest().expectancy(),
            c.compositeScore(), c.compositeSignal(), c.compositeConfidence().doubleValue() * 100,
            c.reasoning()
        );
    }

    private SynthesisResult parseResponse(String response, CompositeAnalysis composite) {
        // Extract JSON from response — handle reasoning models that wrap JSON in text
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start == -1 || end == -1) {
            logger.warn("No JSON found in LLM response for synthesis");
            return fallbackSynthesis(composite);
        }

        String json = response.substring(start, end + 1);

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, SynthesisResult.class);
        } catch (Exception e) {
            logger.warn("Failed to parse synthesis JSON: {}, fallback", e.getMessage());
            return fallbackSynthesis(composite);
        }
    }

    private SynthesisResult fallbackSynthesis(CompositeAnalysis c) {
        String narrative = String.format(
            "Based on composite analysis of %s: news sentiment score %d, technical signal %s (score %d), " +
            "fundamentals score %d, backtest win rate %.1f%% over %d trades. " +
            "Composite verdict: %s (score %d, confidence %.0f%%).",
            c.symbol(), c.news().score(), c.technical().signal(), c.technical().score(),
            c.fundamentals().score(), c.backtest().winRate(), c.backtest().totalTrades(),
            c.compositeSignal(), c.compositeScore(), c.compositeConfidence().doubleValue() * 100
        );

        return new SynthesisResult(
            narrative,
            c.compositeSignal(),
            c.compositeConfidence().doubleValue() * 0.7,
            List.of("Composite analysis of " + c.symbol()),
            List.of(),
            List.of()
        );
    }
}