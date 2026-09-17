package com.swingtrade.data.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Applies a provider-wide minimum interval between market-data requests.
 * The wrapper is deliberately synchronous: ingestion callers already serialize the
 * provider request path, and a single limiter prevents concurrent backfill workers
 * from bypassing the provider's quota.
 */
final class RateLimitedMarketDataClient implements MarketDataClient {

    private final MarketDataClient delegate;
    private final long minimumIntervalMillis;
    private long nextAllowedAtNanos;

    RateLimitedMarketDataClient(MarketDataClient delegate, long minimumIntervalMillis) {
        this.delegate = delegate;
        this.minimumIntervalMillis = Math.max(0L, minimumIntervalMillis);
    }

    private void acquire() {
        if (minimumIntervalMillis == 0L) return;
        synchronized (this) {
            long now = System.nanoTime();
            long waitNanos = nextAllowedAtNanos - now;
            if (waitNanos > 0L) {
                try {
                    long millis = waitNanos / 1_000_000L;
                    int nanos = (int) (waitNanos % 1_000_000L);
                    Thread.sleep(millis, nanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while rate limiting market-data request", e);
                }
            }
            nextAllowedAtNanos = System.nanoTime() + minimumIntervalMillis * 1_000_000L;
        }
    }

    @Override public CandleData fetchCandle(String symbol, LocalDate date) {
        acquire(); return delegate.fetchCandle(symbol, date);
    }
    @Override public Iterable<CandleData> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate) {
        acquire(); return delegate.fetchCandles(symbol, startDate, endDate);
    }
    @Override public CandleData fetchLatestCandle(String symbol) {
        acquire(); return delegate.fetchLatestCandle(symbol);
    }
    @Override public InstrumentDetails fetchInstrumentDetails(String symbol) {
        acquire(); return delegate.fetchInstrumentDetails(symbol);
    }
    @Override public ChartMeta fetchChartMeta(String symbol) {
        acquire(); return delegate.fetchChartMeta(symbol);
    }
    @Override public QuoteData fetchQuote(String symbol) {
        acquire(); return delegate.fetchQuote(symbol);
    }
    @Override public List<QuoteData> fetchQuotes(List<String> symbols) {
        acquire(); return delegate.fetchQuotes(symbols);
    }
    @Override public List<SearchResult> searchSymbols(String query) {
        acquire(); return delegate.searchSymbols(query);
    }
    @Override public Iterable<String> fetchAllStockSymbols() {
        acquire(); return delegate.fetchAllStockSymbols();
    }
    @Override public boolean isConnected() {
        acquire(); return delegate.isConnected();
    }
}
