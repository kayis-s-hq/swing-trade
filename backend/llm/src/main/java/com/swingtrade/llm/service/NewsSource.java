package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import java.util.List;

/**
 * Interface for fetching news articles from a specific source.
 * Each implementation handles one news source (Moneycontrol, ET, Google News, etc.).
 */
public interface NewsSource {

    /**
     * Returns the source identifier (e.g., "moneycontrol", "economic_times").
     */
    String type();

    /**
     * Fetches news articles for a given stock symbol.
     *
     * @param symbol stock symbol (e.g., "RELIANCE", "TCS")
     * @return list of news articles (may be empty if source fails or returns no results)
     */
    List<NewsArticle> fetch(String symbol);

    /**
     * Fetches structured corporate filings for a given symbol.
     * Default implementation returns empty list — only NSE/BSE override this.
     *
     * @param symbol stock symbol
     * @return list of structured filings (empty for non-filing sources)
     */
    default List<StructuredFiling> fetchFilings(String symbol) {
        return List.of();
    }
}