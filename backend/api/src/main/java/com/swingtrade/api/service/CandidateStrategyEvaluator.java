package com.swingtrade.api.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.strategy.LiveSignalEvaluator;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import com.swingtrade.strategy.ResolvedStrategy;
import com.swingtrade.strategy.StrategyResolver;
import com.swingtrade.strategy.WalkForwardEvaluation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Evaluates every live configured strategy for a candidate without persisting a signal. */
@Service
public class CandidateStrategyEvaluator {
    private final ActiveVariantService variants;
    private final StrategyResolver resolver;
    private final BacktestEngine backtestEngine;

    public CandidateStrategyEvaluator(ActiveVariantService variants, StrategyResolver resolver,
                                      BacktestEngine backtestEngine) {
        this.variants = variants;
        this.resolver = resolver;
        this.backtestEngine = backtestEngine;
    }

    public List<Outcome> evaluate(String symbol, List<OhlcvCandle> candles) {
        return variants.active().stream().map(config -> {
            ResolvedStrategy resolved = resolver.resolve(config);
            if (!(resolved instanceof ResolvedStrategy.Signal signal)) {
                return new Outcome(config.variantId(), "SKIPPED", null, "Strategy is not a live signal strategy");
            }
            return LiveSignalEvaluator.evaluateEntry(symbol, candles, signal)
                .map(entry -> new Outcome(config.variantId(), entry.decision().type().name(),
                    entry.decision().score(), null))
                .orElseGet(() -> new Outcome(config.variantId(), "SKIPPED", null, "Insufficient warm-up history"));
        }).toList();
    }

    /** Evaluates live signal and independent historical performance for every resolved variant. */
    public List<Outcome> evaluateWithPerformance(String symbol, List<OhlcvCandle> candles,
                                                 String exchange, BacktestConfig config,
                                                 int oosDays, int oosFolds) {
        return variants.active().stream().map(candidate -> {
            ResolvedStrategy resolved = resolver.resolve(candidate);
            if (!(resolved instanceof ResolvedStrategy.Signal signal)) {
                return new Outcome(candidate.variantId(), "SKIPPED", null,
                    "Strategy is not a live signal strategy", null, null, "SKIPPED");
            }
            var live = LiveSignalEvaluator.evaluateEntry(symbol, candles, signal);
            if (live.isEmpty()) {
                return new Outcome(candidate.variantId(), "SKIPPED", null,
                    "Insufficient warm-up history", null, null, "SKIPPED");
            }
            try {
                BacktestResult backtest = backtestEngine.runBacktest(symbol, exchange, config,
                    signal.strategy(), signal.params());
                WalkForwardEvaluation walkForward = backtestEngine.runWalkForward(symbol, exchange, config,
                    signal.strategy(), signal.params(), oosDays, oosFolds);
                return new Outcome(candidate.variantId(), live.get().decision().type().name(),
                    live.get().decision().score(), live.get().decision().reasoning(), backtest, walkForward,
                    "EVALUATED");
            } catch (IllegalStateException e) {
                return new Outcome(candidate.variantId(), live.get().decision().type().name(),
                    live.get().decision().score(), "Performance unavailable: " + e.getMessage(),
                    null, null, "ERROR");
            }
        }).toList();
    }

    public record Outcome(String variantId, String signalType, BigDecimal score, String detail,
                          BacktestResult backtest, WalkForwardEvaluation walkForward,
                          String performanceStatus) {
        public Outcome(String variantId, String signalType, BigDecimal score, String detail) {
            this(variantId, signalType, score, detail, null, null, "NOT_RUN");
        }
        public boolean buy() { return "BUY".equals(signalType); }
        public Integer backtestTotalTrades() { return backtest == null ? null : backtest.totalTrades(); }
        public Double backtestWinRate() { return backtest == null ? null : backtest.winRate(); }
        public Double backtestTotalReturn() { return backtest == null ? null : backtest.totalReturn().doubleValue(); }
        public Integer oosTotalTrades() { return walkForward == null ? null : walkForward.totalTrades(); }
        public Double oosWinRate() { return walkForward == null ? null : walkForward.averageWinRate(); }
        public Double oosTotalReturn() { return walkForward == null ? null : walkForward.averageTotalReturn(); }
    }
}
