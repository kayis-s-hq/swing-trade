package com.swingtrade.llm.service;

import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.Signal;
import com.swingtrade.llm.SentimentAnalysisResult;
import com.swingtrade.llm.SentimentType;
import com.swingtrade.llm.client.VLLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Main service for sentiment analysis of stocks using LLM.
 * Orchestrates news ingestion, sentiment analysis, caching, and signal generation.
 */
@Service
public class SentimentAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(SentimentAnalysisService.class);

    // Cache configuration
    private static final int DEFAULT_CACHE_MAX_SIZE = 100;
    private static final long DEFAULT_CACHE_EXPIRY_MINUTES = 60;
    private static final long ANALYSIS_TIMEOUT_SECONDS = 120;

    // Thread pool for async operations
    private final ExecutorService analysisExecutor;

    private final VLLMClient vllmClient;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final NewsIngestionService newsIngestionService;
    private final SentimentCacheService sentimentCacheService;

    private final int maxCacheSize;
    private final long cacheExpiryMinutes;
    private final boolean enableCaching;
    private final double defaultConfidence;

    /**
     * Constructs SentimentAnalysisService with required dependencies.
     *
     * @param vllmClient LLM client for sentiment analysis
     * @param sentimentAnalyzer helper for prompt formatting
     * @param newsIngestionService for fetching stock news
     * @param sentimentCacheService for caching sentiment results
     * @param maxCacheSize maximum number of cached results
     * @param cacheExpiryMinutes cache expiration time in minutes
     * @param enableCaching whether caching is enabled
     * @param defaultConfidence default confidence level for analysis
     */
    @Autowired
    public SentimentAnalysisService(
            VLLMClient vllmClient,
            SentimentAnalyzer sentimentAnalyzer,
            NewsIngestionService newsIngestionService,
            SentimentCacheService sentimentCacheService,
            @Value("${llm.sentiment.cache.max-size:100}") int maxCacheSize,
            @Value("${llm.sentiment.cache.expiry-minutes:60}") long cacheExpiryMinutes,
            @Value("${llm.sentiment.cache.enabled:true}") boolean enableCaching,
            @Value("${llm.sentiment.default-confidence:0.75}") double defaultConfidence) {

        this.vllmClient = vllmClient;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.newsIngestionService = newsIngestionService;
        this.sentimentCacheService = sentimentCacheService;

        this.maxCacheSize = maxCacheSize;
        this.cacheExpiryMinutes = cacheExpiryMinutes;
        this.enableCaching = enableCaching;
        this.defaultConfidence = defaultConfidence;

        // Initialize thread pool with bounded capacity
        this.analysisExecutor = new ThreadPoolExecutor(
                2,  // 2 core threads
                5,  // 5 max threads
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Analyzes sentiment for a single stock using news from RSS feeds.
     *
     * @param stockSymbol the stock symbol (e.g., "RELIANCE", "TCS")
     * @param date the date of analysis
     * @return SentimentResult with analysis findings
     */
    public SentimentResult analyzeStockSentiment(String stockSymbol, LocalDate date) {
        logger.info("Starting sentiment analysis for stock: {} on date: {}", stockSymbol, date);

        // Check cache first
        String cacheKey = generateCacheKey(stockSymbol, date);
        if (enableCaching && sentimentCacheService.isCached(cacheKey)) {
            logger.debug("Cache hit for {}: {}", stockSymbol, cacheKey);
            var cached = sentimentCacheService.getValue(cacheKey);
            if (cached.isPresent()) {
                CachedSentiment sentimentData = (CachedSentiment) cached.get();
                return buildSentimentResultFromCache(stockSymbol, date, sentimentData);
            }
        }

        try {
            // Fetch news articles
            List<NewsIngestionService.NewsArticle> articles =
                    newsIngestionService.fetchStockNews(stockSymbol);

            if (articles.isEmpty()) {
                logger.warn("No news articles found for stock: {}", stockSymbol);
                return createDefaultSentimentResult(stockSymbol, date, SentimentType.NEUTRAL);
            }

            logger.info("Found {} articles for {}: {}", articles.size(), stockSymbol, stockSymbol);

            // Clean and prepare news content
            List<String> newsContent = articles.stream()
                    .map(newsIngestionService::cleanNewsText)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());

            if (newsContent.isEmpty()) {
                logger.warn("No valid news content after cleaning for stock: {}", stockSymbol);
                return createDefaultSentimentResult(stockSymbol, date, SentimentType.NEUTRAL);
            }

            // Perform sentiment analysis
            SentimentAnalysisResult analysisResult =
                    performSentimentAnalysis(stockSymbol, newsContent);

            // Build and cache result
            SentimentResult result = buildSentimentResult(stockSymbol, date, analysisResult, articles);

            // Cache the result
            if (enableCaching) {
                cacheSentimentResult(cacheKey, result, analysisResult);
            }

            logger.info("Sentiment analysis complete for {}: {} (confidence: {})",
                    stockSymbol, analysisResult.getSentiment(), analysisResult.getConfidence());

            return result;

        } catch (Exception e) {
            logger.error("Error analyzing sentiment for {}: {}", stockSymbol, e.getMessage(), e);
            // Return default neutral result on error
            return createDefaultSentimentResult(stockSymbol, date, SentimentType.NEUTRAL);
        }
    }

    /**
     * Performs LLM-based sentiment analysis on news content.
     *
     * @param stockSymbol the stock symbol
     * @param newsContent list of cleaned news texts
     * @return sentiment analysis result
     */
    private SentimentAnalysisResult performSentimentAnalysis(String stockSymbol, List<String> newsContent) {
        logger.debug("Performing LLM sentiment analysis for {} with {} articles", stockSymbol, newsContent.size());

        if (newsContent.isEmpty()) {
            return new SentimentAnalysisResult(
                    SentimentType.NEUTRAL,
                    "No news content available for analysis",
                    0.0
            );
        }

        // Combine all news content into a single prompt for comprehensive analysis
        String combinedContent = String.join("\n\n---\n\n", newsContent);

        // Create prompt using SentimentAnalyzer
        List<Map<String, String>> messages =
                sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, combinedContent);

        // Call LLM for sentiment analysis
        String llmResponse;
        try {
            llmResponse = vllmClient.generateChatCompletion(messages, 512, 0.3)
                    .block(Duration.ofSeconds(ANALYSIS_TIMEOUT_SECONDS));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                logger.error("Timeout analyzing sentiment for {}: analysis took more than {} seconds",
                        stockSymbol, ANALYSIS_TIMEOUT_SECONDS);
                return new SentimentAnalysisResult(
                        SentimentType.NEUTRAL,
                        "Analysis timed out - unable to process news content",
                        0.2
                );
            }
            logger.error("Error during LLM sentiment analysis: {}", e.getMessage(), e);
            throw e;
        }

        if (llmResponse == null || llmResponse.isBlank()) {
            logger.warn("Empty LLM response for stock: {}", stockSymbol);
            return new SentimentAnalysisResult(
                    SentimentType.NEUTRAL,
                    "No valid response from LLM",
                    0.1
            );
        }

        logger.debug("LLM response for {}: {} chars", stockSymbol, llmResponse.length());

        // Parse LLM response
        return parseSentimentResponse(stockSymbol, llmResponse);
    }

    /**
     * Parses LLM JSON response into SentimentAnalysisResult.
     *
     * @param stockSymbol the stock symbol
     * @param jsonResponse the JSON response from LLM
     * @return parsed sentiment analysis result
     */
    private SentimentAnalysisResult parseSentimentResponse(String stockSymbol, String jsonResponse) {
        try {
            // Extract sentiment type
            SentimentType sentiment = sentimentAnalyzer.extractSentimentFromResponse(jsonResponse);

            // Extract confidence (try to find in JSON, fallback to default)
            Double confidence = extractConfidenceFromResponse(jsonResponse, defaultConfidence);

            // Extract reasoning
            String reasoning = extractReasoningFromResponse(jsonResponse);

            logger.debug("Parsed sentiment for {}: {} (confidence: {})",
                    stockSymbol, sentiment, confidence);

            return new SentimentAnalysisResult(sentiment, reasoning, confidence);

        } catch (Exception e) {
            logger.error("Error parsing LLM response for {}: {}", stockSymbol, e.getMessage());
            return new SentimentAnalysisResult(
                    SentimentType.NEUTRAL,
                    "Error parsing response: " + e.getMessage(),
                    0.2
            );
        }
    }

    /**
     * Extracts confidence value from LLM JSON response.
     *
     * @param jsonResponse the JSON response
     * @param defaultValue default value if not found
     * @return confidence value between 0 and 1
     */
    private Double extractConfidenceFromResponse(String jsonResponse, double defaultValue) {
        try {
            // Look for "confidence": X.X pattern in JSON
            String regex = "\"confidence\"\\s*:\\s*([0-9.]+)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(jsonResponse);

            if (matcher.find()) {
                Double confidence = Double.parseDouble(matcher.group(1));
                // Clamp to 0-1 range
                return Math.max(0.0, Math.min(1.0, confidence));
            }
        } catch (Exception e) {
            logger.debug("Could not extract confidence from response: {}", e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Extracts reasoning text from LLM JSON response.
     *
     * @param jsonResponse the JSON response
     * @return reasoning text
     */
    private String extractReasoningFromResponse(String jsonResponse) {
        try {
            // Look for "reasoning": "..." pattern in JSON
            String regex = "\"reasoning\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(jsonResponse);

            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            logger.debug("Could not extract reasoning from response: {}", e.getMessage());
        }

        return "Sentiment analysis completed with limited reasoning extracted from LLM response.";
    }

    /**
     * Builds SentimentResult from analysis result.
     *
     * @param stockSymbol the stock symbol
     * @param date the analysis date
     * @param analysisResult the LLM analysis result
     * @param articles the news articles analyzed
     * @return built sentiment result
     */
    private SentimentResult buildSentimentResult(
            String stockSymbol,
            LocalDate date,
            SentimentAnalysisResult analysisResult,
            List<NewsIngestionService.NewsArticle> articles) {

        // Convert SentimentType to SentimentResult.SentimentScore
        SentimentResult.SentimentScore score;
        switch (analysisResult.getSentiment()) {
            case POSITIVE:
                score = SentimentResult.SentimentScore.POSITIVE;
                break;
            case NEGATIVE:
                score = SentimentResult.SentimentScore.NEGATIVE;
                break;
            default:
                score = SentimentResult.SentimentScore.NEUTRAL;
        }

        // Combine article content as raw content
        String rawContent = articles.stream()
                .map(NewsIngestionService.NewsArticle::getRawContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));

        return SentimentResult.create(
                stockSymbol,
                date,
                score,
                analysisResult.getReasoning(),
                rawContent,
                analysisResult.getConfidence()
        );
    }

    /**
     * Creates default sentiment result with neutral sentiment.
     *
     * @param stockSymbol the stock symbol
     * @param date the date
     * @param sentimentType the sentiment type
     * @return default sentiment result
     */
    private SentimentResult createDefaultSentimentResult(
            String stockSymbol,
            LocalDate date,
            SentimentType sentimentType) {

        String defaultReasoning = switch (sentimentType) {
            case POSITIVE -> "No negative news found - potentially positive outlook";
            case NEGATIVE -> "No significant news found - potentially neutral to negative";
            default -> "No news articles available for analysis - defaulting to neutral";
        };

        return SentimentResult.create(
                stockSymbol,
                date,
                sentimentType == SentimentType.POSITIVE ?
                        SentimentResult.SentimentScore.POSITIVE :
                        sentimentType == SentimentType.NEGATIVE ?
                                SentimentResult.SentimentScore.NEGATIVE :
                                SentimentResult.SentimentScore.NEUTRAL,
                defaultReasoning,
                "",
                0.3
        );
    }

    /**
     * Builds SentimentResult from cached sentiment data.
     *
     * @param stockSymbol the stock symbol
     * @param date the analysis date
     * @param cached the cached sentiment data
     * @return built sentiment result
     */
    private SentimentResult buildSentimentResultFromCache(
            String stockSymbol,
            LocalDate date,
            CachedSentiment cached) {

        SentimentResult.SentimentScore score;
        switch (cached.sentimentType()) {
            case POSITIVE:
                score = SentimentResult.SentimentScore.POSITIVE;
                break;
            case NEGATIVE:
                score = SentimentResult.SentimentScore.NEGATIVE;
                break;
            default:
                score = SentimentResult.SentimentScore.NEUTRAL;
        }

        return new SentimentResult(
                cached.resultId(),
                stockSymbol,
                date,
                score,
                cached.reasoning(),
                cached.rawContent(),
                cached.confidence(),
                LocalDate.now()
        );
    }

    /**
     * Caches sentiment result for future retrieval.
     *
     * @param cacheKey the cache key
     * @param result the sentiment result
     * @param analysisResult the LLM analysis result
     */
    private void cacheSentimentResult(
            String cacheKey,
            SentimentResult result,
            SentimentAnalysisResult analysisResult) {

        if (sentimentCacheService.isCacheFull()) {
            logger.debug("Cache is full, pruning oldest entry");
            sentimentCacheService.pruneOldest();
        }

        String rawContent = result.rawContent() != null ? result.rawContent() : "";

        CachedSentiment cached = new CachedSentiment(
                result.id(),
                analysisResult.getSentiment(),
                analysisResult.getReasoning(),
                rawContent,
                analysisResult.getConfidence()
        );

        sentimentCacheService.cache(cacheKey, cached, cacheExpiryMinutes);
    }

    /**
     * Generates cache key for sentiment analysis result.
     *
     * @param stockSymbol the stock symbol
     * @param date the analysis date
     * @return cache key
     */
    private String generateCacheKey(String stockSymbol, LocalDate date) {
        return String.format("%s_%s", stockSymbol.toUpperCase(), date);
    }

    /**
     * Clears cache entry for a specific stock and date.
     *
     * @param stockSymbol the stock symbol
     * @param date the date
     */
    public void clearCache(String stockSymbol, LocalDate date) {
        String cacheKey = generateCacheKey(stockSymbol, date);
        sentimentCacheService.remove(cacheKey);
        logger.debug("Cleared cache for {}", cacheKey);
    }

    /**
     * Clears all cached sentiment results.
     */
    public void clearAllCache() {
        sentimentCacheService.clearAll();
        logger.info("Cleared all sentiment cache entries");
    }

    /**
     * Analyzes sentiment for multiple stocks asynchronously.
     *
     * @param stockSymbols list of stock symbols
     * @param date the analysis date
     * @return map of stock symbol to sentiment result
     */
    public Map<String, SentimentResult> analyzeMultipleStocks(
            List<String> stockSymbols,
            LocalDate date) {

        logger.info("Starting batch sentiment analysis for {} stocks", stockSymbols.size());

        Map<String, Future<SentimentResult>> futures = new ConcurrentHashMap<>();

        // Submit all analysis tasks
        for (String symbol : stockSymbols) {
            Future<SentimentResult> future = analysisExecutor.submit(() ->
                    analyzeStockSentiment(symbol, date));
            futures.put(symbol, future);
        }

        // Collect results
        Map<String, SentimentResult> results = new HashMap<>();
        List<String> failedSymbols = new ArrayList<>();

        for (Map.Entry<String, Future<SentimentResult>> entry : futures.entrySet()) {
            try {
                SentimentResult result = entry.getValue().get(ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                results.put(entry.getKey(), result);
            } catch (Exception e) {
                logger.error("Error analyzing sentiment for {}: {}", entry.getKey(), e.getMessage());
                failedSymbols.add(entry.getKey());
                // Add default result for failed analysis
                results.put(entry.getKey(), createDefaultSentimentResult(
                        entry.getKey(),
                        date,
                        SentimentType.NEUTRAL
                ));
            }
        }

        logger.info("Batch analysis complete: {} successful, {} failed",
                results.size() - failedSymbols.size(), failedSymbols.size());

        return results;
    }

    /**
     * Generates trading signals based on sentiment analysis.
     *
     * @param stockSymbol the stock symbol
     * @param sentimentResult the sentiment result
     * @param currentPrice the current stock price
     * @return trading signal recommendation
     */
    public Signal generateSignalFromSentiment(
            String stockSymbol,
            SentimentResult sentimentResult,
            Double currentPrice) {

        logger.info("Generating trading signal from sentiment for {}: {}", stockSymbol, sentimentResult.score());

        // Determine signal type based on sentiment
        Signal.SignalType signalType;
        BigDecimal confidence;
        String reasoning;

        if (sentimentResult.isPositive()) {
            signalType = Signal.SignalType.BUY;
            confidence = BigDecimal.valueOf(sentimentResult.confidence() != null ?
                    sentimentResult.confidence() * 0.8 : 0.75); // Scale down slightly for trading
            reasoning = String.format(
                    "BUY signal based on positive sentiment (%.0%% confidence). " +
                    "News analysis indicates favorable market conditions. %s",
                    sentimentResult.confidence() * 100,
                    sentimentResult.summary()
            );
        } else if (sentimentResult.isNegative()) {
            signalType = Signal.SignalType.SELL;
            confidence = BigDecimal.valueOf(sentimentResult.confidence() != null ?
                    sentimentResult.confidence() * 0.8 : 0.75);
            reasoning = String.format(
                    "SELL signal based on negative sentiment (%.0%% confidence). " +
                    "News analysis indicates unfavorable market conditions. %s",
                    sentimentResult.confidence() * 100,
                    sentimentResult.summary()
            );
        } else {
            signalType = Signal.SignalType.HOLD;
            confidence = BigDecimal.valueOf(0.5); // Neutral confidence
            reasoning = String.format(
                    "HOLD signal based on neutral sentiment. " +
                    "No strong directional signal from news analysis. %s",
                    sentimentResult.summary()
            );
        }

        return Signal.create(stockSymbol, sentimentResult.date(), signalType, confidence, reasoning);
    }

    /**
     * Gets cached sentiment results for a stock.
     *
     * @param stockSymbol the stock symbol
     * @return map of date to cached sentiment
     */
    public Map<LocalDate, CachedSentiment> getCachedSentiments(String stockSymbol) {
        return sentimentCacheService.getCacheForSymbol(stockSymbol.toUpperCase());
    }

    /**
     * Checks if sentiment is cached for a specific stock and date.
     *
     * @param stockSymbol the stock symbol
     * @param date the date
     * @return true if cached
     */
    public boolean isSentimentCached(String stockSymbol, LocalDate date) {
        return enableCaching && sentimentCacheService.isCached(
                generateCacheKey(stockSymbol, date));
    }

    /**
     * Refreshes cached sentiment for a stock (forces re-analysis).
     *
     * @param stockSymbol the stock symbol
     * @param date the date
     * @return refreshed sentiment result
     */
    public SentimentResult refreshSentiment(String stockSymbol, LocalDate date) {
        // Remove from cache first
        clearCache(stockSymbol, date);

        // Re-analyze
        return analyzeStockSentiment(stockSymbol, date);
    }

    /**
     * Gets statistics about cached sentiment data.
     *
     * @return cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(
                sentimentCacheService.getCacheSize(),
                maxCacheSize,
                enableCaching,
                cacheExpiryMinutes
        );
    }

    /**
     * Gracefully shuts down the service and thread pool.
     */
    public void shutdown() {
        logger.info("Shutting down sentiment analysis service");
        analysisExecutor.shutdown();
        try {
            if (!analysisExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            analysisExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ============ Inner Classes ============

    /**
     * Record representing cached sentiment data.
     */
    public record CachedSentiment(
            Long resultId,
            SentimentType sentimentType,
            String reasoning,
            String rawContent,
            Double confidence
    ) {}

    /**
     * Record representing cache statistics.
     */
    public record CacheStatistics(
            int currentSize,
            int maxSize,
            boolean isEnabled,
            long expiryMinutes
    ) {}
}
