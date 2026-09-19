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
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.ExitReason;
import com.swingtrade.strategy.GateEvaluator;
import com.swingtrade.strategy.GateOutcome;
import com.swingtrade.strategy.MarketContext;
import com.swingtrade.strategy.OverlayConfigResolver;
import com.swingtrade.strategy.PriceActionSignalEngine;
import com.swingtrade.strategy.RegimeGateConfig;
import com.swingtrade.strategy.RuleOutcome;
import com.swingtrade.strategy.SentimentGateConfig;
import com.swingtrade.strategy.SignalResult;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyDecision;
import com.swingtrade.strategy.StrategyParamsView;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final StrategyConfigStore strategyConfigStore;
    private final StrategyTypeRegistry strategyTypeRegistry;
    private final GateEvaluator gateEvaluator;

    public SignalPipeline(CandleStore candleStore,
                          PriceActionSignalEngine priceActionEngine,
                          SignalPersistenceService persistenceService,
                          SentimentGate sentimentGate,
                          PositionStore positionStore,
                          PositionService positionService,
                          StrategyConfigStore strategyConfigStore,
                          StrategyTypeRegistry strategyTypeRegistry,
                          GateEvaluator gateEvaluator) {
        this.candleStore = candleStore;
        this.priceActionEngine = priceActionEngine;
        this.persistenceService = persistenceService;
        this.sentimentGate = sentimentGate;
        this.positionStore = positionStore;
        this.positionService = positionService;
        this.strategyConfigStore = strategyConfigStore;
        this.strategyTypeRegistry = strategyTypeRegistry;
        this.gateEvaluator = gateEvaluator;
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

        // Clear any stale processed signals for this symbol/date so retries can regenerate.
        // Uses SignalEntity.STRATEGY_DEFAULT (now "BREAKOUT_STRICT", not the literal "DEFAULT" -
        // see V47's migration comment and SignalEntity's Javadoc) so this delete stays in sync
        // with whatever value the entity's default field/new writes actually use.
        persistenceService.deleteBySymbolAndDateAndStrategy(symbol, latestDate, SignalEntity.STRATEGY_DEFAULT);

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

        BigDecimal atr = BigDecimal.valueOf(result.atr());
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

    /**
     * True if any variant is currently active (SHADOW/CHAMPION), used by
     * {@code JobOrchestratorService} to keep the pre-multi-strategy "always run sentiment"
     * behaviour when the feature is entirely unused (plan §7.1 item 5).
     */
    public boolean hasActiveVariants() {
        return strategyConfigStore.findAllCurrent().stream().anyMatch(StrategyConfig::isActive);
    }

    /**
     * Evaluates every active (SHADOW/CHAMPION) {@link StrategyConfig} variant for {@code symbol}
     * against one shared {@link MarketContext} (built once, not per variant) and persists one
     * {@code signals} row per variant - plan §7.1.
     *
     * <p>Table-growth control: non-HOLD rows are persisted for SHADOW variants; CHAMPION
     * persists every row, including HOLD, for a full audit trail. Idempotency key is
     * (symbol, date, strategy=variantId, strategy_version) - replaces the F5
     * (symbol, date, strategy) key, which cannot disambiguate two versions of the same variant.
     *
     * <p>Per-variant failure isolation: one variant throwing (bad params, missing indicator,
     * gate error) is logged and skipped; it never prevents other variants - or this method as a
     * whole - from completing for the symbol.
     *
     * <p><b>Simplifications (see phase report):</b> (1) {@code regimeGate} is evaluated with no
     * live index series wired in this phase (see {@link com.swingtrade.strategy.IndexSeries}'s
     * javadoc) - {@link GateEvaluator#applyRegimeGate} treats a {@code null} series as "skip,
     * pass", so the gate is a documented no-op until Nifty data is ingested. (2) {@code
     * sentimentGate} cannot be fully applied here because the SENTIMENT stage runs strictly
     * after SIGNAL in the job pipeline (see {@code JobOrchestratorService}) - a BUY from a
     * variant with {@code sentimentGate} enabled is persisted with the same
     * {@code PENDING_SENTIMENT} warning-flag convention the legacy engine uses, and a
     * {@code sentimentGate} outcome recorded as a deferred pass; real per-variant blocking based
     * on the actual sentiment result is left to the paper-trading phase (plan §7.2), which is out
     * of scope here. (3) this variant fan-out runs in addition to, not instead of, the legacy
     * {@link #generatePrimarySignal} call - no variant here replaces the existing
     * {@code BREAKOUT_STRICT} production signal; cutting a CHAMPION variant over to be the
     * production signal is a later-phase promotion concern (plan §7.4), not this method's job.
     *
     * @param symbol the stock symbol
     * @return one outcome per active variant that was evaluated (variants that failed entirely -
     *     e.g. an unknown strategy type - do not appear at all rather than a partial entry)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<VariantSignalOutcome> generateVariantSignals(String symbol) {
        List<StrategyConfig> activeConfigs = strategyConfigStore.findAllCurrent().stream()
            .filter(StrategyConfig::isActive)
            .toList();
        if (activeConfigs.isEmpty()) {
            return List.of();
        }

        List<OhlcvCandle> candles = candleStore.findTopBySymbolOrderByDateDesc(symbol, 100);
        if (candles.size() < MIN_CANDLES) {
            logger.debug("Not enough candles for {} to evaluate variants: {} available", symbol, candles.size());
            return List.of();
        }
        List<OhlcvCandle> chronologicalCandles = new ArrayList<>(candles);
        Collections.reverse(chronologicalCandles);
        LocalDate latestDate = chronologicalCandles.get(chronologicalCandles.size() - 1).date();

        MarketContext ctx;
        try {
            ctx = MarketContext.of(symbol, chronologicalCandles);
        } catch (RuntimeException e) {
            logger.warn("Could not build MarketContext for {}: {}", symbol, e.getMessage());
            return List.of();
        }
        int barIndex = ctx.barCount() - 1;

        List<VariantSignalOutcome> outcomes = new ArrayList<>();
        for (StrategyConfig config : activeConfigs) {
            try {
                outcomes.add(evaluateAndPersistVariant(symbol, latestDate, ctx, barIndex, config));
            } catch (Exception e) {
                logger.error("Variant {} v{} ({}) failed for {} on {}: {}",
                    config.variantId(), config.version(), config.strategyType(), symbol, latestDate,
                    e.getMessage(), e);
            }
        }
        return outcomes;
    }

    private VariantSignalOutcome evaluateAndPersistVariant(String symbol, LocalDate date, MarketContext ctx,
                                                            int barIndex, StrategyConfig config) {
        SignalStrategy strategy = strategyTypeRegistry.findByType(config.strategyType())
            .orElseThrow(() -> new IllegalStateException("Unknown strategy type: " + config.strategyType()));
        StrategyParamsView params = StrategyParamsView.of(config.params());
        StrategyDecision decision = strategy.evaluateEntry(ctx, barIndex, params);

        Optional<RegimeGateConfig> regimeGateConfig = OverlayConfigResolver.regimeGate(config.overlays());
        Optional<SentimentGateConfig> sentimentGateConfig = OverlayConfigResolver.sentimentGate(config.overlays());

        List<GateOutcome> gateOutcomes = new ArrayList<>();
        StrategyDecision afterGates = decision;
        if (regimeGateConfig.isPresent()) {
            GateEvaluator.GateResult regimeResult = gateEvaluator.applyRegimeGate(
                afterGates, date, null, regimeGateConfig.get());
            afterGates = regimeResult.decision();
            gateOutcomes.addAll(regimeResult.outcomes());
        }

        boolean sentimentGateEnabled = sentimentGateConfig.isPresent();
        String warningFlag = SignalEntity.WarningFlag.NONE.code();
        if (afterGates.type() == Signal.SignalType.BUY && sentimentGateEnabled) {
            gateOutcomes.add(GateOutcome.pass("sentimentGate",
                "Sentiment not yet available at SIGNAL stage; deferred to a later stage"));
            warningFlag = SignalEntity.WarningFlag.PENDING_SENTIMENT.code();
        }

        boolean isChampion = config.mode() == StrategyMode.CHAMPION;
        boolean shouldPersist = isChampion || afterGates.type() != Signal.SignalType.HOLD;

        if (shouldPersist) {
            persistenceService.deleteBySymbolAndDateAndStrategyAndVersion(
                symbol, date, config.variantId(), config.version());
            // BUY signals carry their suggested stop/target through to persistence (plan §7.4
            // gap-fill) - paper-trade exit evaluation for the resulting shadow position needs
            // them later and cannot recompute them without re-running the strategy.
            Signal baseSignal = afterGates.type() == Signal.SignalType.BUY
                ? Signal.createWithLevels(symbol, date, afterGates.type(), afterGates.score(),
                    afterGates.reasoning(), ctx.view(barIndex).close(),
                    afterGates.suggestedStop(), afterGates.suggestedTarget())
                : Signal.create(symbol, date, afterGates.type(), afterGates.score(), afterGates.reasoning());
            persistenceService.saveVariantSignal(baseSignal, warningFlag, config.variantId(), config.version(),
                afterGates.score(), toOutcomeMaps(afterGates.rules()), toGateOutcomeMaps(gateOutcomes));
        }

        return new VariantSignalOutcome(config.variantId(), config.version(), afterGates.type(),
            sentimentGateEnabled, shouldPersist, afterGates.score());
    }

    private List<Map<String, Object>> toOutcomeMaps(List<RuleOutcome> rules) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RuleOutcome rule : rules) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", rule.key());
            map.put("passed", rule.passed());
            map.put("weight", rule.weight());
            map.put("mandatory", rule.mandatory());
            map.put("detail", rule.detail());
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> toGateOutcomeMaps(List<GateOutcome> gates) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GateOutcome gate : gates) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("gate", gate.gate());
            map.put("blocked", gate.blocked());
            map.put("reason", gate.reason());
            map.put("score", gate.score());
            result.add(map);
        }
        return result;
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
        double rsiCenter = 57.5;
        double rsiMargin = result.type() == Signal.SignalType.BUY
            ? clamp(1.0 - Math.abs(result.rsi() - rsiCenter) / 7.5)
            : clamp((50.0 - result.rsi()) / 20.0);
        double emaSpread = result.ema50() == 0.0 ? 0.0
            : (result.type() == Signal.SignalType.BUY
                ? (result.ema20() - result.ema50()) / Math.abs(result.ema50())
                : (result.ema50() - result.ema20()) / Math.abs(result.ema50()));
        double trendMargin = clamp(emaSpread * 20.0);
        double confidence = switch (result.type()) {
            case BUY -> 0.55 + 0.25 * rsiMargin + 0.20 * trendMargin;
            case SELL -> 0.45 + 0.30 * rsiMargin + 0.25 * trendMargin;
            case HOLD -> 0.20 + 0.10 * trendMargin;
        };
        return BigDecimal.valueOf(clamp(confidence)).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * One active variant's evaluation outcome for a symbol/day (plan §7.1), used by
     * {@code JobOrchestratorService} to decide whether the SENTIMENT stage needs to run.
     *
     * @param variantId              the variant's stable id (persisted as {@code signals.strategy})
     * @param strategyVersion        the variant's version number
     * @param type                   the final (post-gate) decision type
     * @param sentimentGateEnabled   true if this variant's overlays enable {@code sentimentGate}
     * @param persisted              true if a signals row was actually written (false when a
     *                               SHADOW variant's HOLD was suppressed for table-growth control)
     * @param confidence             the decision score in [0,1], used for signal arbitration
     */
    public record VariantSignalOutcome(String variantId, int strategyVersion, Signal.SignalType type,
                                        boolean sentimentGateEnabled, boolean persisted,
                                        java.math.BigDecimal confidence) {
    }

}
