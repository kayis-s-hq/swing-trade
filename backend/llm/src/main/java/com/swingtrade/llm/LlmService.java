package com.swingtrade.llm;

import java.util.List;

/**
 * Service interface for LLM-based news ingestion and sentiment analysis.
 * Handles fetching news from various sources and analyzing them for trading signals.
 */
public interface LlmService {
    
    /**
     * Fetches news from various sources (NSE announcements, Google News RSS).
     * 
     * @return List of news articles
     */
    List<String> fetchNews();
    
    /**
     * Analyzes fetched news for technical buy signals.
     * 
     * @param newsArticles List of news articles to analyze
     * @return List of technical signals derived from news analysis
     */
    List<TechnicalSignal> analyzeNewsForBuySignals(List<String> newsArticles);
    
    /**
     * Processes news and generates trading signals.
     * 
     * @param newsArticles List of news articles to process
     * @return List of processed trading signals
     */
    List<TechnicalSignal> processNewsForTradingSignals(List<String> newsArticles);
}
