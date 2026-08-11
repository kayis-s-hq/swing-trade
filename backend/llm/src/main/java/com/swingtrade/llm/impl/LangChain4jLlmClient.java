package com.swingtrade.llm.impl;

import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.llm.LlmClient;
import com.swingtrade.llm.SentimentOutput;
import com.swingtrade.llm.SentimentType;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of LLM client using LangChain4j integration with local vLLM endpoint.
 * Configurable through application.properties with base URL support.
 */
public class LangChain4jLlmClient implements LlmClient {
    
    private final ChatModel chatModel;
    private static final String DEFAULT_BASE_URL = "http://localhost:8000";
    
    /**
     * Creates a new LangChain4j LLM client with default configuration.
     */
    public LangChain4jLlmClient() {
        this(DEFAULT_BASE_URL);
    }
    
    /**
     * Creates a new LangChain4j LLM client with custom base URL.
     *
     * @param baseUrl The base URL of the LLM endpoint (vLLM, Ollama, etc.)
     */
    public LangChain4jLlmClient(String baseUrl) {
        // Initialize the Ollama model with the provided base URL
        // Ollama supports multiple model types and is compatible with vLLM endpoints
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName("meta-llama/Llama-3.2-3B-Instruct") // Default model, can be configured
                .temperature(0.0) // Deterministic output for consistent results
                .build();
    }
    
    @Override
    public SentimentOutput analyzeSentiment(String inputText) {
        // Create a prompt that forces structured output
        String prompt = """
            Analyze the sentiment of the following financial news text and respond with exactly 
            one JSON object with the fields: sentiment (POSITIVE/NEUTRAL/NEGATIVE), 
            reasoning (brief explanation), and confidence (0.0-1.0).
            
            Text: %s
            """.formatted(inputText);
            
        // Get response from LLM
        String response = chatModel.chat(prompt);
        
        // Parse structured output (simplified approach for demonstration)
        try {
            SentimentType sentiment = parseSentimentFromResponse(response);
            String reasoning = extractReasoning(response);
            Double confidence = extractConfidence(response);
            
            return new SentimentOutput(sentiment, reasoning, confidence);
        } catch (Exception e) {
            // Fallback to neutral sentiment if parsing fails
            return new SentimentOutput(
                SentimentType.NEUTRAL, 
                "Unable to parse sentiment from LLM response: " + e.getMessage(), 
                0.5
            );
        }
    }
    
    @Override
    public List<Signal> processNewsForSignals(List<String> newsArticles) {
        List<Signal> signals = new ArrayList<>();

        for (String article : newsArticles) {
            // Analyze each article for technical signals
            SentimentOutput sentimentResult = analyzeSentiment(article);

            // Convert sentiment to domain signal based on rules
            if (sentimentResult.getSentiment() == SentimentType.POSITIVE) {
                // Generate positive signal with some strength
                Signal signal = new Signal(
                    null, // id — will be assigned by DB
                    "AAPL", // Simplified — in real implementation, extract symbol from article
                    LocalDate.now(),
                    SignalType.BUY,
                    BigDecimal.valueOf(sentimentResult.getConfidence() * 0.8 + 0.2), // Normalize to 0.2-1.0 range
                    sentimentResult.getReasoning(),
                    null, null, null, null, // entryPrice, stopLoss, target, riskReward
                    null, // indicators
                    LocalDate.now(),
                    null, // sentimentScore
                    null // sentimentReasoning
                );
                signals.add(signal);
            } else if (sentimentResult.getSentiment() == SentimentType.NEGATIVE) {
                // Generate negative signal
                Signal signal = new Signal(
                    null,
                    "AAPL",
                    LocalDate.now(),
                    SignalType.SELL,
                    BigDecimal.valueOf(sentimentResult.getConfidence() * 0.8 + 0.2),
                    sentimentResult.getReasoning(),
                    null, null, null, null,
                    null,
                    LocalDate.now(),
                    null, // sentimentScore
                    null // sentimentReasoning
                );
                signals.add(signal);
            }
        }

        return signals;
    }
    
    /**
     * Parses sentiment type from LLM response.
     * 
     * @param response The LLM response string
     * @return The parsed sentiment type
     */
    private SentimentType parseSentimentFromResponse(String response) {
        // Extract sentiment from response using regex
        Pattern pattern = Pattern.compile("(POSITIVE|NEUTRAL|NEGATIVE)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        
        if (matcher.find()) {
            try {
                return SentimentType.valueOf(matcher.group().toUpperCase());
            } catch (IllegalArgumentException e) {
                return SentimentType.NEUTRAL;
            }
        }
        
        return SentimentType.NEUTRAL;
    }
    
    /**
     * Extracts reasoning from LLM response.
     * 
     * @param response The LLM response string
     * @return The extracted reasoning
     */
    private String extractReasoning(String response) {
        // Simple extraction - in production would use more robust parsing
        return response.length() > 100 ? response.substring(0, 100) + "..." : response;
    }
    
    /**
     * Extracts confidence score from LLM response.
     * 
     * @param response The LLM response string
     * @return The extracted confidence score
     */
    private Double extractConfidence(String response) {
        // Look for numeric confidence values
        Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher matcher = pattern.matcher(response);
        
        if (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group());
                return Math.min(1.0, Math.max(0.0, value)); // Clamp between 0 and 1
            } catch (NumberFormatException e) {
                return 0.5;
            }
        }
        
        return 0.5;
    }
}
