package com.swingtrade.llm;

import com.swingtrade.domain.Signal;

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
     * @return List of signals derived from news analysis
     */
    List<Signal> analyzeNewsForBuySignals(List<String> newsArticles);

    /**
     * Processes news and generates trading signals.
     *
     * @param newsArticles List of news articles to process
     * @return List of processed trading signals
     */
    List<Signal> processNewsForTradingSignals(List<String> newsArticles);
}
