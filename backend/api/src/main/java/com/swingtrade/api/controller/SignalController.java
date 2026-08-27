package com.swingtrade.api.controller;

import com.swingtrade.api.dto.CombinedSignalResponse;
import com.swingtrade.api.dto.GenerateAllResponse;
import com.swingtrade.api.dto.ScanRequest;
import com.swingtrade.api.dto.ScanResponse;
import com.swingtrade.api.dto.SignalGenerationProgress;
import com.swingtrade.api.dto.SignalResponse;
import com.swingtrade.api.dto.SymbolRequest;
import com.swingtrade.api.dto.TechnicalAnalysisResponse;
import com.swingtrade.api.dto.SentimentAnalysisResponse;
import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Stock;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST Controller for trading signals and analysis.
 * Provides endpoints for signal generation, scanning, and analysis.
 */
@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private static final Logger logger = LoggerFactory.getLogger(SignalController.class);

    @Autowired
    private com.swingtrade.api.service.SignalService signalService;

    @Autowired
    private com.swingtrade.api.service.ScanService scanService;

    @Autowired
    private com.swingtrade.domain.store.SignalStore signalStore;

    @Autowired
    private SentimentStore sentimentStore;

    @Autowired
    private com.swingtrade.domain.store.StockStore stockStore;

    @Autowired
    private com.swingtrade.domain.store.WatchlistStore watchlistStore;

    @Autowired
    private NewsIngestionService newsIngestionService;

    @Autowired
    private SentimentService sentimentService;

    /**
     * Get the latest trading signals for today.
     *
     * @return List of latest signals
     */
    @GetMapping("/latest")
    public ResponseEntity<List<SignalResponse>> getLatestSignals() {
        logger.debug("Fetching latest signals from DB");
        List<Signal> all = signalStore.findAll().stream()
            .sorted(java.util.Comparator.comparing(
                (Signal s) -> s.date() != null ? s.date() : java.time.LocalDate.now()
            ).reversed())
            .toList();
        List<SignalResponse> signals = all.stream().map(SignalResponse::new).collect(Collectors.toList());
        return ResponseEntity.ok(signals);
    }

    /**
     * Get signals for a specific symbol.
     *
     * @param symbol Stock symbol
     * @return List of signals for the symbol
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<SignalResponse>> getSignalsBySymbol(@PathVariable String symbol) {
        logger.debug("Fetching signals for symbol: {}", symbol);
        List<SignalResponse> signals = signalService.getSignalsBySymbol(symbol);
        return ResponseEntity.ok(signals);
    }

    /**
     * Get signals by date range.
     *
     * @param startDate Start date (ISO format)
     * @param endDate End date (ISO format)
     * @return List of signals in date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<SignalResponse>> getSignalsByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        logger.debug("Fetching signals for date range: {} to {}", startDate, endDate);
        List<SignalResponse> signals = signalService.getSignalsByDateRange(startDate, endDate);
        return ResponseEntity.ok(signals);
    }

    /**
     * Get signals by type (BUY, SELL, HOLD).
     *
     * @param type Signal type
     * @return List of signals of specified type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<SignalResponse>> getSignalsByType(@PathVariable Signal.SignalType type) {
        logger.debug("Fetching signals of type: {}", type);
        List<SignalResponse> signals = signalService.getSignalsByType(type.name());
        return ResponseEntity.ok(signals);
    }

    /**
     * Get high confidence signals.
     *
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     * @return List of high confidence signals
     */
    @GetMapping("/high-confidence")
    public ResponseEntity<List<SignalResponse>> getHighConfidenceSignals(
            @RequestParam(defaultValue = "0.7") Double minConfidence
    ) {
        logger.debug("Fetching high confidence signals (min: {})", minConfidence);

        if (minConfidence < 0.0 || minConfidence > 1.0) {
            return ResponseEntity.badRequest().build();
        }

        List<SignalResponse> signals = signalService.getHighConfidenceSignals(minConfidence);
        return ResponseEntity.ok(signals);
    }

    /**
     * Generate a new signal for a symbol.
     *
     * @param request Symbol request
     * @return Generated signal
     */
    @PostMapping("/generate")
    public ResponseEntity<SignalResponse> generateSignal(
            @Valid @RequestBody SymbolRequest request
    ) {
        logger.info("Generating signal for symbol: {}", request.getSymbol());

        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        Signal domainSignal = signalService.generateSignal(request.getSymbol());
        return ResponseEntity.ok(new SignalResponse(domainSignal));
    }

    /**
     * Generate a price-action signal for a symbol (Phase 2 strategy engine).
     *
     * @param symbol Stock symbol
     * @return Generated price-action signal, or 404 if there isn't enough candle history
     */
    @PostMapping("/price-action/{symbol}/generate")
    public ResponseEntity<SignalResponse> generatePriceActionSignal(@PathVariable String symbol) {
        logger.info("Generating price-action signal for symbol: {}", symbol);

        return signalService.generatePriceActionSignal(symbol)
                .map(signal -> ResponseEntity.ok(new SignalResponse(signal)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Generate price-action signals for all active watchlist symbols.
     * Clears the latest signal per symbol before regenerating.
     */
    @PostMapping("/generate-all")
    @Transactional
    public ResponseEntity<GenerateAllResponse> generateAllSignals() {
        logger.info("Generating signals for all watchlist symbols");
        // Ensure stock entries exist for watchlist symbols (prevents FK violations on signal save)
        List<String> symbols = watchlistStore.getActiveWatchlistSymbols();
        ensureStocksExist(symbols);
        int cleared = 0;
        for (String symbol : symbols) {
            List<Signal> existing = signalStore.findBySymbol(symbol);
            if (!existing.isEmpty()) {
                Signal latest = existing.stream()
                    .max(java.util.Comparator.comparing(s -> s.date()))
                    .orElse(null);
                if (latest != null) {
                    cleared += signalStore.deleteByDate(latest.date());
                }
            }
        }
        if (cleared > 0) {
            logger.info("Cleared {} stale signals", cleared);
        }
        java.util.Set<String> generatedSymbols = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        List<SignalResponse> signals = new ArrayList<>();
        List<GenerateAllResponse.SymbolResult> skipped = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                java.util.Optional<Signal> result = signalService.generatePriceActionSignal(symbol);
                if (result.isPresent()) {
                    generatedSymbols.add(symbol);
                    signals.add(new SignalResponse(result.get()));
                } else {
                    List<com.swingtrade.domain.OhlcvCandle> candles =
                        signalService.getCandleCount(symbol);
                    if (candles.isEmpty() || candles.size() < 50) {
                        skipped.add(new GenerateAllResponse.SymbolResult(symbol,
                            "Insufficient candle data (" + candles.size() + " available)"));
                    } else {
                        skipped.add(new GenerateAllResponse.SymbolResult(symbol,
                            "No signal conditions met"));
                    }
                }
            } catch (IllegalStateException e) {
                skipped.add(new GenerateAllResponse.SymbolResult(symbol, e.getMessage()));
            } catch (Exception e) {
                skipped.add(new GenerateAllResponse.SymbolResult(symbol,
                    "Generation error: " + e.getMessage()));
            }
        }
        logger.info("Generated {} signals, skipped {} of {} symbols",
            signals.size(), skipped.size(), symbols.size());
        return ResponseEntity.ok(GenerateAllResponse.of(signals, skipped));
    }

    /**
     * Generate price-action signals for all active watchlist symbols with SSE streaming.
     * Emits progress events for each symbol: generating -> signal/skipped -> complete.
     */
    @PostMapping("/generate-all/stream")
    public SseEmitter generateAllSignalsStream() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        try {
            List<String> symbols = watchlistStore.getActiveWatchlistSymbols();
            int total = symbols.size();

            // Ensure stock entries exist for watchlist symbols (prevents FK violations on signal save)
            ensureStocksExist(symbols);

            // Clear stale signals
            for (String symbol : symbols) {
                List<Signal> existing = signalStore.findBySymbol(symbol);
                if (!existing.isEmpty()) {
                    Signal latest = existing.stream()
                        .max(java.util.Comparator.comparing(s -> s.date()))
                        .orElse(null);
                    if (latest != null) {
                        signalStore.deleteByDate(latest.date());
                    }
                }
            }

            emitter.send(SseEmitter.event()
                .name("progress")
                .data(SignalGenerationProgress.started(total)));
        } catch (IOException e) {
            logger.warn("Failed to send start event: {}", e.getMessage());
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                List<String> symbols = watchlistStore.getActiveWatchlistSymbols();
                int total = symbols.size();
                final int[] signalCount = {0};
                final int[] skipCount = {0};

                for (int i = 0; i < symbols.size(); i++) {
                    String symbol = symbols.get(i);
                    int current = i + 1;
                    try {
                        emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(SignalGenerationProgress.generating(symbol, current, total)));

                        java.util.Optional<Signal> result = signalService.generatePriceActionSignal(symbol);
                        if (result.isPresent()) {
                            signalCount[0]++;
                            SignalResponse response = new SignalResponse(result.get());
                            emitter.send(SseEmitter.event()
                                .name("signal")
                                .data(SignalGenerationProgress.signalDone(symbol, response, current, total)));
                        } else {
                            skipCount[0]++;
                            List<com.swingtrade.domain.OhlcvCandle> candles = signalService.getCandleCount(symbol);
                            String reason = (candles.isEmpty() || candles.size() < 50)
                                ? "Insufficient candle data (" + candles.size() + " available)"
                                : "No signal conditions met";
                            emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(SignalGenerationProgress.skipped(symbol, reason, current, total)));
                        }
                    } catch (Exception e) {
                        try {
                            skipCount[0]++;
                            emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(SignalGenerationProgress.skipped(symbol, "Error: " + e.getMessage(), current, total)));
                        } catch (IOException ioEx) {
                            logger.warn("Failed to send error event for {}: {}", symbol, ioEx.getMessage());
                        }
                    }
                }

                emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(SignalGenerationProgress.complete(signalCount[0], skipCount[0], total)));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", e.getMessage())));
                } catch (IOException ioEx) {
                    logger.error("Failed to send error: {}", ioEx.getMessage());
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> logger.info("Client disconnected from generate-all stream"));
        emitter.onTimeout(() -> logger.warn("Generate-all stream timed out"));
        emitter.onError(e -> logger.error("Generate-all stream error: {}", e.getMessage()));

        return emitter;
    }

    /**
     * Ensure stock entries exist in the stocks table for the given symbols.
     * Prevents FK violations when signal persistence tries to insert signals.
     */
    private void ensureStocksExist(List<String> symbols) {
        for (String symbol : symbols) {
            if (!stockStore.existsBySymbol(symbol)) {
                try {
                    Stock stock = new Stock(
                        symbol,
                        Stock.Exchange.NSE,
                        symbol,
                        Stock.Sector.OTHERS,
                        null, null, null, null, null,
                        java.time.LocalDate.now()
                    );
                    stockStore.save(stock);
                    logger.debug("Auto-created stock entry for {}", symbol);
                } catch (Exception e) {
                    // Race condition: another thread may have created it
                    logger.debug("Stock for {} already exists or failed: {}", symbol, e.getMessage());
                }
            }
        }
    }

    /**
     * Clear all signals.
     */
    @DeleteMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> clearAllSignals() {
        logger.info("Clearing all signals");
        int cleared = signalStore.deleteAllSignals();
        logger.info("Cleared {} signals", cleared);
        return ResponseEntity.ok(Map.of("cleared", cleared));
    }

    /**
     * Path segments used elsewhere in this controller as literal routes (e.g.
     * {@code GET /api/signals/latest}) rather than as a {@code {symbol}} path
     * variable. {@code DELETE} has no matching literal mapping for these, so
     * without this guard they'd silently fall through to
     * {@link #clearSignalForSymbol} and be treated as a (non-existent) stock
     * symbol instead of being rejected.
     */
    private static final java.util.Set<String> RESERVED_SIGNAL_PATH_SEGMENTS =
        java.util.Set.of("latest", "date-range", "scan");

    /**
     * Clear signals for a specific symbol.
     */
    @DeleteMapping("/{symbol}")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearSignalForSymbol(@PathVariable String symbol) {
        if (RESERVED_SIGNAL_PATH_SEGMENTS.contains(symbol.toLowerCase(java.util.Locale.ROOT))) {
            logger.warn("Rejected DELETE /api/signals/{} — '{}' is a reserved path segment, not a stock symbol",
                symbol, symbol);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "'" + symbol + "' is a reserved path segment, not a stock symbol"));
        }
        logger.info("Clearing signals for {}", symbol);
        List<Signal> signals = signalStore.findBySymbol(symbol);
        int cleared = 0;
        for (Signal s : signals) {
            cleared += signalStore.deleteByDate(s.date());
        }
        return ResponseEntity.ok(Map.of("cleared", cleared, "symbol", symbol));
    }

    /**
     * Trigger a full market scan.
     *
     * @param request Scan request (optional parameters)
     * @return Scan result
     */
    @PostMapping("/scan")
    public ResponseEntity<ScanResponse> triggerScan(
            @Valid @RequestBody(required = false) ScanRequest request
    ) {
        logger.info("Triggering market scan");
        ScanResponse scanResult = scanService.triggerScan();

        if (scanResult.getSignalsFound() == 0) {
            scanResult.setStatus(ScanResponse.ScanStatus.NO_SIGNALS);
        }

        return ResponseEntity.ok(scanResult);
    }

    /**
     * Get scan history.
     *
     * @return List of scan results
     */
    @GetMapping("/scan/history")
    public ResponseEntity<List<ScanResponse>> getScanHistory() {
        logger.debug("Fetching scan history");
        List<ScanResponse> history = scanService.getScanHistory();
        return ResponseEntity.ok(history);
    }

    /**
     * Get technical analysis for a symbol.
     *
     * @param symbol Stock symbol
     * @return Technical analysis result
     */
    @GetMapping("/analysis/{symbol}")
    public ResponseEntity<TechnicalAnalysisResponse> getTechnicalAnalysis(@PathVariable String symbol) {
        logger.debug("Fetching technical analysis for symbol: {}", symbol);
        TechnicalAnalysisResponse analysis = convertTechnicalAnalysis(signalService.getTechnicalAnalysis(symbol));

        if (analysis == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(analysis);
    }

    /**
     * Get sentiment analysis for a symbol.
     *
     * @param symbol Stock symbol
     * @return Sentiment analysis result
     */
    @GetMapping("/sentiment/{symbol}")
    public ResponseEntity<SentimentAnalysisResponse> getSentimentAnalysis(@PathVariable String symbol) {
        logger.debug("Fetching sentiment analysis for symbol: {}", symbol);
        Optional<com.swingtrade.domain.SentimentResult> opt = sentimentStore.findLatestBySymbol(symbol);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        com.swingtrade.domain.SentimentResult result = opt.get();
        SentimentAnalysisResponse response = new SentimentAnalysisResponse();
        response.setSymbol(result.symbol());
        response.setAnalyzedAt(result.analyzedAt());
        response.setScore(SentimentAnalysisResponse.SentimentScore.valueOf(result.score().name()));
        response.setSummary(result.summary());
        return ResponseEntity.ok(response);
    }

    /**
     * Get combined signal with LLM sentiment.
     *
     * @param symbol Stock symbol
     * @return Combined signal analysis
     */
    @GetMapping("/combined/{symbol}")
    public ResponseEntity<CombinedSignalResponse> getCombinedSignal(@PathVariable String symbol) {
        logger.debug("Fetching combined signal for symbol: {}", symbol);
        CombinedSignalResponse combined = new CombinedSignalResponse();
        combined.setSymbol(symbol);
        combined.setAnalysisDate(LocalDate.now());

        // Fetch fresh news from all sources
        try {
            List<NewsArticle> articles = newsIngestionService.fetchStockNews(symbol);
            logger.info("Fetched {} articles for {} from all sources", articles.size(), symbol);

            // Run sentiment analysis with fresh news
            com.swingtrade.domain.SentimentResult result = sentimentService.analyzeStockSentiment(symbol, LocalDate.now());
            SentimentAnalysisResponse sa = new SentimentAnalysisResponse();
            sa.setSymbol(result.symbol());
            sa.setAnalyzedAt(result.analyzedAt());
            sa.setScore(SentimentAnalysisResponse.SentimentScore.valueOf(result.score().name()));
            sa.setSummary(result.summary());
            sa.setConfidence(result.confidence() != null ? result.confidence().doubleValue() : null);
            sa.setArticleCount(result.articleCount());
            combined.setSentimentAnalysis(sa);
        } catch (Exception e) {
            logger.warn("Combined signal fetch failed for {}: {}", symbol, e.getMessage());
        }

        return ResponseEntity.ok(combined);
    }

    
    /**
     * Convert SignalService.TechnicalAnalysis to TechnicalAnalysisResponse DTO
     */
    private TechnicalAnalysisResponse convertTechnicalAnalysis(com.swingtrade.api.dto.SignalQueryResult.TechnicalAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        TechnicalAnalysisResponse response = new TechnicalAnalysisResponse();
        response.setSymbol(analysis.symbol());
        response.setAnalysisDate(analysis.date());
        response.setIndicators(analysis.indicators());
        response.setSignal("TECHNICAL");
        return response;
    }

    /**
     * Convert SignalService.SentimentAnalysis to SentimentAnalysisResponse DTO
     */
    private SentimentAnalysisResponse convertSentimentAnalysis(com.swingtrade.api.dto.SignalQueryResult.SentimentAnalysis sentiment) {
        if (sentiment == null) {
            return null;
        }
        SentimentAnalysisResponse response = new SentimentAnalysisResponse();
        response.setSymbol(sentiment.symbol());
        response.setAnalyzedAt(sentiment.date());
        response.setScore(SentimentAnalysisResponse.SentimentScore.valueOf(sentiment.score()));
        response.setSummary(sentiment.summary());
        return response;
    }

    /**
     * Convert SignalService.CombinedSignal to CombinedSignalResponse DTO
     */
    private CombinedSignalResponse convertCombinedSignal(com.swingtrade.api.dto.SignalQueryResult.CombinedSignal combined) {
        if (combined == null) {
            return null;
        }
        CombinedSignalResponse response = new CombinedSignalResponse();
        response.setSymbol(combined.symbol());
        response.setAnalysisDate(combined.date());
        response.setSignalType(Signal.SignalType.valueOf(combined.finalSignal().name()));
        response.setTechnicalAnalysis(convertTechnicalAnalysis(null)); // Placeholder
        response.setSentimentAnalysis(convertSentimentAnalysis(combined.sentiment()));
        return response;
    }
}
