package com.swingtrade.llm;

import com.swingtrade.domain.Signal;

import java.util.List;

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
    SentimentOutput analyzeSentiment(String inputText);

    /**
     * Processes news articles for technical trading signals.
     *
     * @param newsArticles List of news articles to process
     * @return Processed results with domain signals
     */
    List<Signal> processNewsForSignals(List<String> newsArticles);
}
