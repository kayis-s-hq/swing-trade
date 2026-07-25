package com.swingtrade.domain.store;

import com.swingtrade.domain.NewsArticle;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Store interface for persisting and retrieving news articles.
 * Implemented in the data module to keep JPA dependencies out of the llm module.
 */
public interface NewsArticleStore {

    /**
     * Saves a list of news articles to the database.
     *
     * @param articles articles to persist
     * @return number of articles saved
     */
    int saveAll(List<NewsArticle> articles);

    /**
     * Finds recent articles for a symbol since a given time.
     *
     * @param symbol stock symbol
     * @param since time threshold
     * @return list of articles after the threshold
     */
    List<NewsArticle> findRecentBySymbol(String symbol, OffsetDateTime since);

    /**
     * Returns the count of articles for a symbol.
     */
    long countBySymbol(String symbol);
}