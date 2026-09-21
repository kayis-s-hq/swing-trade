package com.swingtrade.api.service;

import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.service.VariantTradingService;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.strategy.LiveSignalEvaluator;
import com.swingtrade.strategy.ResolvedStrategy;
import com.swingtrade.strategy.StrategyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The per-variant simulated paper-trading half of the PAPER_TRADE stage: every active variant's
 * own portfolio bookkeeping, plus the "selected" book that follows the signal-tournament winner.
 * Extracted from {@link JobOrchestratorService}; stateless apart from its collaborators.
 */
final class VariantPaperTradeStage {

    private static final Logger logger = LoggerFactory.getLogger(VariantPaperTradeStage.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final VariantTradingService variantTradingService;
    private final CandleStore candleStore;
    private final SignalStore signalStore;
    private final StrategyResolver strategyResolver;
    private final SignalArbiter signalArbiter;
    private final SentimentGate sentimentGate;
    private final LlmAnalysisGate llmAnalysisGate;
    private final LiveEligibilityService liveEligibilityService;
    private final boolean llmAnalysisEnabled;
    private final boolean llmAnalysisAdvisoryOnly;

    VariantPaperTradeStage(VariantTradingService variantTradingService, CandleStore candleStore,
                           SignalStore signalStore, StrategyResolver strategyResolver,
                           SignalArbiter signalArbiter, SentimentGate sentimentGate,
                           LlmAnalysisGate llmAnalysisGate, LiveEligibilityService liveEligibilityService,
                           boolean llmAnalysisEnabled, boolean llmAnalysisAdvisoryOnly) {
        this.variantTradingService = variantTradingService;
        this.candleStore = candleStore;
        this.signalStore = signalStore;
        this.strategyResolver = strategyResolver;
        this.signalArbiter = signalArbiter;
        this.sentimentGate = sentimentGate;
        this.llmAnalysisGate = llmAnalysisGate;
        this.liveEligibilityService = liveEligibilityService;
        this.llmAnalysisEnabled = llmAnalysisEnabled;
        this.llmAnalysisAdvisoryOnly = llmAnalysisAdvisoryOnly;
    }

    /**
     * Runs every active variant's own simulated paper trading — SHADOW variants entirely, and
     * the CHAMPION too (in addition to its real order-queueing path), so every variant's own
     * portfolio bookkeeping is directly comparable. Each variant is isolated in its own
     * try/catch: one variant's failure for this symbol/day must not block another's execution.
     */
    void runVariants(String symbol, List<StrategyConfig> configs) {
        if (variantTradingService == null || configs.isEmpty()) return;
        OhlcvCandle latest = candleStore.findLatestBySymbol(symbol).orElse(null);
        if (latest == null || latest.close() == null) return;

        for (StrategyConfig config : configs) {
            String variantId = config.variantId();
            try {
                variantTradingService.ensurePortfolio(variantId, config.paperCapital());
                variantTradingService.evaluateOpenPositions(variantId, symbol, latest);
                evaluateStrategyExits(config, symbol);

                List<Signal> variantSignals = signalStore.findUnprocessed().stream()
                    .filter(s -> s.symbol().equals(symbol))
                    .filter(s -> signalStore.findStrategyById(s.id())
                        .map(variantId::equals).orElse(false))
                    .toList();
                for (Signal signal : variantSignals) {
                    try {
                        if (signal.type() == Signal.SignalType.BUY) {
                            variantTradingService.openPosition(variantId, signal, latest.close());
                        } else if (signal.type() == Signal.SignalType.SELL) {
                            variantTradingService.closePosition(variantId, symbol, latest.close(),
                                "Strategy SELL signal");
                        }
                        signalStore.markProcessed(signal.id());
                    } catch (RuntimeException e) {
                        logger.warn("Variant {} failed to process signal {} for {}: {}",
                            variantId, signal.id(), symbol, e.getMessage());
                    }
                }
            } catch (RuntimeException e) {
                logger.warn("Variant paper trading failed for {} on {} (isolated, other variants unaffected): {}",
                    variantId, symbol, e.getMessage());
            }
        }
    }

    /** Closes a SignalStrategy variant's open positions per its own {@code evaluateExit} rules. */
    private void evaluateStrategyExits(StrategyConfig config, String symbol) {
        if (!(strategyResolver.resolve(config) instanceof ResolvedStrategy.Signal resolved)) return;
        List<Position> open = variantTradingService.findOpenPositions(config.variantId(), symbol);
        if (open.isEmpty()) return;
        List<OhlcvCandle> chronological = new ArrayList<>(candleStore.findTopBySymbolOrderByDateDesc(symbol, 1000));
        Collections.reverse(chronological);
        for (Position position : open) {
            var decision = LiveSignalEvaluator.evaluateExit(symbol, chronological, resolved,
                position.entryDate(), position.entryPrice(), position.stopLoss(), position.target(),
                position.quantity());
            if (decision.isPresent() && decision.get().exit()) {
                variantTradingService.closePosition(config.variantId(), symbol, decision.get().exitPrice(),
                    decision.get().reason() + ": " + decision.get().detail());
            }
        }
    }

    /**
     * Drives the dedicated "selected" paper book: evaluates exits on its open position for
     * {@code symbol}, then executes the tournament winner if it is still PENDING and clears the
     * same sentiment / LLM / live-eligibility gates as the champion's BUYs. The winner's own
     * variant book is unaffected - this only adds a follow-the-winner book.
     *
     * <p>The returned text is derived from real outcomes only: a variant is named only when it
     * was actually evaluated in this run ({@code evaluatedVariantIds}); a pending selection whose
     * variant was skipped or not evaluated is reported without naming it.</p>
     *
     * @return a short summary, or {@code null} when there is nothing selected to act on
     */
    String selectedTrade(String symbol, String portfolioId, java.math.BigDecimal capital,
                         int expiryDays, Set<String> evaluatedVariantIds) {
        if (signalArbiter == null || variantTradingService == null) {
            return null;
        }
        try {
            variantTradingService.ensurePortfolio(portfolioId, capital);
            OhlcvCandle latest = candleStore.findLatestBySymbol(symbol).orElse(null);
            if (latest == null || latest.close() == null) {
                return null;
            }
            variantTradingService.evaluateOpenPositions(portfolioId, symbol, latest);

            Optional<SignalSelectionEntity> pending =
                signalArbiter.findLatest(symbol, SignalSelectionEntity.PENDING);
            if (pending.isEmpty()) {
                return null;
            }
            SignalSelectionEntity selection = pending.get();
            String winner = selection.getWinnerVariantId();
            String label = evaluatedVariantIds.contains(winner) ? winner : "prior selection";
            if (selection.getSelectionDate().isBefore(LocalDate.now(IST).minusDays(expiryDays))) {
                signalArbiter.markStatus(selection, SignalSelectionEntity.BLOCKED, "expired before gates cleared");
                return "expired " + label;
            }
            Optional<Signal> signal = signalStore.findLatestBySymbolAndStrategy(symbol, winner)
                .filter(s -> selection.getWinnerSignalId() == null || selection.getWinnerSignalId().equals(s.id()));
            if (signal.isEmpty() || signal.get().type() != Signal.SignalType.BUY) {
                signalArbiter.markStatus(selection, SignalSelectionEntity.BLOCKED, "winning signal no longer available");
                return "winning signal missing";
            }

            String block = null;
            var sentiment = sentimentGate.evaluatePersisted(symbol, signal.get().date());
            if (sentiment.action() == SentimentGate.SentimentVerdict.Action.PENDING) {
                return "deferred " + label + " (awaiting sentiment)";
            }
            if (sentiment.action() == SentimentGate.SentimentVerdict.Action.SUPPRESS) {
                block = "SENTIMENT_BLOCK: " + sentiment.reason();
            }
            if (block == null && llmAnalysisEnabled && llmAnalysisGate != null) {
                var llm = llmAnalysisGate.evaluatePersisted(symbol, signal.get().date());
                if (llm.action() == LlmAnalysisGate.LlmVerdict.Action.PENDING) {
                    return "deferred " + label + " (awaiting LLM analysis)";
                }
                if (llm.action() == LlmAnalysisGate.LlmVerdict.Action.SUPPRESS && !llmAnalysisAdvisoryOnly) {
                    block = "LLM_BLOCK: " + llm.reason();
                }
            }
            if (block == null && liveEligibilityService != null) {
                var eligibility = liveEligibilityService.assess(symbol, signal.get().date(), latest.close());
                if (!eligibility.eligible()) {
                    block = "ELIGIBILITY_BLOCK: " + eligibility.rejectionReasons();
                }
            }
            if (block != null) {
                signalArbiter.markStatus(selection, SignalSelectionEntity.BLOCKED, block);
                logger.info("Selected-book BUY {} for {} blocked: {}", winner, symbol, block);
                return "blocked " + label;
            }
            if (!variantTradingService.openPosition(portfolioId, signal.get(), latest.close())) {
                return "could not open " + label + " (will retry)";
            }
            signalArbiter.markStatus(selection, SignalSelectionEntity.EXECUTED, null);
            return "executed " + label;
        } catch (RuntimeException e) {
            logger.error("Selected-book execution failed for {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }
}
