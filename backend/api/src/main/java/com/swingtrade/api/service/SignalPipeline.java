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
    private final PriceActionSignalEngine priceActionEngine;
    private final SignalPersistenceService persistenceService;
    private final SentimentGate sentimentGate;

    public SignalPipeline(CandleStore candleStore,
                          PriceActionSignalEngine priceActionEngine,
                          SignalPersistenceService persistenceService,
                          SentimentGate sentimentGate) {
        this.candleStore = candleStore;
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

        // Clear any stale processed signals for this symbol/date so retries can regenerate
        persistenceService.deleteBySymbolAndDate(symbol, latestDate);

        SignalResult result;
        try {
            result = priceActionEngine.analyze(symbol, chronologicalCandles);
        } catch (IllegalStateException e) {
            logger.debug("Not enough candles for {}: {}", symbol, e.getMessage());
            return java.util.Optional.empty();
        }

        Signal signal = Signal.create(result.symbol(), result.date(), result.type(),
                BigDecimal.ONE, result.reasoning());

        SentimentGate.SentimentVerdict verdict = SentimentGate.SentimentVerdict.allow();
        String sentimentScore = null;
        String sentimentReasoning = null;
        if (result.type() == Signal.SignalType.BUY) {
            verdict = sentimentGate.evaluate(symbol, latestDate);
            sentimentReasoning = verdict.reason();
            if (verdict.action() == SentimentGate.SentimentVerdict.Action.SUPPRESS) {
                return java.util.Optional.empty();
            }
            sentimentScore = switch (verdict.action()) {
                case FLAG_NEUTRAL -> "NEUTRAL";
                case ALLOW_GRACEFUL -> "UNKNOWN";
                default -> "POSITIVE";
            };
        }

        BigDecimal atr = RiskCalculator.calculateATR(chronologicalCandles);
        String indicators = buildPriceActionIndicators(result);

        String warningFlag = warningFlag(result, latestDate);
        Signal saved = persistenceService.buildAndSaveWithWarning(
                symbol, latestDate, result.type(), BigDecimal.ONE,
                indicators, indicators, atr, warningFlag, sentimentScore, sentimentReasoning);

        logger.info("Generated {} signal for {} on {} (reasoning: {})",
                result.type(), symbol, latestDate, result.reasoning());

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

        SentimentGate.SentimentVerdict verdict = SentimentGate.SentimentVerdict.allow();
        String sentimentScore = null;
        String sentimentReasoning = null;
        if (result.type() == Signal.SignalType.BUY) {
            verdict = sentimentGate.evaluate(symbol, result.date());
            sentimentReasoning = verdict.reason();
            if (verdict.action() == SentimentGate.SentimentVerdict.Action.SUPPRESS) {
                return java.util.Optional.empty();
            }
            sentimentScore = switch (verdict.action()) {
                case FLAG_NEUTRAL -> "NEUTRAL";
                case ALLOW_GRACEFUL -> "UNKNOWN";
                default -> "POSITIVE";
            };
        }

        BigDecimal confidence = result.type() == Signal.SignalType.BUY
                ? BigDecimal.ONE
                : BigDecimal.valueOf(0.5);
        Signal baseSignal = Signal.create(result.symbol(), result.date(), result.type(), confidence, result.reasoning());

        BigDecimal atr = BigDecimal.valueOf(result.atr());
        String indicators = buildPriceActionIndicators(result);

        // Calculate risk params upfront so they are saved in a single DB write
        OhlcvCandle latestCandle = candleStore.findLatestBySymbol(result.symbol()).orElse(null);
        BigDecimal entryPrice = null, stopLoss = null, target = null, riskReward = null;
        if (latestCandle != null && latestCandle.close() != null) {
            BigDecimal closePrice = latestCandle.close();
            stopLoss = RiskCalculator.calculateStopLoss(closePrice, atr);
            target = RiskCalculator.calculateTarget(closePrice, atr);
            riskReward = RiskCalculator.calculateRiskReward(stopLoss, target, closePrice);
            entryPrice = closePrice;
        }

        Signal saved = persistenceService.buildAndSave(
                baseSignal, entryPrice, stopLoss, target, riskReward, indicators, sentimentScore, sentimentReasoning);

        logger.info("Generated {} price-action signal for {} on {}", result.type(), result.symbol(), result.date());

        return java.util.Optional.of(saved);
    }

    // ---- Private helpers ----

    private String buildPriceActionIndicators(SignalResult result) {
        return String.format(
                "RSI=%.2f,EMA20=%.2f,EMA50=%.2f,ATR=%.2f",
                result.rsi(), result.ema20(), result.ema50(), result.atr());
    }

    private String warningFlag(SignalResult result, LocalDate date) {
        if (result.type() != Signal.SignalType.BUY) {
            return "NONE";
        }
        try {
            SentimentGate.SentimentVerdict verdict = sentimentGate.evaluate(result.symbol(), date);
            return switch (verdict.action()) {
                case FLAG_NEUTRAL -> "NEUTRAL_SENTIMENT";
                case ALLOW_GRACEFUL -> "SENTIMENT_ERROR";
                default -> "NONE";
            };
        } catch (Exception e) {
            return "NONE";
        }
    }

    }