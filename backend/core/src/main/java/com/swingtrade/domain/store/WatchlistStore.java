package com.swingtrade.domain.store;

import com.swingtrade.domain.Stock;

import java.util.List;

public interface WatchlistStore {

    List<Stock> getWatchlist();

    void addToWatchlist(String symbol);

    void removeFromWatchlist(String symbol);

    boolean existsBySymbol(String symbol);

    List<Stock> getWatchlistByExchange(String exchange);

    /**
     * Returns active watchlist symbols in alphabetical order.
     */
    List<String> getActiveWatchlistSymbols();
}