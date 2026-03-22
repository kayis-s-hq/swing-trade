package com.swingtrade.strategy;

import com.swingtrade.domain.Signal;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.llm.service.SentimentAnalysisService;
import com.swingtrade.strategy.SwingTradingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Engine for generating and managing trading signals.
 * Runs scheduled analysis on market data and stores signals.
 */
@Component
public class SignalEngine {

    private static final Logger logger = LoggerFactory.getLogger(SignalEngine.class);

    private final OhlcvCandleRepository candleRepository;
    private final SignalRepository signalRepository;
    private final SwingTradingStrategy strategy;
    private final SentimentAnalysisService sentimentAnalysisService;

    /**
     * Constructs SignalEngine with required dependencies.
     */
    public SignalEngine(OhlcvCandleRepository candleRepository,
                        SignalRepository signalRepository,
                        SwingTradingStrategy strategy,
                        SentimentAnalysisService sentimentAnalysisService) {
        this.candleRepository = candleRepository;
        this.signalRepository = signalRepository;
        this.strategy = strategy;
        this.sentimentAnalysisService = sentimentAnalysisService;
    }

    /**
     * Scheduled job to generate signals for all stocks with data.
     * Runs at 17:00 IST (30 min after market close) on weekdays.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Kolkata")
    @Transactional
    public void generateDailySignals() {
        logger.info("Starting daily signal generation at {}", LocalDate.now(ZoneId.of("Asia/Kolkata")));

        try {
            // Get all distinct symbols with candle data
            List<String> symbols = candleRepository.findAllDistinctSymbols();
            logger.info("Found {} symbols with candle data", symbols.size());

            int processed = 0;
            int success = 0;
            int failures = 0;

            for (String symbol : symbols) {
                try {
                    generateSignalsForSymbol(symbol);
                    success++;
                } catch (Exception e) {
                    logger.error("Error generating signals for {}: {}", symbol, e.getMessage());
                    failures++;
                }
                processed++;

                // Progress logging
                if (processed % 10 == 0) {
                    logger.info("Progress: {}/{} symbols processed", processed, symbols.size());
                }
            }

            logger.info("Signal generation completed: {}/{} success, {} failures",
                        success, processed, failures);

        } catch (Exception e) {
            logger.error("Error during daily signal generation", e);
        }
    }

    /**
     * Generates signals for a specific stock.
     *
     * @param symbol the stock symbol
     */
    @CacheEvict(value = {"latestSignal", "signals"}, key = "#symbol")
    @Transactional
    public void generateSignalsForSymbol(String symbol) {
        logger.debug("Generating signals for {}", symbol);

        // Get recent candles (need at least 50 for EMA)
        List<OhlcvCandleEntity> candles = candleRepository.findTopBySymbolOrderByDateDesc(
                symbol,
                org.springframework.data.domain.PageRequest.of(0, 100)
        );

        if (candles.size() < 50) {
            logger.debug("Not enough candles for {}: {} available", symbol, candles.size());
            return;
        }

        // Reverse to get chronological order
        List<OhlcvCandleEntity> chronologicalCandles = new ArrayList<>(candles);
        java.util.Collections.reverse(chronologicalCandles);

        // Get latest candle date
        LocalDate latestDate = chronologicalCandles.get(chronologicalCandles.size() - 1).getDate();

        // Check if signal already exists for this date
        List<SignalEntity> existingSignals = signalRepository.findBySymbolAndDate(symbol, latestDate);
        if (!existingSignals.isEmpty()) {
            logger.debug("Signal already exists for {} on {}", symbol, latestDate);
            return;
        }

        // Generate signal
        List<com.swingtrade.domain.OhlcvCandle> domainCandles = chronologicalCandles.stream()
                .map(this::toDomainCandle)
                .collect(Collectors.toList());

        Signal signal = strategy.analyze(domainCandles);

        // Save signal
        SignalEntity signalEntity = new SignalEntity();
        signalEntity.setSymbol(symbol);
        signalEntity.setDate(latestDate);
        signalEntity.setSignalType(signal.type().toString());
        signalEntity.setConfidenceScore(signal.confidence());
        signalEntity.setReasoning(buildSignalReason(signal));

        signalRepository.save(signalEntity);

        logger.info("Generated {} signal for {} on {} (confidence: {:.2%})",
                    signal.type(), symbol, latestDate, signal.confidence());
    }

    /**
     * Converts OhlcvCandleEntity to domain OhlcvCandle.
     *
     * @param entity the JPA entity
     * @return domain object
     */
    private com.swingtrade.domain.OhlcvCandle toDomainCandle(OhlcvCandleEntity entity) {
        return new com.swingtrade.domain.OhlcvCandle(
                entity.getSymbol(),
                entity.getDate(),
                entity.getOpenPrice(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getClosePrice(),
                entity.getVolume(),
                entity.getAdjClosePrice()
        );
    }

    /**
     * Builds a human-readable reason for the signal.
     *
     * @param signal the signal
     * @return reason string
     */
    private String buildSignalReason(Signal signal) {
        // Note: The strategy returns signalType and confidence, but we need to capture factors
        // For now, provide a simple reason based on signal type
        return switch (signal.type()) {
            case BUY -> "Technical indicators suggest bullish momentum";
            case SELL -> "Technical indicators suggest bearish momentum";
            case HOLD -> "No clear signal - maintain current position";
        };
    }

    /**
     * Manually triggers signal generation for a specific symbol.
     *
     * @param symbol the stock symbol
     */
    @Cacheable(value = "latestSignal", key = "#symbol")
    @Transactional
    public void generateSignalForSymbolNow(String symbol) {
        logger.info("Manually generating signal for {}", symbol);
        generateSignalsForSymbol(symbol);
    }

    /**
     * Gets the latest signal for a symbol.
     *
     * @param symbol the stock symbol
     * @return latest signal or empty
     */
    @Cacheable(value = "latestSignal", key = "#symbol")
    public java.util.Optional<SignalEntity> getLatestSignal(String symbol) {
        return signalRepository.findLatestBySymbol(symbol);
    }

    /**
     * Gets all signals for a symbol.
     *
     * @param symbol the stock symbol
     * @return list of signals
     */
    @Cacheable(value = "signals", key = "#symbol")
    public List<SignalEntity> getSignalsForSymbol(String symbol) {
        return signalRepository.findBySymbolOrderByDateDesc(symbol,
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
    }

    /**
     * Gets all buy signals generated since a date.
     *
     * @param sinceDate the start date
     * @return list of buy signals
     */
    public List<SignalEntity> getBuySignalsSince(LocalDate sinceDate) {
        return signalRepository.findBuySignalsSince(sinceDate,
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
    }
}
