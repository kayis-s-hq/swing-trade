/*
 * Copyright 2026 Swing Trade
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swingtrade.api.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RiskCalculator;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.strategy.PriceActionSignalEngine;
import com.swingtrade.strategy.SignalResult;
import com.swingtrade.strategy.SwingTradingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic signal generation pipeline shared by both the primary swing strategy
 * and the price-action strategy.
 *
 * <p>Each method runs in its own {@code REQUIRES_NEW} transaction so a failure
 * for one symbol does not affect others in a batch.</p>
 */
@Service
public class SignalPipeline {

    private static final Logger logger = LoggerFactory.getLogger(SignalPipeline.class);
    private static final int MIN_CANDLES = 50;

    private final CandleStore candleStore;
    private final SwingTradingStrategy swingStrategy;
    private final PriceActionSignalEngine priceActionEngine;
    private final SignalPersistenceService persistenceService;
    private final SentimentGate sentimentGate;

    public SignalPipeline(CandleStore candleStore,
                          SwingTradingStrategy swingStrategy,
                          PriceActionSignalEngine priceActionEngine,
                          SignalPersistenceService persistenceService,
                          SentimentGate sentimentGate) {
        this.candleStore = candleStore;
        this.swingStrategy = swingStrategy;
        this.priceActionEngine = priceActionEngine;
        this.persistenceService = persistenceService;
        this.sentimentGate = sentimentGate;
    }

    /**
     * Generates a primary swing-trading signal for a symbol.
     *
     * <p>Pipeline: fetch candles -> check min count -> reverse to chronological
     * -> check dedup -> compute strategy signal -> check sentiment -> compute risk params -> save.</p>
     *
     * @param symbol the stock symbol
     * @return the saved signal, or empty if suppressed or skipped
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public java.util.Optional<Signal> generatePrimarySignal(String symbol) {
        logger.debug("Generating primary signal for {}", symbol);

        List<OhlcvCandle> candles = candleStore.findTopBySymbolOrderByDateDesc(symbol, 100);
        if (candles.size() < MIN_CANDLES) {
            logger.debug("Not enough candles for {}: {} available", symbol, candles.size());
            return java.util.Optional.empty();
        }

        List<OhlcvCandle> chronologicalCandles = new ArrayList<>(candles);
        Collections.reverse(chronologicalCandles);

        LocalDate latestDate = chronologicalCandles.get(chronologicalCandles.size() - 1).date();

        if (persistenceService.existsForDate(symbol, latestDate)) {
            logger.debug("Signal already exists for {} on {}", symbol, latestDate);
            return java.util.Optional.empty();
        }

        Signal signal = swingStrategy.analyze(chronologicalCandles);

        // Sentiment gate for BUY signals
        if (signal.type() == Signal.SignalType.BUY) {
            SentimentGate.SentimentVerdict verdict = sentimentGate.evaluate(symbol, latestDate);
            if (verdict.action() == SentimentGate.SentimentVerdict.Action.SUPPRESS) {
                return java.util.Optional.empty();
            }
            // FLAG_NEUTRAL and ALLOW/ALLOW_GRACEFUL continue to save
        }

        BigDecimal atr = RiskCalculator.calculateATR(chronologicalCandles);
        String indicators = buildSignalReason(signal);

        Signal saved = persistenceService.buildAndSave(
                symbol, latestDate, signal.type(), signal.confidence(),
                indicators, indicators, atr);

        logger.info("Generated {} signal for {} on {} (confidence: {}%, warning: {})",
                signal.type(), symbol, latestDate,
                String.format("%.0f", signal.confidence().doubleValue() * 100),
                warningFlag(signal, latestDate));

        return java.util.Optional.of(saved);
    }

    /**
     * Generates a price-action signal for a symbol using the {@link PriceActionSignalEngine}.
     *
     * @param symbol the stock symbol
     * @return the saved signal, or empty if skipped
     */
    public java.util.Optional<Signal> generatePriceActionSignal(String symbol) {
        logger.debug("Generating price-action signal for {}", symbol);

        SignalResult result;
        try {
            result = priceActionEngine.generateSignal(symbol);
        } catch (IllegalStateException e) {
            logger.debug("Not enough candles for price-action signal on {}: {}", symbol, e.getMessage());
            return java.util.Optional.empty();
        }

        BigDecimal confidence = result.type() == Signal.SignalType.BUY
                ? BigDecimal.ONE
                : BigDecimal.valueOf(0.5);
        Signal baseSignal = Signal.create(result.symbol(), result.date(), result.type(), confidence, result.reasoning());

        BigDecimal atr = BigDecimal.valueOf(result.atr());
        String indicators = buildPriceActionIndicators(result);

        Signal saved = persistenceService.buildAndSave(
                baseSignal, null, null, null, null, indicators);

        // Override the ATR-based risk params with the price-action engine's ATR
        saved = overrideRiskParams(saved, result);

        logger.info("Generated {} price-action signal for {} on {}", result.type(), result.symbol(), result.date());

        return java.util.Optional.of(saved);
    }

    // ---- Private helpers ----

    private String buildSignalReason(Signal signal) {
        return switch (signal.type()) {
            case BUY -> "Technical indicators suggest bullish momentum";
            case SELL -> "Technical indicators suggest bearish momentum";
            case HOLD -> "No clear signal - maintain current position";
        };
    }

    private String buildPriceActionIndicators(SignalResult result) {
        return String.format(
                "RSI=%.2f,EMA20=%.2f,EMA50=%.2f,ATR=%.2f",
                result.rsi(), result.ema20(), result.ema50(), result.atr());
    }

    private String warningFlag(Signal signal, LocalDate date) {
        if (signal.type() != Signal.SignalType.BUY) {
            return "NONE";
        }
        try {
            SentimentGate.SentimentVerdict verdict = sentimentGate.evaluate(signal.symbol(), date);
            return switch (verdict.action()) {
                case FLAG_NEUTRAL -> "NEUTRAL_SENTIMENT";
                case ALLOW_GRACEFUL -> "SENTIMENT_ERROR";
                default -> "NONE";
            };
        } catch (Exception e) {
            return "NONE";
        }
    }

    /**
     * Overrides the risk parameters on a signal with explicit values.
     * Used by the price-action pipeline to inject its own ATR-based values.
     */
    private Signal overrideRiskParams(Signal signal, SignalResult result) {
        OhlcvCandle latestCandle = candleStore.findLatestBySymbol(signal.symbol()).orElse(null);
        if (latestCandle == null || latestCandle.close() == null) {
            return signal;
        }
        BigDecimal closePrice = latestCandle.close();
        BigDecimal atr = BigDecimal.valueOf(result.atr());
        BigDecimal stopLoss = RiskCalculator.calculateStopLoss(closePrice, atr);
        BigDecimal target = RiskCalculator.calculateTarget(closePrice, atr);
        BigDecimal riskReward = RiskCalculator.calculateRiskReward(stopLoss, target, closePrice);

        return new Signal(
                signal.id(),
                signal.symbol(),
                signal.date(),
                signal.type(),
                signal.confidence(),
                signal.reasoning(),
                closePrice,
                stopLoss,
                target,
                riskReward,
                signal.indicators(),
                signal.generatedAt()
        );
    }
}