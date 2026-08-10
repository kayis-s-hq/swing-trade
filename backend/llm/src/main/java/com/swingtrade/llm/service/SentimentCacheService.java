package com.swingtrade.llm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory cache service for storing sentiment analysis results.
 * Provides TTL-based expiration and LRU eviction when cache is full.
 */
@Service
public class SentimentCacheService {

    private static final Logger logger = LoggerFactory.getLogger(SentimentCacheService.class);

    // Cache entry with expiration
    private static class CacheEntry<V> {
        final V value;
        final ZonedDateTime expiryTime;
        Instant accessTime;
        final String symbol;

        CacheEntry(V value, long expiryMinutes, String symbol) {
            this.value = value;
            this.expiryTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                    .plusMinutes(expiryMinutes);
            this.accessTime = Instant.now();
            this.symbol = symbol;
        }

        boolean isExpired() {
            return ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).isAfter(expiryTime);
        }

        void touch() {
            this.accessTime = Instant.now();
        }

        V getValue() {
            return value;
        }
    }

    private final Map<String, CacheEntry<?>> cache;
    private final ScheduledExecutorService scheduler;
    private final int maxCacheSize;
    private final long defaultExpiryMinutes;

    // LRU tracking
    private final Map<String, Long> accessOrder;
    private final AtomicInteger evictionCounter;

    /**
     * Constructs SentimentCacheService with configuration.
     *
     * @param maxCacheSize maximum number of entries in cache
     * @param defaultExpiryMinutes default TTL for entries in minutes
     */
    public SentimentCacheService(
            @Value("${llm.sentiment.cache.max-size:100}") int maxCacheSize,
            @Value("${llm.sentiment.cache.expiry-minutes:60}") long defaultExpiryMinutes) {

        this.cache = new ConcurrentHashMap<>();
        this.accessOrder = new LinkedHashMap<>();
        this.maxCacheSize = maxCacheSize;
        this.defaultExpiryMinutes = defaultExpiryMinutes;
        this.evictionCounter = new AtomicInteger(0);

        // Initialize scheduler for periodic cleanup
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                this::cleanupExpiredEntries,
                5,
                5,
                TimeUnit.MINUTES
        );

        logger.info("SentimentCacheService initialized with max size: {}, expiry: {} minutes",
                maxCacheSize, defaultExpiryMinutes);
    }

    /**
     * Caches a sentiment result.
     *
     * @param key the cache key
     * @param value the value to cache
     * @param expiryMinutes TTL in minutes (overrides default if specified)
     */
    public void cache(String key, Object value, long expiryMinutes) {
        // Check if cache is full
        if (cache.size() >= maxCacheSize) {
            pruneOldest();
            logger.debug("Pruned cache, size now: {}", cache.size());
        }

        // Add to cache
        String symbol = extractSymbolFromKey(key);
        CacheEntry<?> entry = new CacheEntry<>(value, expiryMinutes, symbol);
        cache.put(key, entry);
        accessOrder.put(key, System.currentTimeMillis());

        logger.trace("Cached entry for key: {}", key);
    }

    /**
     * Retrieves a cached value.
     *
     * @param key the cache key
     * @return Optional containing the cached value, or empty if not found/expired
     */
    public Optional<CacheEntry<?>> get(String key) {
        CacheEntry<?> entry = cache.get(key);

        if (entry == null) {
            logger.trace("Cache miss for key: {}", key);
            return Optional.empty();
        }

        if (entry.isExpired()) {
            logger.trace("Cache expired for key: {}", key);
            cache.remove(key);
            accessOrder.remove(key);
            return Optional.empty();
        }

        // Update access time
        entry.touch();
        accessOrder.put(key, System.currentTimeMillis());

        return Optional.of(entry);
    }

    /**
     * Retrieves the raw cached value (unwrapped from CacheEntry).
     *
     * @param key the cache key
     * @return Optional containing the cached value, or empty if not found/expired
     */
    public Optional<?> getValue(String key) {
        return get(key).map(CacheEntry::getValue);
    }

    /**
     * Checks if a value is cached (not expired).
     *
     * @param key the cache key
     * @return true if cached and not expired
     */
    public boolean isCached(String key) {
        CacheEntry<?> entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Removes a specific entry from cache.
     *
     * @param key the cache key
     */
    public void remove(String key) {
        cache.remove(key);
        accessOrder.remove(key);
        logger.trace("Removed entry for key: {}", key);
    }

    /**
     * Clears all cached entries.
     */
    public void clearAll() {
        cache.clear();
        accessOrder.clear();
        evictionCounter.set(0);
        logger.info("Cleared all cache entries");
    }

    /**
     * Removes the oldest entry (LRU eviction).
     */
    public void pruneOldest() {
        if (accessOrder.isEmpty()) {
            return;
        }

        // Get oldest key
        String oldestKey = accessOrder.keySet().iterator().next();
        accessOrder.remove(oldestKey);

        // Remove from main cache
        if (cache.remove(oldestKey) != null) {
            evictionCounter.incrementAndGet();
            logger.debug("Pruned oldest entry: {}", oldestKey);
        }
    }

    /**
     * Removes entries for a specific symbol.
     *
     * @param symbol the stock symbol
     */
    public void removeBySymbol(String symbol) {
        List<String> keysToRemove = cache.keySet().stream()
                .filter(key -> key.toUpperCase().startsWith(symbol.toUpperCase()))
                .collect(java.util.stream.Collectors.toList());

        for (String key : keysToRemove) {
            cache.remove(key);
            accessOrder.remove(key);
        }

        logger.info("Removed {} entries for symbol: {}", keysToRemove.size(), symbol);
    }

    /**
     * Gets cached entries for a specific symbol.
     *
     * @param symbol the stock symbol
     * @return map of date to cached sentiment
     */
    public Map<LocalDate, SentimentService.CachedSentiment> getCacheForSymbol(String symbol) {
        Map<LocalDate, SentimentService.CachedSentiment> results = new HashMap<>();

        for (Map.Entry<String, CacheEntry<?>> entry : cache.entrySet()) {
            if (entry.getKey().toUpperCase().startsWith(symbol.toUpperCase())) {
                if (entry.getValue().value instanceof SentimentService.CachedSentiment cached) {
                    // Parse date from key (format: SYMBOL_YYYY-MM-DD)
                    String keyDate = entry.getKey().substring(symbol.length() + 1);
                    try {
                        LocalDate date = LocalDate.parse(keyDate);
                        results.put(date, cached);
                    } catch (Exception e) {
                        logger.trace("Error parsing date from key: {}", entry.getKey());
                    }
                }
            }
        }

        logger.debug("Found {} cached entries for symbol: {}", results.size(), symbol);
        return results;
    }

    /**
     * Gets the current cache size.
     *
     * @return number of entries in cache
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Checks if cache is full.
     *
     * @return true if at max capacity
     */
    public boolean isCacheFull() {
        return cache.size() >= maxCacheSize;
    }

    /**
     * Gets cache statistics.
     *
     * @return cache stats
     */
    public CacheStatistics getStatistics() {
        int currentSize = cache.size();
        int activeSize = (int) cache.values().stream()
                .filter(entry -> !entry.isExpired())
                .count();
        int evictions = evictionCounter.get();

        return new CacheStatistics(currentSize, maxCacheSize, activeSize, evictions);
    }

    /**
     * Cleans up expired entries from cache.
     * Called periodically by scheduler.
     */
    private void cleanupExpiredEntries() {
        List<String> expiredKeys = new ArrayList<>();

        for (Map.Entry<String, CacheEntry<?>> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredKeys.add(entry.getKey());
            }
        }

        if (!expiredKeys.isEmpty()) {
            for (String key : expiredKeys) {
                cache.remove(key);
                accessOrder.remove(key);
            }
            logger.debug("Cleaned up {} expired entries", expiredKeys.size());
        }
    }

    /**
     * Extracts stock symbol from cache key.
     *
     * @param key the cache key
     * @return extracted symbol
     */
    private String extractSymbolFromKey(String key) {
        int underscoreIndex = key.indexOf('_');
        if (underscoreIndex > 0) {
            return key.substring(0, underscoreIndex);
        }
        return key;
    }

    /**
     * Gracefully shuts down the cache service.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("SentimentCacheService shut down");
    }

    /**
     * Record representing cache statistics.
     */
    public record CacheStatistics(
            int currentSize,
            int maxSize,
            int activeSize,
            int totalEvictions
    ) {}
}
