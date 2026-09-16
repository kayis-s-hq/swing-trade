package com.swingtrade.domain.store;

import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.PersistedNewsArticle;

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

    /** Saves articles idempotently and returns their persisted identities. */
    List<PersistedNewsArticle> saveAllReturningPersisted(List<NewsArticle> articles);

    /** Finds persisted articles with stable identity and first-seen provenance. */
    List<PersistedNewsArticle> findPersistedBySymbolAndPublishedAtBetween(
        String symbol, OffsetDateTime from, OffsetDateTime through);

    List<PersistedNewsArticle> findPersistedBySymbolAndPublishedAtBetweenAndFirstSeenAtBeforeOrEqual(
        String symbol, OffsetDateTime from, OffsetDateTime through, OffsetDateTime firstSeenCutoff);

    /**
     * Finds recent articles for a symbol since a given time.
     *
     * @param symbol stock symbol
     * @param since time threshold
     * @return list of articles after the threshold
     */
    List<NewsArticle> findRecentBySymbol(String symbol, OffsetDateTime since);

    /**
     * Finds persisted articles inside an inclusive decision-time window.
     * Historical consumers must use this bounded query rather than a live feed.
     */
    List<NewsArticle> findBySymbolAndPublishedAtBetween(String symbol, OffsetDateTime from,
                                                        OffsetDateTime through);

    /**
     * Returns the count of articles for a symbol.
     */
    long countBySymbol(String symbol);
}
