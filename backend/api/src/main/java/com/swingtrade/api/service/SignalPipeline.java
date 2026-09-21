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
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.strategy.ExitReason;
import com.swingtrade.strategy.LiveSignalEvaluator;
import com.swingtrade.strategy.PriceActionSignalEngine;
import com.swingtrade.strategy.ResolvedStrategy;
import com.swingtrade.strategy.SignalResult;
import com.swingtrade.strategy.StrategyDecision;
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
    /** Candle window for SignalStrategy live evaluation; matches the backtest history window. */
    private static final int LIVE_CANDLE_WINDOW = 1000;
    private static final java.math.MathContext CONFIDENCE_MC = java.math.MathContext.DECIMAL64;
    private static final BigDecimal RSI_CENTER = new BigDecimal("57.5");
    private static final BigDecimal RSI_HALF_BAND = new BigDecimal("7.5");
    private static final BigDecimal FIFTY = BigDecimal.valueOf(50);
    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);

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
     * SENTIMENT stage runs afterward for every symbol and its verdict is read by the
     * PAPER_TRADE stage before a BUY trade is actually executed. This
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

        BigDecimal confidence = deriveConfidence(result);
        Signal signal = Signal.create(result.symbol(), result.date(), result.type(),
                confidence, result.reasoning());

        BigDecimal atr = result.atr();
        String indicators = buildPriceActionIndicators(result);

        // Sentiment score/reasoning are filled in later by the SENTIMENT stage's own
        // write path (see JobOrchestratorService), not here.
        String warningFlag = (result.type() == Signal.SignalType.BUY
            ? SignalEntity.WarningFlag.PENDING_SENTIMENT
            : SignalEntity.WarningFlag.NONE).code();
        Signal saved = persistenceService.buildAndSaveWithWarning(
                symbol, latestDate, result.type(), confidence,
                result.reasoning(), indicators, atr, warningFlag, null, null);

        logger.info("Generated {} signal for {} on {} (reasoning: {})",
                result.type(), symbol, latestDate, result.reasoning());

        return java.util.Optional.of(saved);
    }

    /**
     * Generates one configured live signal. A shadow signal is persisted for
     * audit/analysis but is never allowed to close a position; only the
     * champion is trade-authoritative.
     *
     * <p>{@link ResolvedStrategy.Signal} variants are evaluated through
     * {@link LiveSignalEvaluator} - the same {@code evaluateEntry} the backtest calls - and only a
     * BUY decision is persisted (their exits are decided by {@code evaluateExit} against open
     * positions, not by SELL signals). Legacy strategies keep the previous engine path. Every
     * outcome, including a skip, is reported via {@link ConfiguredEvaluation}.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfiguredEvaluation generateConfiguredSignal(String symbol, StrategyConfig config,
                                                         ResolvedStrategy resolved, boolean champion) {
        return generateConfiguredSignal(symbol, config, resolved, champion, false);
    }

    /**
     * As {@link #generateConfiguredSignal(String, StrategyConfig, ResolvedStrategy, boolean)}; with
     * {@code dryRun} the strategy is evaluated and the outcome reported, but nothing is persisted
     * (the returned signal, if any, is transient with a {@code null} id) and no positions are closed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfiguredEvaluation generateConfiguredSignal(String symbol, StrategyConfig config,
                                                         ResolvedStrategy resolved, boolean champion,
                                                         boolean dryRun) {
        if (config == null || resolved == null) {
            throw new IllegalArgumentException("Configured signal requires a config and resolved strategy");
        }
        return switch (resolved) {
            case ResolvedStrategy.Unresolved unresolved -> ConfiguredEvaluation.skipped(unresolved.reason());
            case ResolvedStrategy.Legacy legacy ->
                generateLegacyConfiguredSignal(symbol, config, legacy, champion, dryRun);
            case ResolvedStrategy.Signal signalStrategy ->
                generateSignalStrategySignal(symbol, config, signalStrategy, dryRun);
        };
    }

    private ConfiguredEvaluation generateLegacyConfiguredSignal(String symbol, StrategyConfig config,
                                                                ResolvedStrategy.Legacy legacy,
                                                                boolean champion, boolean dryRun) {
        SignalResult result;
        try {
            result = priceActionEngine.generateSignal(symbol, legacy.strategy());
        } catch (IllegalStateException e) {
            logger.debug("Not enough candles for configured signal on {}: {}", symbol, e.getMessage());
            return ConfiguredEvaluation.skipped("insufficient candle history: " + e.getMessage());
        }

        if (champion && !dryRun && result.type() == Signal.SignalType.SELL) {
            closeHeldPositionOnSell(symbol, result.date());
        }
        BigDecimal confidence = deriveConfidence(result);
        if (dryRun) {
            return ConfiguredEvaluation.evaluated(
                Signal.create(symbol, result.date(), result.type(), confidence, result.reasoning()),
                confidence, result.reasoning());
        }
        String warningFlag = result.type() == Signal.SignalType.BUY
            ? SignalEntity.WarningFlag.PENDING_SENTIMENT.code() : SignalEntity.WarningFlag.NONE.code();
        Signal saved = persistenceService.buildAndSaveWithWarning(
            symbol, result.date(), result.type(), confidence, result.reasoning(),
            buildPriceActionIndicators(result), result.atr(), warningFlag,
            null, null, config.variantId(), config.version());
        return ConfiguredEvaluation.evaluated(saved, confidence, result.reasoning());
    }

    private ConfiguredEvaluation generateSignalStrategySignal(String symbol, StrategyConfig config,
                                                              ResolvedStrategy.Signal resolved,
                                                              boolean dryRun) {
        List<OhlcvCandle> descending = candleStore.findTopBySymbolOrderByDateDesc(symbol, LIVE_CANDLE_WINDOW);
        List<OhlcvCandle> chronological = new ArrayList<>(descending);
        Collections.reverse(chronological);

        var evaluation = LiveSignalEvaluator.evaluateEntry(symbol, chronological, resolved);
        if (evaluation.isEmpty()) {
            return ConfiguredEvaluation.skipped("insufficient candle history: " + chronological.size()
                + " candles, strategy needs more than " + resolved.strategy().warmupBars(resolved.params()));
        }
        StrategyDecision decision = evaluation.get().decision();
        if (decision.type() != Signal.SignalType.BUY) {
            return ConfiguredEvaluation.evaluated(null, decision.score(), decision.reasoning());
        }

        BigDecimal entry = evaluation.get().close();
        BigDecimal stop = decision.suggestedStop();
        BigDecimal target = decision.suggestedTarget();
        BigDecimal riskReward = stop != null && target != null
            ? RiskCalculator.calculateRiskReward(stop, target, entry) : null;
        BigDecimal confidence = clamp(decision.score()).setScale(4, java.math.RoundingMode.HALF_UP);
        if (dryRun) {
            return ConfiguredEvaluation.evaluated(Signal.create(symbol, evaluation.get().date(),
                Signal.SignalType.BUY, confidence, decision.reasoning()), decision.score(), decision.reasoning());
        }
        Signal saved = persistenceService.saveConfiguredSignal(symbol, evaluation.get().date(),
            Signal.SignalType.BUY, confidence, decision.reasoning(), describeRules(decision), entry, stop,
            target, riskReward, SignalEntity.WarningFlag.PENDING_SENTIMENT.code(), config.variantId(),
            config.version(), config.paramsHash());
        return ConfiguredEvaluation.evaluated(saved, decision.score(), decision.reasoning());
    }

    private static String describeRules(StrategyDecision decision) {
        return decision.rules().stream()
            .map(rule -> rule.key() + "=" + (rule.passed() ? "pass" : "fail"))
            .collect(java.util.stream.Collectors.joining(","));
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

        BigDecimal confidence = deriveConfidence(result);
        Signal baseSignal = Signal.create(result.symbol(), result.date(), result.type(), confidence, result.reasoning());

        BigDecimal atr = result.atr();
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

    /**
     * Converts rule margins into a bounded confidence instead of assigning every signal a fixed
     * conviction. RSI location and EMA separation are stable, explainable inputs available on the
     * signal result itself; the strategy still decides BUY/SELL/HOLD independently.
     */
    private BigDecimal deriveConfidence(SignalResult result) {
        boolean buy = result.type() == Signal.SignalType.BUY;
        BigDecimal rsiMargin = buy
            ? clamp(BigDecimal.ONE.subtract(
                result.rsi().subtract(RSI_CENTER).abs().divide(RSI_HALF_BAND, CONFIDENCE_MC)))
            : clamp(FIFTY.subtract(result.rsi()).divide(TWENTY, CONFIDENCE_MC));
        BigDecimal emaSpread = result.ema50().signum() == 0 ? BigDecimal.ZERO
            : (buy ? result.ema20().subtract(result.ema50()) : result.ema50().subtract(result.ema20()))
                .divide(result.ema50().abs(), CONFIDENCE_MC);
        BigDecimal trendMargin = clamp(emaSpread.multiply(TWENTY));
        BigDecimal confidence = switch (result.type()) {
            case BUY -> new BigDecimal("0.55").add(new BigDecimal("0.25").multiply(rsiMargin))
                .add(new BigDecimal("0.20").multiply(trendMargin));
            case SELL -> new BigDecimal("0.45").add(new BigDecimal("0.30").multiply(rsiMargin))
                .add(new BigDecimal("0.25").multiply(trendMargin));
            case HOLD -> new BigDecimal("0.20").add(new BigDecimal("0.10").multiply(trendMargin));
        };
        return clamp(confidence).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

}
