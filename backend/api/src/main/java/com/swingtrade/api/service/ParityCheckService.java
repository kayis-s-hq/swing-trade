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

import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.data.entity.StrategyExperimentLogEntity;
import com.swingtrade.data.repository.StrategyExperimentLogRepository;
import com.swingtrade.data.service.MarketCalendar;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.GateEvaluator;
import com.swingtrade.strategy.MarketContext;
import com.swingtrade.strategy.OverlayConfigResolver;
import com.swingtrade.strategy.RegimeGateConfig;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyDecision;
import com.swingtrade.strategy.StrategyParamsView;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nightly live-vs-backtest parity check (plan §7.3): for every active (SHADOW/CHAMPION)
 * {@link StrategyConfig} variant, re-evaluates yesterday's last completed trading day through
 * the exact same {@link SignalStrategy#evaluateEntry} call the live SIGNAL stage uses (see
 * {@link SignalPipeline#generateVariantSignals}) and compares the result against what was
 * actually recorded in the {@code signals} table for that variant/day.
 *
 * <p>Strictly read-only with respect to {@code signals} and paper portfolios - this class never
 * writes to either. The only writes it performs are the {@code parity_mismatch} metric row
 * (reusing {@code strategy_experiment_log}, plan §6.4's append-only run log - see V48) and, on a
 * mismatch, a Discord alert.
 *
 * <p>A missing {@code signals} row for a (symbol, variant, date) is treated as a recorded HOLD,
 * matching {@link SignalPipeline#evaluateAndPersistVariant}'s table-growth convention of
 * suppressing HOLD rows for SHADOW variants (CHAMPION persists every row, including HOLD).
 */
@Service
public class ParityCheckService {

    private static final Logger log = LoggerFactory.getLogger(ParityCheckService.class);
    private static final int MIN_CANDLES = 50;
    private static final int LOOKBACK_CANDLES = 100;

    private final StrategyConfigStore strategyConfigStore;
    private final StrategyTypeRegistry strategyTypeRegistry;
    private final CandleStore candleStore;
    private final SignalStore signalStore;
    private final GateEvaluator gateEvaluator;
    private final MarketCalendar marketCalendar;
    private final StrategyExperimentLogRepository experimentLogRepository;
    private final DiscordNotificationService discordService;

    public ParityCheckService(StrategyConfigStore strategyConfigStore,
                               StrategyTypeRegistry strategyTypeRegistry,
                               CandleStore candleStore,
                               SignalStore signalStore,
                               GateEvaluator gateEvaluator,
                               MarketCalendar marketCalendar,
                               StrategyExperimentLogRepository experimentLogRepository,
                               DiscordNotificationService discordService) {
        this.strategyConfigStore = strategyConfigStore;
        this.strategyTypeRegistry = strategyTypeRegistry;
        this.candleStore = candleStore;
        this.signalStore = signalStore;
        this.gateEvaluator = gateEvaluator;
        this.marketCalendar = marketCalendar;
        this.experimentLogRepository = experimentLogRepository;
        this.discordService = discordService;
    }

    /**
     * The last completed NSE trading day strictly before {@code today}, e.g. "yesterday" unless
     * yesterday was a weekend/holiday, in which case it walks further back. Reuses
     * {@link MarketCalendar#isNseTradingSession}, the single source of truth for whether an NSE
     * session actually ran on a date (also used by {@code EodIngestionScheduler}).
     */
    public LocalDate lastCompletedTradingDay(LocalDate today) {
        LocalDate candidate = today.minusDays(1);
        while (!marketCalendar.isNseTradingSession(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    /**
     * Runs the parity check for every active variant against {@code today}'s last completed
     * trading day, persists one {@code strategy_experiment_log} row per variant, and sends a
     * Discord alert if any variant had a mismatch.
     *
     * @param today the reference "now" (the calendar date the nightly job runs on)
     * @return the mismatches found, across all variants
     */
    public List<ParityMismatch> runParityCheck(LocalDate today) {
        LocalDate targetDate = lastCompletedTradingDay(today);
        List<StrategyConfig> activeConfigs = strategyConfigStore.findAllCurrent().stream()
            .filter(StrategyConfig::isActive)
            .toList();

        List<ParityMismatch> allMismatches = new ArrayList<>();
        for (StrategyConfig config : activeConfigs) {
            List<ParityMismatch> mismatches = checkVariant(config, targetDate);
            persistMetric(config, targetDate, mismatches);
            allMismatches.addAll(mismatches);
        }

        if (!allMismatches.isEmpty()) {
            alertDiscord(targetDate, allMismatches);
        }
        return allMismatches;
    }

    private List<ParityMismatch> checkVariant(StrategyConfig config, LocalDate targetDate) {
        Optional<SignalStrategy> strategy = strategyTypeRegistry.findByType(config.strategyType());
        if (strategy.isEmpty()) {
            log.warn("Parity check: unknown strategy type {} for variant {} v{} - skipping",
                config.strategyType(), config.variantId(), config.version());
            return List.of();
        }

        Map<String, Signal.SignalType> recorded = new HashMap<>();
        for (Signal signal : signalStore.findByDateAndStrategy(targetDate, config.variantId())) {
            recorded.put(signal.symbol(), signal.type());
        }

        StrategyParamsView params = StrategyParamsView.of(config.params());
        Optional<RegimeGateConfig> regimeGateConfig = OverlayConfigResolver.regimeGate(config.overlays());

        List<ParityMismatch> mismatches = new ArrayList<>();
        for (String symbol : candleStore.findAllDistinctSymbols()) {
            Signal.SignalType reevaluated = reevaluate(strategy.get(), params, regimeGateConfig, symbol, targetDate)
                .orElse(null);
            if (reevaluated == null) {
                // Not enough candle history (or no candle on targetDate) to trust the re-eval;
                // skip rather than falsely flag a mismatch.
                continue;
            }
            Signal.SignalType recordedType = recorded.getOrDefault(symbol, Signal.SignalType.HOLD);
            if (recordedType != reevaluated) {
                mismatches.add(new ParityMismatch(config.variantId(), config.version(), symbol, targetDate,
                    recordedType, reevaluated));
            }
        }
        return mismatches;
    }

    /**
     * Re-evaluates {@code symbol} at {@code targetDate} exactly the way
     * {@link SignalPipeline#generateVariantSignals} does for the live SIGNAL stage: same
     * {@code MIN_CANDLES}/lookback-window shape, same chronological {@link MarketContext}
     * construction, same {@link SignalStrategy#evaluateEntry} call, same regime-gate overlay
     * application.
     *
     * @return empty if there isn't enough candle history (or no candle exactly on
     *     {@code targetDate}) to re-evaluate
     */
    private Optional<Signal.SignalType> reevaluate(SignalStrategy strategy, StrategyParamsView params,
                                                     Optional<RegimeGateConfig> regimeGateConfig,
                                                     String symbol, LocalDate targetDate) {
        List<OhlcvCandle> candles = candleStore.findAllBySymbolOrderByDateDesc(symbol).stream()
            .filter(c -> !c.date().isAfter(targetDate))
            .limit(LOOKBACK_CANDLES)
            .toList();
        if (candles.size() < MIN_CANDLES) {
            return Optional.empty();
        }
        List<OhlcvCandle> chronological = new ArrayList<>(candles);
        Collections.reverse(chronological);
        if (!chronological.get(chronological.size() - 1).date().equals(targetDate)) {
            // No candle exactly on targetDate (e.g. symbol delisted/suspended that day).
            return Optional.empty();
        }

        MarketContext ctx;
        try {
            ctx = MarketContext.of(symbol, chronological);
        } catch (RuntimeException e) {
            log.warn("Parity check: could not build MarketContext for {} on {}: {}",
                symbol, targetDate, e.getMessage());
            return Optional.empty();
        }
        int barIndex = ctx.barCount() - 1;

        StrategyDecision decision = strategy.evaluateEntry(ctx, barIndex, params);
        StrategyDecision afterGates = decision;
        if (regimeGateConfig.isPresent()) {
            GateEvaluator.GateResult result = gateEvaluator.applyRegimeGate(
                afterGates, targetDate, null, regimeGateConfig.get());
            afterGates = result.decision();
        }
        // sentimentGate is deliberately not applied here: SignalPipeline defers it to a later
        // stage at SIGNAL time too (see generateVariantSignals' javadoc simplification #2), so
        // the persisted "live" decision it is compared against was never sentiment-gated either.
        return Optional.of(afterGates.type());
    }

    private void persistMetric(StrategyConfig config, LocalDate targetDate, List<ParityMismatch> mismatches) {
        StrategyExperimentLogEntity entity = new StrategyExperimentLogEntity();
        entity.setVariantId(config.variantId());
        entity.setVersion(config.version());
        entity.setStrategyType(config.strategyType());
        entity.setParamsHash(config.paramsHash());
        entity.setWindowStart(targetDate);
        entity.setWindowEnd(targetDate);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("metric", "parity_mismatch");
        metrics.put("parityMismatchCount", mismatches.size());
        List<Map<String, Object>> details = new ArrayList<>();
        for (ParityMismatch mismatch : mismatches) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("symbol", mismatch.symbol());
            detail.put("recorded", mismatch.recordedType().name());
            detail.put("reevaluated", mismatch.reevaluatedType().name());
            details.add(detail);
        }
        metrics.put("mismatches", details);
        entity.setMetrics(metrics);

        experimentLogRepository.save(entity);
    }

    private void alertDiscord(LocalDate targetDate, List<ParityMismatch> mismatches) {
        StringBuilder description = new StringBuilder();
        description.append(mismatches.size()).append(" mismatch(es) on ").append(targetDate).append(":\n");
        for (ParityMismatch mismatch : mismatches) {
            description.append("- ").append(mismatch.variantId()).append(" v").append(mismatch.version())
                .append(" / ").append(mismatch.symbol())
                .append(": recorded=").append(mismatch.recordedType())
                .append(" reevaluated=").append(mismatch.reevaluatedType())
                .append('\n');
        }
        boolean sent = discordService.sendEmbed(
            "Live-vs-backtest parity mismatch", description.toString(), DiscordNotificationService.COLOR_RED);
        if (!sent) {
            log.warn("Parity mismatch alert not delivered (Discord disabled or webhook not configured): {}",
                description);
        }
    }

    /**
     * One (variant, symbol, date) triple where the nightly re-evaluated decision differs from
     * what was actually recorded in {@code signals} (plan §7.3).
     */
    public record ParityMismatch(String variantId, int version, String symbol, LocalDate date,
                                  Signal.SignalType recordedType, Signal.SignalType reevaluatedType) {
    }
}
