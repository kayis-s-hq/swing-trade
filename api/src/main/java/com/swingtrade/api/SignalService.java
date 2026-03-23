package com.swingtrade.api;

import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.strategy.SignalEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing trading signals
 */
@Service
public class SignalService {

    private final SignalRepository signalRepository;
    private final SignalEngine signalEngine;

    @Autowired
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
        List<SignalEntity> entities = signalRepository.findByDateRangeAndSignalType(startDate, endDate, "BUY", pageable);
        return convertToDomain(entities);
    }

    /**
     * Get signals by type
     * @param signalType signal type (BUY, SELL, HOLD)
     * @return List of signals of the specified type
     */
    public List<Signal> getSignalsByType(String signalType) {
        Pageable pageable = PageRequest.of(0, 100);
        List<SignalEntity> entities = signalRepository.findBuySignalsSince(LocalDate.now().minusDays(30), pageable);
        // Filter by the actual signalType parameter instead of hardcoding BUY
        List<Signal> signals = convertToDomain(entities);
        List<Signal> filtered = new ArrayList<>();
        for (Signal signal : signals) {
            if (signal.getType().toString().equals(signalType)) {
                filtered.add(signal);
            }
        }
        return filtered;
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
            if (signal.getConfidence() >= minConfidence) {
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
    public Signal generateSignal(String symbol) {
        // Delegate to SignalEngine for signal generation
        // This would typically trigger the strategy engine to analyze the symbol
        return signalEngine.generateSignal(symbol);
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
    private SignalType combineSignals(Signal technicalSignal, SentimentAnalysis sentiment) {
        SignalType technicalType = technicalSignal.getType();
        String sentimentScore = sentiment.getScore();

        if ("NEGATIVE".equals(sentimentScore)) {
            // Negative sentiment suppresses BUY signals
            if (technicalType == SignalType.BUY) {
                return SignalType.HOLD;
            }
        }

        return technicalType;
    }

    /**
     * Convert SignalEntity list to Signal domain objects
     */
    private List<Signal> convertToDomain(List<SignalEntity> entities) {
        List<Signal> signals = new ArrayList<>();
        for (SignalEntity entity : entities) {
            signals.add(new Signal(
                entity.getSymbol(),
                entity.getSignalType(),
                entity.getConfidence(),
                entity.getDate(),
                entity.getReasoning()
            ));
        }
        return signals;
    }

    // DTO classes for API responses

    public static class Signal {
        private final String symbol;
        private final SignalType type;
        private final Double confidence;
        private final LocalDate date;
        private final String reasoning;

        public Signal(String symbol, String type, Double confidence, LocalDate date, String reasoning) {
            this.symbol = symbol;
            this.type = SignalType.valueOf(type);
            this.confidence = confidence;
            this.date = date;
            this.reasoning = reasoning;
        }

        public String getSymbol() { return symbol; }
        public SignalType getType() { return type; }
        public Double getConfidence() { return confidence; }
        public LocalDate getDate() { return date; }
        public String getReasoning() { return reasoning; }
    }

    public enum SignalType {
        BUY, SELL, HOLD
    }

    public static class TechnicalAnalysis {
        private final String symbol;
        private final LocalDate date;
        private final List<String> indicators;
        private final double strength;

        public TechnicalAnalysis(String symbol, LocalDate date, List<String> indicators, double strength) {
            this.symbol = symbol;
            this.date = date;
            this.indicators = indicators;
            this.strength = strength;
        }

        public String getSymbol() { return symbol; }
        public LocalDate getDate() { return date; }
        public List<String> getIndicators() { return indicators; }
        public double getStrength() { return strength; }
    }

    public static class SentimentAnalysis {
        private final String symbol;
        private final LocalDate date;
        private final String score;
        private final String summary;

        public SentimentAnalysis(String symbol, LocalDate date, String score, String summary) {
            this.symbol = symbol;
            this.date = date;
            this.score = score;
            this.summary = summary;
        }

        public String getSymbol() { return symbol; }
        public LocalDate getDate() { return date; }
        public String getScore() { return score; }
        public String getSummary() { return summary; }
    }

    public static class CombinedSignal {
        private final String symbol;
        private final LocalDate date;
        private final Signal technicalSignal;
        private final SentimentAnalysis sentiment;
        private final SignalType finalSignal;

        public CombinedSignal(String symbol, LocalDate date, Signal technicalSignal,
                              SentimentAnalysis sentiment, SignalType finalSignal) {
            this.symbol = symbol;
            this.date = date;
            this.technicalSignal = technicalSignal;
            this.sentiment = sentiment;
            this.finalSignal = finalSignal;
        }

        public String getSymbol() { return symbol; }
        public LocalDate getDate() { return date; }
        public Signal getTechnicalSignal() { return technicalSignal; }
        public SentimentAnalysis getSentiment() { return sentiment; }
        public SignalType getFinalSignal() { return finalSignal; }
    }
}
