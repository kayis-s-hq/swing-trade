package com.swingtrade.api.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.llm.service.SentimentService;
import com.swingtrade.strategy.PriceActionSignalEngine;
import com.swingtrade.strategy.SignalResult;
import com.swingtrade.strategy.SwingTradingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine for generating and managing trading signals.
 * Runs scheduled analysis on market data and stores signals.
 */
@Component
public class SignalEngine {

    private static final Logger logger = LoggerFactory.getLogger(SignalEngine.class);

    private final CandleStore candleStore;
    private final SignalStore signalStore;
    private final SwingTradingStrategy strategy;
    private final SentimentService sentimentService;
    private final PriceActionSignalEngine priceActionSignalEngine;

    /**
     * Constructs SignalEngine with required dependencies.
     */
    public SignalEngine(CandleStore candleStore,
                        SignalStore signalStore,
                        SwingTradingStrategy strategy,
                        SentimentService sentimentService,
                        PriceActionSignalEngine priceActionSignalEngine) {
        this.candleStore = candleStore;
        this.signalStore = signalStore;
        this.strategy = strategy;
        this.sentimentService = sentimentService;
        this.priceActionSignalEngine = priceActionSignalEngine;
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
            List<String> symbols = candleStore.findAllDistinctSymbols();
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

                try {
                    generatePriceActionSignalForSymbol(symbol);
                } catch (Exception e) {
                    logger.error("Error generating price-action signal for {}: {}", symbol, e.getMessage());
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
        List<OhlcvCandle> candles = candleStore.findTopBySymbolOrderByDateDesc(symbol, 100);

        if (candles.size() < 50) {
            logger.debug("Not enough candles for {}: {} available", symbol, candles.size());
            return;
        }

        // Reverse to get chronological order
        List<OhlcvCandle> chronologicalCandles = new ArrayList<>(candles);
        java.util.Collections.reverse(chronologicalCandles);

        // Get latest candle date
        LocalDate latestDate = chronologicalCandles.get(chronologicalCandles.size() - 1).date();

        // Check if signal already exists for this date
        List<Signal> existingSignals = signalStore.findBySymbolAndDate(symbol, latestDate);
        if (!existingSignals.isEmpty()) {
            logger.debug("Signal already exists for {} on {}", symbol, latestDate);
            return;
        }

        // Generate signal
        Signal signal = strategy.analyze(chronologicalCandles);

        // NEW: Check sentiment before saving for BUY signals
        String warningFlag = "NONE";
        try {
            if (signal.type() == Signal.SignalType.BUY) {
                SentimentResult sentiment = sentimentService.analyzeStockSentiment(symbol, latestDate);

                if (sentiment.isNegative()) {
                    logger.info("Suppressing BUY signal for {} on {} due to NEGATIVE sentiment (reasoning: {})",
                               symbol, latestDate, sentiment.summary());
                    return; // Don't save - signal suppressed
                }

                if (sentiment.isNeutral()) {
                    logger.info("Saving NEUTRAL sentiment signal for {} on {} (reasoning: {})",
                               symbol, latestDate, sentiment.summary());
                    warningFlag = "NEUTRAL_SENTIMENT";
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to check sentiment for {} on {}: {}, saving signal anyway",
                       symbol, latestDate, e.getMessage());
            // Continue saving signal - sentiment check failure should not block signals
        }

        // Populate entryPrice/stopLoss/target from latest candle
        OhlcvCandle latestCandle = candleStore.findLatestBySymbol(symbol).orElse(null);
        BigDecimal entryPrice = null, stopLoss = null, target = null, rr = null;
        if (latestCandle != null && latestCandle.close() != null) {
            BigDecimal closePrice = latestCandle.close();
            BigDecimal atr = calculateATR(latestCandle, chronologicalCandles);
            stopLoss = closePrice.subtract(atr.multiply(BigDecimal.valueOf(2)));
            BigDecimal risk = closePrice.subtract(stopLoss);
            target = closePrice.add(risk.multiply(BigDecimal.valueOf(2.5)));
            rr = risk.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO : target.subtract(closePrice).divide(risk, 4, BigDecimal.ROUND_HALF_UP);
            entryPrice = closePrice;
        }

        // Save signal via Store (entity persistence handled internally)
        Signal savedSignal = Signal.create(symbol, latestDate, signal.type(), signal.confidence(),
                buildSignalReason(signal));
        // Set trading parameters via domain object recreation
        Signal finalSignal = new Signal(
                savedSignal.id(),
                savedSignal.symbol(),
                savedSignal.date(),
                savedSignal.type(),
                savedSignal.confidence(),
                savedSignal.reasoning(),
                entryPrice, stopLoss, target, rr,
                savedSignal.indicators(),
                savedSignal.generatedAt()
        );
        signalStore.save(finalSignal);

        logger.info("Generated {} signal for {} on {} (confidence: {}%, warning: {})",
                    signal.type(), symbol, latestDate, String.format("%.0f", signal.confidence().doubleValue() * 100), warningFlag);
    }

    /**
     * Generates a price-action signal for a specific stock using {@link PriceActionSignalEngine}
     * and persists it alongside the default strategy's signals, tagged with
     * {@link SignalEntity#STRATEGY_PRICE_ACTION}.
     *
     * @param symbol the stock symbol
     */
    @CacheEvict(value = {"latestSignal", "signals"}, key = "#symbol")
    @Transactional
    public void generatePriceActionSignalForSymbol(String symbol) {
        logger.debug("Generating price-action signal for {}", symbol);

        SignalResult result;
        try {
            result = priceActionSignalEngine.generateSignal(symbol);
        } catch (IllegalStateException e) {
            logger.debug("Not enough candles for price-action signal on {}: {}", symbol, e.getMessage());
            return;
        }

        List<Signal> existingSignals = signalStore.findBySymbolAndDate(symbol, result.date());
        if (!existingSignals.isEmpty()) {
            logger.debug("Price-action signal already exists for {} on {}", symbol, result.date());
            return;
        }

        BigDecimal confidence = result.type() == Signal.SignalType.BUY ? java.math.BigDecimal.ONE : java.math.BigDecimal.valueOf(0.5);
        Signal baseSignal = Signal.create(result.symbol(), result.date(), result.type(), confidence, result.reasoning());

        // Populate entryPrice/stopLoss/target from latest candle + ATR
        OhlcvCandle latestCandle = candleStore.findLatestBySymbol(result.symbol()).orElse(null);
        BigDecimal entryPrice = null, stopLoss = null, target = null, rr = null;
        if (latestCandle != null && latestCandle.close() != null) {
            BigDecimal closePrice = latestCandle.close();
            BigDecimal atr = BigDecimal.valueOf(result.atr());
            stopLoss = closePrice.subtract(atr.multiply(BigDecimal.valueOf(2)));
            BigDecimal risk = closePrice.subtract(stopLoss);
            target = closePrice.add(risk.multiply(BigDecimal.valueOf(2.5)));
            rr = risk.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO : target.subtract(closePrice).divide(risk, 4, BigDecimal.ROUND_HALF_UP);
            entryPrice = closePrice;
        }

        Signal finalSignal = new Signal(
                baseSignal.id(),
                baseSignal.symbol(),
                baseSignal.date(),
                baseSignal.type(),
                baseSignal.confidence(),
                baseSignal.reasoning(),
                entryPrice, stopLoss, target, rr,
                buildPriceActionIndicators(result),
                baseSignal.generatedAt()
        );
        signalStore.save(finalSignal);

        logger.info("Generated {} price-action signal for {} on {}", result.type(), symbol, result.date());
    }

    /**
     * Builds the comma-separated indicators string persisted alongside a price-action signal.
     *
     * @param result the price-action signal result
     * @return comma-separated "NAME=value" indicator readings
     */
    private String buildPriceActionIndicators(SignalResult result) {
        return String.format(java.util.Locale.ROOT, "RSI=%.2f,EMA20=%.2f,EMA50=%.2f,ATR=%.2f",
                result.rsi(), result.ema20(), result.ema50(), result.atr());
    }

    /**
     * Manually triggers price-action signal generation for a specific symbol.
     *
     * @param symbol the stock symbol
     */
    @Transactional
    public void generatePriceActionSignalForSymbolNow(String symbol) {
        logger.info("Manually generating price-action signal for {}", symbol);
        generatePriceActionSignalForSymbol(symbol);
    }

    // No conversion needed — Store returns domain objects directly

    /**
     * Builds a human-readable reason for the signal.
     *
     * @param signal the signal
     * @return reason string
     */
    private String buildSignalReason(Signal signal) {
        return switch (signal.type()) {
            case BUY -> "Technical indicators suggest bullish momentum";
            case SELL -> "Technical indicators suggest bearish momentum";
            case HOLD -> "No clear signal - maintain current position";
        };
    }

    /**
     * Calculates ATR from the last 14 candles for a symbol.
     */
    private BigDecimal calculateATR(OhlcvCandle latest, List<OhlcvCandle> candles) {
        if (candles.size() < 15) return BigDecimal.valueOf(0.02).multiply(latest.close());
        BigDecimal totalRange = BigDecimal.ZERO;
        int count = 0;
        for (int i = candles.size() - 14; i < candles.size(); i++) {
            OhlcvCandle c = candles.get(i);
            BigDecimal high = c.high();
            BigDecimal low = c.low();
            if (high != null && low != null) {
                totalRange = totalRange.add(high.subtract(low));
                count++;
            }
        }
        return count > 0 ? totalRange.divide(BigDecimal.valueOf(count), 4, BigDecimal.ROUND_HALF_UP)
                        : BigDecimal.valueOf(0.02).multiply(latest.close());
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
    public java.util.Optional<Signal> getLatestSignal(String symbol) {
        return signalStore.findLatestBySymbol(symbol);
    }

    /**
     * Gets all signals for a symbol.
     *
     * @param symbol the stock symbol
     * @return list of signals
     */
    @Cacheable(value = "signals", key = "#symbol")
    public List<Signal> getSignalsForSymbol(String symbol) {
        return signalStore.findBySymbol(symbol);
    }

    /**
     * Gets all buy signals generated since a date.
     *
     * @param sinceDate the start date
     * @return list of buy signals
     */
    public List<Signal> getBuySignalsSince(LocalDate sinceDate) {
        // Fetch all signals and filter by date in memory (Store returns domain objects)
        return signalStore.findAll().stream()
                .filter(s -> s.date() != null && !s.date().isBefore(sinceDate))
                .filter(s -> s.type() == Signal.SignalType.BUY)
                .toList();
    }
}
