package com.swingtrade.llm.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.SynthesisResult;
import com.swingtrade.llm.SynthesisOutput;
import com.swingtrade.llm.config.SynthesisPromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SynthesisService {

    private static final Logger logger = LoggerFactory.getLogger(SynthesisService.class);
    private static final int MAX_TOKENS = 1024;
    private static final double TEMPERATURE = 0.2;
    private static final long TIMEOUT_SECONDS = 600;

    private final LlmClientProvider clientProvider;
    private final SynthesisPromptLoader promptLoader;

    public SynthesisService(LlmClientProvider clientProvider, SynthesisPromptLoader promptLoader) {
        this.clientProvider = clientProvider;
        this.promptLoader = promptLoader;
    }

    public SynthesisResult synthesize(CompositeAnalysis composite) {
        String symbol = composite.symbol();
        logger.info("Generating LLM synthesis for {}", symbol);

        String userPrompt = buildPrompt(composite);

        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", promptLoader.getSystemPrompt()),
            Map.of("role", "user", "content", userPrompt)
        );

        try {
            String llmResponse = clientProvider.getClient().generateChatCompletion(messages, MAX_TOKENS, TEMPERATURE)
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
        try {
            BeanOutputConverter<SynthesisOutput> converter = new BeanOutputConverter<>(SynthesisOutput.class);
            SynthesisOutput output = converter.convert(response);

            return new SynthesisResult(
                output.getNarrative(),
                output.getRecommendation(),
                output.getConfidence() != null ? output.getConfidence() : 0.0,
                output.getKeyDrivers() != null ? output.getKeyDrivers() : List.of(),
                output.getBullishFactors() != null ? output.getBullishFactors() : List.of(),
                output.getBearishFactors() != null ? output.getBearishFactors() : List.of(),
                true
            );
        } catch (Exception e) {
            logger.debug("BeanOutputConverter failed, falling back to Jackson parsing: {}", e.getMessage());
            return parseWithFallback(response, composite);
        }
    }

    /**
     * Fallback: manual JSON parsing with brace-counting extraction.
     */
    private SynthesisResult parseWithFallback(String response, CompositeAnalysis composite) {
        String json = extractJson(response);
        if (json == null) {
            logger.warn("No JSON found in LLM response for synthesis");
            return fallbackSynthesis(composite);
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            LlmResponseDTO dto = mapper.readValue(json, LlmResponseDTO.class);
            return new SynthesisResult(
                dto.getNarrative(), dto.getRecommendation(), dto.getConfidence(),
                dto.getKeyDrivers(), dto.getBullishFactors(), dto.getBearishFactors(), true
            );
        } catch (Exception e) {
            logger.warn("Failed to parse synthesis JSON: {}, fallback", e.getMessage());
            return fallbackSynthesis(composite);
        }
    }

    private String extractJson(String response) {
        int open = findFirstBrace(response, '{', 0);
        if (open < 0) {
            return null;
        }

        int balance = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = open; i < response.length(); i++) {
            char ch = response.charAt(i);
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
                        return response.substring(open, i + 1);
                    }
                }
            }
        }
        return null;
    }

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

    private static class LlmResponseDTO {
        String narrative;
        String recommendation;
        double confidence;
        List<String> keyDrivers;
        List<String> bullishFactors;
        List<String> bearishFactors;

        public String getNarrative() { return narrative; }
        public String getRecommendation() { return recommendation; }
        public double getConfidence() { return confidence; }
        public List<String> getKeyDrivers() { return keyDrivers; }
        public List<String> getBullishFactors() { return bullishFactors; }
        public List<String> getBearishFactors() { return bearishFactors; }
    }

    private SynthesisResult fallbackSynthesis(CompositeAnalysis c) {
        return new SynthesisResult(
            null, null, 0.0, List.of(), List.of(), List.of(), false
        );
    }
}