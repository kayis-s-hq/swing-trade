package com.swingtrade.data.service;

/**
 * Search result from Yahoo Finance v1/finance/search endpoint.
 */
public record SearchResult(
    String symbol,
    String shortName,
    String longName,
    String quoteType,
    String exchange,
    String exchangeName
) {
    public static SearchResult of(
        String symbol, String shortName, String longName,
        String quoteType, String exchange, String exchangeName
    ) {
        return new SearchResult(
            symbol, shortName, longName, quoteType, exchange, exchangeName
        );
    }
}
