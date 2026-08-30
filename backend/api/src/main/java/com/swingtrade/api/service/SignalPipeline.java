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

import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RiskCalculator;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.strategy.ExitReason;
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
    private final PositionStore positionStore;
    private final PositionService positionService;

    public SignalPipeline(CandleStore candleStore,
                          PriceActionSignalEngine priceActionEngine,
                          SignalPersistenceService persistenceService,
                          SentimentGate sentimentGate,
                          PositionStore positionStore,
                          PositionService positionService) {
        this.candleStore = candleStore;
        this.priceActionEngine = priceActionEngine;
        this.persistenceService = persistenceService;
        this.sentimentGate = sentimentGate;
        this.positionStore = positionStore;
        this.positionService = positionService;
    }

    /**
     * Generates a primary swing-trading signal for a symbol.
     *
     * <p>Pipeline: fetch candles -> check min count -> reverse to chronological
     * -> check dedup -> compute strategy signal -> compute risk params -> save.</p>
     *
     * <p>Sentiment is deliberately NOT evaluated here. Every technical BUY is persisted
     * unconditionally with a {@code PENDING_SENTIMENT} warning flag; the JobOrchestrator's
     * SENTIMENT stage runs afterward (only for a BUY, not every symbol every day) and its
     * verdict is read by the PAPER_TRADE stage before a trade is actually executed. This
     * gives a full audit trail ("a real BUY signal fired, sentiment later blocked the
     * trade") instead of a sentiment-suppressed BUY silently never existing in the
     * `signals` table at all, which was this method's previous behavior.</p>
     *
     * @param symbol the stock symbol
     * @return the saved signal, or empty if skipped (not enough candle history)
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
        persistenceService.deleteBySymbolAndDateAndStrategy(symbol, latestDate, "DEFAULT");

        SignalResult result;
        try {
            result = priceActionEngine.analyze(symbol, chronologicalCandles);
        } catch (IllegalStateException e) {
            logger.debug("Not enough candles for {}: {}", symbol, e.getMessage());
            return java.util.Optional.empty();
        }

        if (result.type() == Signal.SignalType.SELL) {
            closeHeldPositionOnSell(symbol, latestDate);
        }

        Signal signal = Signal.create(result.symbol(), result.date(), result.type(),
                BigDecimal.ONE, result.reasoning());

        BigDecimal atr = RiskCalculator.calculateATR(chronologicalCandles);
        String indicators = buildPriceActionIndicators(result);

        // Sentiment score/reasoning are filled in later by the SENTIMENT stage's own
        // write path (see JobOrchestratorService), not here.
        String warningFlag = (result.type() == Signal.SignalType.BUY
            ? SignalEntity.WarningFlag.PENDING_SENTIMENT
            : SignalEntity.WarningFlag.NONE).code();
        Signal saved = persistenceService.buildAndSaveWithWarning(
                symbol, latestDate, result.type(), BigDecimal.ONE,
                result.reasoning(), indicators, atr, warningFlag, null, null);

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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public java.util.Optional<Signal> generatePriceActionSignal(String symbol) {
        logger.debug("Generating price-action signal for {}", symbol);

        SignalResult result;
        try {
            result = priceActionEngine.generateSignal(symbol);
        } catch (IllegalStateException e) {
            logger.debug("Not enough candles for price-action signal on {}: {}", symbol, e.getMessage());
            return java.util.Optional.empty();
        }

        if (result.type() == Signal.SignalType.SELL) {
            closeHeldPositionOnSell(symbol, result.date());
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

    /**
     * Closes any held position for {@code symbol} on a SELL signal. Shared by
     * both the primary and price-action pipelines so a SELL from either
     * strategy actually exits a live position, not just one of them.
     */
    private void closeHeldPositionOnSell(String symbol, LocalDate date) {
        boolean held = positionStore.findBySymbol(symbol).isPresent();
        if (held) {
            try {
                positionService.closePosition(symbol, ExitReason.SIGNAL_EXIT.name());
                logger.info("SELL signal closed held position for {} on {} (reason={})", symbol, date,
                    ExitReason.SIGNAL_EXIT.name());
            } catch (Exception e) {
                logger.warn("SELL signal for {} on {} failed to close held position - signal still "
                    + "persisted for audit; position remains open: {}", symbol, date, e.getMessage());
            }
        } else {
            logger.debug("SELL signal for {} on {} - no held position; persisting informational "
                + "SELL signal only", symbol, date);
        }
    }

    private String buildPriceActionIndicators(SignalResult result) {
        return String.format(
                "RSI=%.2f,EMA20=%.2f,EMA50=%.2f,ATR=%.2f",
                result.rsi(), result.ema20(), result.ema50(), result.atr());
    }

}
