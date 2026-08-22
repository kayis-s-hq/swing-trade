package com.swingtrade.api.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.api.dto.SignalQueryResult.CombinedSignal;
import com.swingtrade.api.dto.SignalQueryResult.SentimentAnalysis;
import com.swingtrade.api.dto.SignalQueryResult.TechnicalAnalysis;
import com.swingtrade.api.dto.SignalResponse;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.SignalStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for managing trading signals
 */
@Service
public class SignalService {

    private final SignalStore signalStore;
    private final SentimentStore sentimentStore;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final SignalEngine signalEngine;
    private final com.swingtrade.domain.store.CandleStore candleStore;

    public SignalService(SignalStore signalStore,
                         SentimentStore sentimentStore,
                         TechnicalAnalysisService technicalAnalysisService,
                         SignalEngine signalEngine,
                         com.swingtrade.domain.store.CandleStore candleStore) {
        this.signalStore = signalStore;
        this.sentimentStore = sentimentStore;
        this.technicalAnalysisService = technicalAnalysisService;
        this.signalEngine = signalEngine;
        this.candleStore = candleStore;
    }

    /**
     * Get the latest trading signals for today
     * @return List of latest trading signals
     */
    public List<SignalResponse> getLatestSignals() {
        // Fetch all signals and take the latest per symbol
        List<Signal> all = signalStore.findAll();
        java.util.Map<String, Signal> latestBySymbol = all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Signal::symbol,
                        s -> s,
                        (a, b) -> b.date() != null && a.date() != null && b.date().isAfter(a.date()) ? b : a));
        return latestBySymbol.values().stream()
                .map(SignalResponse::new)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    /**
     * Get signals by symbol
     * @param symbol the stock symbol
     * @return List of signals for the symbol
     */
    public List<SignalResponse> getSignalsBySymbol(String symbol) {
        return signalStore.findBySymbol(symbol).stream()
                .map(SignalResponse::new)
                .toList();
    }

    /**
     * Get signals by date range and type
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param signalType signal type (BUY, SELL, HOLD)
     * @return List of signals in the date range
     */
    public List<SignalResponse> getSignalsByDateRange(LocalDate startDate, LocalDate endDate, String signalType) {
        return signalStore.findByDateRangeAndType(startDate, endDate, Signal.SignalType.valueOf(signalType)).stream()
                .map(SignalResponse::new)
                .toList();
    }

    /**
     * Get signals by date range
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return List of signals in the date range
     */
    public List<SignalResponse> getSignalsByDateRange(LocalDate startDate, LocalDate endDate) {
        return signalStore.findByDateRange(startDate, endDate).stream()
                .map(SignalResponse::new)
                .toList();
    }

    /**
     * Get signals by type
     * @param signalType signal type (BUY, SELL, HOLD)
     * @return List of signals of the specified type
     */
    public List<SignalResponse> getSignalsByType(String signalType) {
        return signalStore.findByType(Signal.SignalType.valueOf(signalType)).stream()
                .map(SignalResponse::new)
                .toList();
    }

    /**
     * Get high confidence signals
     * @param minConfidence minimum confidence threshold (0.0 to 1.0)
     * @return List of high confidence signals
     */
    public List<SignalResponse> getHighConfidenceSignals(double minConfidence) {
        return signalStore.findByMinConfidence(minConfidence).stream()
                .map(SignalResponse::new)
                .toList();
    }

    /**
     * Generate a signal for a specific symbol
     * @param symbol the stock symbol
     * @return Generated signal
     */
    public com.swingtrade.domain.Signal generateSignal(String symbol) {
        signalEngine.generateSignalForSymbolNow(symbol);
        return signalStore.findLatestBySymbol(symbol).orElse(null);
    }

    /**
     * Generate a price-action signal for a specific symbol (Phase 2 strategy engine:
     * EMA20/EMA50/RSI14/ATR14/VolumeMA20 breakout rules).
     * @param symbol the stock symbol
     * @return the generated/latest price-action signal domain object, or empty if there wasn't
     *         enough candle history to compute one
     */
    public java.util.Optional<com.swingtrade.domain.Signal> generatePriceActionSignal(String symbol) {
        signalEngine.generatePriceActionSignalForSymbolNow(symbol);
        // Filter by strategy name in memory since Store doesn't support strategy filtering
        return signalStore.findBySymbol(symbol).stream()
                .filter(s -> "PRICE_ACTION".equals(s.indicators()) || true) // all signals for now
                .max(java.util.Comparator.comparing(com.swingtrade.domain.Signal::date))
                .or(() -> signalStore.findLatestBySymbol(symbol));
    }

    /**
     * Returns the candle count for a symbol (used for generate-all diagnostics).
     */
    public List<com.swingtrade.domain.OhlcvCandle> getCandleCount(String symbol) {
        return candleStore.findTopBySymbolOrderByDateDesc(symbol, 1000);
    }

    /**
     * Get technical analysis for a symbol
     * @param symbol the stock symbol
     * @return Technical analysis results
     */
    public TechnicalAnalysis getTechnicalAnalysis(String symbol) {
        CompositeAnalysis.TechnicalScore score = technicalAnalysisService.compute(symbol);
        return new TechnicalAnalysis(
            symbol, LocalDate.now(), score.indicators(), score.confidence()
        );
    }

    /**
     * Get sentiment analysis for a symbol
     * @param symbol the stock symbol
     * @return Sentiment analysis results
     */
    public SentimentAnalysis getSentimentAnalysis(String symbol) {
        String sym = symbol.toUpperCase();
        return sentimentStore.findLatestBySymbol(sym)
            .map(e -> new SentimentAnalysis(
                sym, e.date(), e.score().name(), e.summary()))
            .orElseGet(() -> new SentimentAnalysis(sym, LocalDate.now(), "NEUTRAL", "No sentiment data available"));
    }

    /**
     * Get combined signal (technical + sentiment)
     * @param symbol the stock symbol
     * @return Combined signal with technical and sentiment analysis
     */
    public CombinedSignal getCombinedSignal(String symbol) {
        Signal technicalSignal = generateSignal(symbol);
        SentimentAnalysis sentiment = getSentimentAnalysis(symbol);

        return new CombinedSignal(
            symbol,
            LocalDate.now(),
            technicalSignal,
            sentiment,
            combineSignals(technicalSignal, sentiment)
        );
    }

    /**
     * Combines technical signal with sentiment to determine final signal
     */
    private Signal.SignalType combineSignals(Signal technicalSignal, SentimentAnalysis sentiment) {
        Signal.SignalType technicalType = technicalSignal.type();
        String sentimentScore = sentiment.score();

        if ("NEGATIVE".equals(sentimentScore)) {
            // Negative sentiment suppresses BUY signals
            if (technicalType == Signal.SignalType.BUY) {
                return Signal.SignalType.HOLD;
            }
        }

        return technicalType;
    }
}
