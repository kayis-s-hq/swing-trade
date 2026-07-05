package com.swingtrade.api;

import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.strategy.SignalEngine;
import com.swingtrade.api.dto.SignalQueryResult;
import com.swingtrade.api.dto.SignalQueryResult.CombinedSignal;
import com.swingtrade.api.dto.SignalQueryResult.SentimentAnalysis;
import com.swingtrade.api.dto.SignalQueryResult.Signal;
import com.swingtrade.api.dto.SignalQueryResult.SignalType;
import com.swingtrade.api.dto.SignalQueryResult.TechnicalAnalysis;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing trading signals
 */
@Service
public class SignalService {

    private final SignalRepository signalRepository;
    private final SignalEngine signalEngine;

    public SignalService(SignalRepository signalRepository, SignalEngine signalEngine) {
        this.signalRepository = signalRepository;
        this.signalEngine = signalEngine;
    }

    /**
     * Get the latest trading signals for today
     * @return List of latest trading signals
     */
    public List<Signal> getLatestSignals() {
        Pageable pageable = PageRequest.of(0, 50);
        List<SignalEntity> entities = signalRepository.findAll(pageable).getContent();
        return convertToDomain(entities);
    }

    /**
     * Get signals by symbol
     * @param symbol the stock symbol
     * @return List of signals for the symbol
     */
    public List<Signal> getSignalsBySymbol(String symbol) {
        Pageable pageable = PageRequest.of(0, 100);
        List<SignalEntity> entities = signalRepository.findBySymbolOrderByDateDesc(symbol, pageable);
        return convertToDomain(entities);
    }

    /**
     * Get signals by date range and type
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param signalType signal type (BUY, SELL, HOLD)
     * @return List of signals in the date range
     */
    public List<Signal> getSignalsByDateRange(LocalDate startDate, LocalDate endDate, String signalType) {
        Pageable pageable = PageRequest.of(0, 100);
        List<SignalEntity> entities = signalRepository.findByDateRangeAndSignalType(startDate, endDate, signalType, pageable);
        return convertToDomain(entities);
    }

    /**
     * Get signals by date range
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return List of signals in the date range
     */
    public List<Signal> getSignalsByDateRange(LocalDate startDate, LocalDate endDate) {
        Pageable pageable = PageRequest.of(0, 100);
        List<SignalEntity> entities = signalRepository.findByDateRange(startDate, endDate, pageable);
        return convertToDomain(entities);
    }

    /**
     * Get signals by type
     * @param signalType signal type (BUY, SELL, HOLD)
     * @return List of signals of the specified type
     */
    public List<Signal> getSignalsByType(String signalType) {
        Pageable pageable = PageRequest.of(0, 100);
        List<SignalEntity> entities = signalRepository.findByDateRangeAndSignalType(
            LocalDate.now().minusDays(30), LocalDate.now(), signalType, pageable);
        return convertToDomain(entities);
    }

    /**
     * Get high confidence signals
     * @param minConfidence minimum confidence threshold (0.0 to 1.0)
     * @return List of high confidence signals
     */
    public List<Signal> getHighConfidenceSignals(double minConfidence) {
        Pageable pageable = PageRequest.of(0, 100);
        List<SignalEntity> allEntities = signalRepository.findAll(pageable).getContent();
        List<Signal> signals = convertToDomain(allEntities);
        List<Signal> highConfidence = new ArrayList<>();
        for (Signal signal : signals) {
            if (signal.confidence() >= minConfidence) {
                highConfidence.add(signal);
            }
        }
        return highConfidence;
    }

    /**
     * Generate a signal for a specific symbol
     * @param symbol the stock symbol
     * @return Generated signal
     */
    public com.swingtrade.domain.Signal generateSignal(String symbol) {
        // Delegate to SignalEngine for signal generation
        // This would typically trigger the strategy engine to analyze the symbol
        signalEngine.generateSignalsForSymbol(symbol);
        // Return the latest signal for the symbol
        var latest = signalRepository.findLatestBySymbol(symbol);
        return latest.map(com.swingtrade.domain.Signal.class::cast).orElse(null);
    }

    /**
     * Generate a price-action signal for a specific symbol (Phase 2 strategy engine:
     * EMA20/EMA50/RSI14/ATR14/VolumeMA20 breakout rules).
     * @param symbol the stock symbol
     * @return the generated/latest price-action signal entity, or empty if there wasn't
     *         enough candle history to compute one
     */
    public java.util.Optional<SignalEntity> generatePriceActionSignal(String symbol) {
        signalEngine.generatePriceActionSignalForSymbolNow(symbol);
        java.util.List<SignalEntity> latest = signalRepository.findLatestBySymbolAndStrategy(
                symbol, SignalEntity.STRATEGY_PRICE_ACTION,
                PageRequest.of(0, 1));
        return latest.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(latest.get(0));
    }

    /**
     * Get technical analysis for a symbol
     * @param symbol the stock symbol
     * @return Technical analysis results
     */
    public TechnicalAnalysis getTechnicalAnalysis(String symbol) {
        // This would delegate to TechnicalIndicators service
        // For now, return placeholder
        return new TechnicalAnalysis(symbol, LocalDate.now(), new ArrayList<>(), 0.0);
    }

    /**
     * Get sentiment analysis for a symbol
     * @param symbol the stock symbol
     * @return Sentiment analysis results
     */
    public SentimentAnalysis getSentimentAnalysis(String symbol) {
        // This would delegate to LLM sentiment service
        // For now, return placeholder
        return new SentimentAnalysis(symbol, LocalDate.now(), "NEUTRAL", "No recent news");
    }

    /**
     * Get combined signal (technical + sentiment)
     * @param symbol the stock symbol
     * @return Combined signal with technical and sentiment analysis
     */
    public CombinedSignal getCombinedSignal(String symbol) {
        com.swingtrade.domain.Signal technicalSignal = generateSignal(symbol);
        SentimentAnalysis sentiment = getSentimentAnalysis(symbol);

        return new CombinedSignal(
            symbol,
            LocalDate.now(),
            convertDomainSignalToApiSignal(technicalSignal),
            sentiment,
            combineSignals(technicalSignal, sentiment)
        );
    }

    /**
     * Combines technical signal with sentiment to determine final signal
     */
    private SignalType combineSignals(com.swingtrade.domain.Signal technicalSignal, SentimentAnalysis sentiment) {
        com.swingtrade.domain.Signal.SignalType technicalType = technicalSignal.type();
        String sentimentScore = sentiment.score();

        if ("NEGATIVE".equals(sentimentScore)) {
            // Negative sentiment suppresses BUY signals
            if (technicalType == com.swingtrade.domain.Signal.SignalType.BUY) {
                return SignalType.HOLD;
            }
        }

        return SignalType.valueOf(technicalType.name());
    }

    /**
     * Convert domain Signal to API SignalResponse
     */
    public com.swingtrade.api.dto.SignalResponse convertSignalToResponse(com.swingtrade.domain.Signal signal) {
        if (signal == null) return null;
        com.swingtrade.api.dto.SignalResponse response = new com.swingtrade.api.dto.SignalResponse();
        response.setId(signal.id());
        response.setSymbol(signal.symbol());
        response.setDate(signal.date());
        response.setSignalType(com.swingtrade.api.dto.SignalResponse.SignalType.valueOf(signal.type().name()));
        response.setConfidence(signal.confidence());
        response.setReasoning(signal.reasoning());
        response.setEntryPrice(signal.entryPrice());
        response.setStopLoss(signal.stopLoss());
        response.setTarget(signal.target());
        response.setRiskRewardRatio(signal.riskReward());
        response.setIndicators(java.util.List.of(signal.indicators().split(",")));
        response.setGeneratedAt(signal.generatedAt());
        return response;
    }

    /**
     * Convert SignalEntity list to Signal domain objects
     */
    private List<Signal> convertToDomain(List<SignalEntity> entities) {
        List<Signal> signals = new ArrayList<>();
        for (SignalEntity entity : entities) {
            signals.add(convertDomainSignalToApiSignal(entity.toDomain()));
        }
        return signals;
    }

    /**
     * Convert domain Signal to local API Signal class
     */
    private Signal convertDomainSignalToApiSignal(com.swingtrade.domain.Signal domainSignal) {
        if (domainSignal == null) return null;
        return new Signal(
            domainSignal.symbol(),
            domainSignal.type().name(),
            domainSignal.confidence().doubleValue(),
            domainSignal.date(),
            domainSignal.reasoning()
        );
    }
}
