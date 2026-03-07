package com.swingtrade.llm;

import java.util.List;
import java.util.Map;

/**
 * Interface for LLM client that connects to local vLLM endpoint.
 * Provides methods for interacting with language models for sentiment analysis.
 */
public interface LlmClient {
    
    /**
     * Analyzes text and returns structured sentiment output.
     * 
     * @param inputText The text to analyze for sentiment
     * @return Structured sentiment analysis result
     */
    SentimentAnalysisResult analyzeSentiment(String inputText);
    
    /**
     * Processes news articles for technical trading signals.
     * 
     * @param newsArticles List of news articles to process
     * @return Processed results with technical signals
     */
    List<TechnicalSignal> processNewsForSignals(List<String> newsArticles);
}
