package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal.SignalType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Multi-symbol portfolio backtest simulator (plan §6.1). Iterates by calendar date across every
 * candidate symbol with shared cash, honouring {@code maxConcurrentPositions} and
 * {@code maxCapitalPerPositionPct} the same way {@code paper_trading_portfolio} does live.
 *
 * <p>Fill semantics mirror the live pending-order flow (checked against
 * {@code PendingOrderExecutionScheduler} before writing this class): a strategy's entry decision
 * is made from bar T's close, and the fill happens at bar T+1's open plus slippage - never at T's
 * own close. Exits are evaluated through {@link SignalStrategy#evaluateExit}, which already
 * (via {@link UniformExitEvaluator}) fills a gapped-through stop/target at that bar's open rather
 * than the stop/target price itself, so no separate gap handling is needed here.
 *
 * <p>Position sizing is risk-based: {@code riskPerTradePct * equity / (entry - stop)}, capped by
 * {@code maxCapitalPerPositionPct * equity} and by available cash. Equity used for sizing is the
 * most recently closed day's mark-to-market equity (yesterday's), never today's still-unknown
 * value - this keeps sizing decisions look-ahead free.
 *
 * <p>Equity is marked to market every calendar date in the run (fixes finding F10: the legacy
 * single-symbol {@link BacktestEngine} only updates equity at trade exit, which biases
 * Sharpe/MaxDD to look smoother than the true daily P&amp;L path).
 *
 * <p>The single-symbol {@link BacktestEngine} is left untouched and keeps serving diagnostics
 * (plan §6.1: "leaderboard uses portfolio engine only").
 */
public final class PortfolioBacktestEngine {

    /** Symbol-concentration flag threshold (plan §6.2: "flag if one symbol > 40% of P&L"). */
    static final double SYMBOL_CONCENTRATION_THRESHOLD_PCT = 40.0;

    /**
     * Runs the portfolio simulation.
     *
     * @param candlesBySymbol   chronologically-ordered candle history per symbol, including
     *                          enough leading warm-up history before {@code evaluationStart} for
     *                          {@code strategy.warmupBars(params)}
     * @param strategy          the {@link SignalStrategy} under test (e.g.
     *                          {@link LegacyPriceActionAdapter})
     * @param params            resolved params for {@code strategy}
     * @param config            capital/risk/cost configuration (plan §6.1)
     * @param evaluationStart   first date new entries may be taken (inclusive)
     * @param evaluationEnd     last date the simulation runs to (inclusive)
     * @param annualRiskFreeRatePct annualised risk-free rate for Sharpe/Sortino, e.g. 6.5
     */
    public PortfolioBacktestResult run(Map<String, List<OhlcvCandle>> candlesBySymbol,
                                        SignalStrategy strategy,
                                        StrategyParamsView params,
                                        PortfolioBacktestConfig config,
                                        LocalDate evaluationStart,
                                        LocalDate evaluationEnd,
                                        double annualRiskFreeRatePct) {
        if (candlesBySymbol == null || candlesBySymbol.isEmpty()) {
            throw new IllegalArgumentException("candlesBySymbol cannot be null/empty");
        }
        if (evaluationStart == null || evaluationEnd == null || evaluationStart.isAfter(evaluationEnd)) {
            throw new IllegalArgumentException("Evaluation window must be non-empty and ordered");
        }

        List<ExcludedSymbol> excluded = new ArrayList<>();
        Map<String, MarketContext> contexts = new HashMap<>();
        Map<String, Map<LocalDate, Integer>> dateIndex = new HashMap<>();
        buildContexts(candlesBySymbol, strategy, params, evaluationStart, evaluationEnd, excluded, contexts,
            dateIndex);

        if (contexts.isEmpty()) {
            throw new IllegalStateException("No symbol passed data-quality gates: " + excluded);
        }

        TreeSet<LocalDate> globalDates = collectGlobalDates(dateIndex, evaluationStart, evaluationEnd);

        SimState state = new SimState(config.initialCapital());
        List<PortfolioTrade> trades = new ArrayList<>();
        List<DailyEquityPoint> equityCurve = new ArrayList<>();

        for (LocalDate date : globalDates) {
            fillPendingEntries(date, state, contexts, dateIndex, config);
            manageExits(date, state, contexts, dateIndex, strategy, params, config, trades);
            selectCandidates(date, state, contexts, dateIndex, strategy, params, config);
            equityCurve.add(markToMarket(date, state));
        }

        LocalDate lastDate = globalDates.isEmpty() ? evaluationEnd : globalDates.last();
        forceCloseRemaining(lastDate, state, config, trades);

        if (equityCurve.isEmpty()) {
            throw new IllegalStateException("No trading dates in evaluation window for any surviving symbol");
        }

        return buildResult(trades, equityCurve, excluded, annualRiskFreeRatePct);
    }

    private void buildContexts(Map<String, List<OhlcvCandle>> candlesBySymbol, SignalStrategy strategy,
                                StrategyParamsView params, LocalDate evaluationStart, LocalDate evaluationEnd,
                                List<ExcludedSymbol> excluded, Map<String, MarketContext> contexts,
                                Map<String, Map<LocalDate, Integer>> dateIndex) {
        int warmupBars = strategy.warmupBars(params);
        for (Map.Entry<String, List<OhlcvCandle>> entry : candlesBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<OhlcvCandle> candles = entry.getValue();
            Optional<String> reason = DataQualityGate.evaluate(symbol, candles, evaluationStart, evaluationEnd);
            if (reason.isPresent()) {
                excluded.add(new ExcludedSymbol(symbol, reason.get()));
                continue;
            }
            if (candles.size() <= warmupBars + 1) {
                excluded.add(new ExcludedSymbol(symbol, "Insufficient candle history for warm-up ("
                    + candles.size() + " <= " + (warmupBars + 1) + ")"));
                continue;
            }
            MarketContext ctx = MarketContext.of(symbol, candles);
            Map<LocalDate, Integer> byDate = new HashMap<>();
            for (int i = 0; i < candles.size(); i++) {
                byDate.put(candles.get(i).date(), i);
            }
            contexts.put(symbol, ctx);
            dateIndex.put(symbol, byDate);
        }
    }

    private TreeSet<LocalDate> collectGlobalDates(Map<String, Map<LocalDate, Integer>> dateIndex,
                                                   LocalDate evaluationStart, LocalDate evaluationEnd) {
        TreeSet<LocalDate> globalDates = new TreeSet<>();
        for (Map<LocalDate, Integer> byDate : dateIndex.values()) {
            for (LocalDate d : byDate.keySet()) {
                if (!d.isBefore(evaluationStart) && !d.isAfter(evaluationEnd)) {
                    globalDates.add(d);
                }
            }
        }
        return globalDates;
    }

    /** Fills entries decided on the previous date at today's open + slippage (plan §6.1). */
    private void fillPendingEntries(LocalDate date, SimState state, Map<String, MarketContext> contexts,
                                     Map<String, Map<LocalDate, Integer>> dateIndex,
                                     PortfolioBacktestConfig config) {
        Map<String, StrategyDecision> toFill = state.pendingEntries;
        state.pendingEntries = new HashMap<>();
        for (Map.Entry<String, StrategyDecision> e : toFill.entrySet()) {
            String symbol = e.getKey();
            Integer idx = dateIndex.get(symbol).get(date);
            if (idx == null) {
                continue; // symbol didn't trade today; decision lapses (documented simplification)
            }
            MarketContext.View view = contexts.get(symbol).view(idx);
            BigDecimal fillPrice = view.open().multiply(BigDecimal.valueOf(1 + config.slippagePct()));
            StrategyDecision decision = e.getValue();
            BigDecimal stop = decision.suggestedStop();
            BigDecimal target = decision.suggestedTarget();
            BigDecimal riskPerShare = fillPrice.subtract(stop);
            if (riskPerShare.signum() <= 0) {
                continue;
            }
            int quantity = sizePosition(state, config, fillPrice, riskPerShare);
            if (quantity <= 0) {
                continue;
            }
            state.cash = state.cash.subtract(fillPrice.multiply(BigDecimal.valueOf(quantity)));
            state.openPositions.put(symbol, OpenPosition.open(idx, date, fillPrice, stop, target, quantity));
            state.excursions.put(symbol, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            state.lastKnownPrice.put(symbol, fillPrice);
        }
    }

    private int sizePosition(SimState state, PortfolioBacktestConfig config, BigDecimal fillPrice,
                              BigDecimal riskPerShare) {
        BigDecimal riskAmount = state.equity.multiply(config.riskPerTradePct());
        int qtyByRisk = riskAmount.divide(riskPerShare, 0, RoundingMode.FLOOR).intValue();
        BigDecimal maxCapital = state.equity.multiply(config.maxCapitalPerPositionPct());
        int qtyByCapital = fillPrice.signum() == 0 ? 0
            : maxCapital.divide(fillPrice, 0, RoundingMode.FLOOR).intValue();
        int qtyByCash = fillPrice.signum() == 0 ? 0
            : state.cash.divide(fillPrice, 0, RoundingMode.FLOOR).intValue();
        return Math.min(Math.min(qtyByRisk, qtyByCapital), qtyByCash);
    }

    /** Manages exits (and MAE/MFE tracking) for open positions with a bar today (plan §6.1/§6.2). */
    private void manageExits(LocalDate date, SimState state, Map<String, MarketContext> contexts,
                              Map<String, Map<LocalDate, Integer>> dateIndex, SignalStrategy strategy,
                              StrategyParamsView params, PortfolioBacktestConfig config,
                              List<PortfolioTrade> trades) {
        for (String symbol : new ArrayList<>(state.openPositions.keySet())) {
            Integer idx = dateIndex.get(symbol).get(date);
            if (idx == null) {
                continue;
            }
            OpenPosition pos = state.openPositions.get(symbol);
            MarketContext.View view = contexts.get(symbol).view(idx);
            state.lastKnownPrice.put(symbol, view.close());

            BigDecimal[] mm = state.excursions.get(symbol);
            mm[0] = mm[0].min(view.low().subtract(pos.entryPrice()));
            mm[1] = mm[1].max(view.high().subtract(pos.entryPrice()));

            ExitDecision exitDecision = strategy.evaluateExit(contexts.get(symbol), idx, pos, params);
            if (exitDecision.exit()) {
                closePosition(symbol, date, idx, pos, exitDecision, mm, state, config, trades);
            }
        }
    }

    private void closePosition(String symbol, LocalDate date, int idx, OpenPosition pos, ExitDecision exitDecision,
                                BigDecimal[] mm, SimState state, PortfolioBacktestConfig config,
                                List<PortfolioTrade> trades) {
        BigDecimal exitPrice = exitDecision.exitPrice().multiply(BigDecimal.valueOf(1 - config.slippagePct()));
        BigDecimal costs = config.costModel().roundTripCost(pos.entryPrice(), exitPrice, pos.quantity(),
            config.brokeragePerTrade());
        BigDecimal grossPnl = exitPrice.subtract(pos.entryPrice()).multiply(BigDecimal.valueOf(pos.quantity()));
        BigDecimal netPnl = grossPnl.subtract(costs);
        double pnlPct = pnlPct(pos, netPnl);

        state.cash = state.cash.add(exitPrice.multiply(BigDecimal.valueOf(pos.quantity()))).subtract(costs);

        trades.add(new PortfolioTrade(symbol, pos.entryDate(), date, pos.entryPrice(), exitPrice,
            pos.stopLoss(), pos.target(), pos.quantity(), exitDecision.reason(),
            pos.entryPrice().subtract(pos.stopLoss()), netPnl, pnlPct, idx - pos.entryIndex(),
            mm[0], mm[1], BigDecimal.ONE));

        state.openPositions.remove(symbol);
        state.excursions.remove(symbol);
    }

    private double pnlPct(OpenPosition pos, BigDecimal netPnl) {
        BigDecimal entryValue = pos.entryPrice().multiply(BigDecimal.valueOf(pos.quantity()));
        return entryValue.signum() == 0 ? 0.0
            : netPnl.divide(entryValue, 6, RoundingMode.HALF_UP).doubleValue() * 100.0;
    }

    /**
     * Ranks today's BUY candidates by score desc, tiebreak lower ATR% (plan §6.1), and reserves
     * capacity-limited slots as tomorrow's pending entries.
     */
    private void selectCandidates(LocalDate date, SimState state, Map<String, MarketContext> contexts,
                                   Map<String, Map<LocalDate, Integer>> dateIndex, SignalStrategy strategy,
                                   StrategyParamsView params, PortfolioBacktestConfig config) {
        record Candidate(String symbol, StrategyDecision decision, BigDecimal atrPct) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, MarketContext> e : contexts.entrySet()) {
            String symbol = e.getKey();
            if (state.openPositions.containsKey(symbol) || state.pendingEntries.containsKey(symbol)) {
                continue;
            }
            Integer idx = dateIndex.get(symbol).get(date);
            if (idx == null) {
                continue;
            }
            MarketContext.View view = e.getValue().view(idx);
            StrategyDecision decision = strategy.evaluateEntry(e.getValue(), idx, params);
            if (decision.type() == SignalType.BUY) {
                BigDecimal close = view.close();
                BigDecimal atrPct = close.signum() == 0 ? BigDecimal.ZERO
                    : view.atr(config.atrPeriodForRanking()).divide(close, 6, RoundingMode.HALF_UP);
                candidates.add(new Candidate(symbol, decision, atrPct));
            }
        }
        candidates.sort(Comparator.<Candidate, BigDecimal>comparing(c -> c.decision().score()).reversed()
            .thenComparing(Candidate::atrPct));

        int capacity = config.maxConcurrentPositions() - state.openPositions.size() - state.pendingEntries.size();
        for (Candidate c : candidates) {
            if (capacity <= 0) {
                break;
            }
            state.pendingEntries.put(c.symbol(), c.decision());
            capacity--;
        }
    }

    /** Daily mark-to-market equity (plan §6.1 fix for finding F10). */
    private DailyEquityPoint markToMarket(LocalDate date, SimState state) {
        BigDecimal openValue = BigDecimal.ZERO;
        for (Map.Entry<String, OpenPosition> e : state.openPositions.entrySet()) {
            BigDecimal price = state.lastKnownPrice.getOrDefault(e.getKey(), e.getValue().entryPrice());
            openValue = openValue.add(price.multiply(BigDecimal.valueOf(e.getValue().quantity())));
        }
        state.equity = state.cash.add(openValue);
        return new DailyEquityPoint(date, state.equity);
    }

    /**
     * Force-closes any still-open positions at the last known price at the end of the run (plan
     * mirrors the legacy single-symbol engine's tail handling for symmetric treatment).
     */
    private void forceCloseRemaining(LocalDate lastDate, SimState state, PortfolioBacktestConfig config,
                                      List<PortfolioTrade> trades) {
        for (Map.Entry<String, OpenPosition> e : new HashMap<>(state.openPositions).entrySet()) {
            String symbol = e.getKey();
            OpenPosition pos = e.getValue();
            BigDecimal exitPrice = state.lastKnownPrice.getOrDefault(symbol, pos.entryPrice());
            BigDecimal costs = config.costModel().roundTripCost(pos.entryPrice(), exitPrice, pos.quantity(),
                config.brokeragePerTrade());
            BigDecimal grossPnl = exitPrice.subtract(pos.entryPrice()).multiply(BigDecimal.valueOf(pos.quantity()));
            BigDecimal netPnl = grossPnl.subtract(costs);
            double pnlPct = pnlPct(pos, netPnl);
            BigDecimal[] mm = state.excursions.getOrDefault(symbol, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            trades.add(new PortfolioTrade(symbol, pos.entryDate(), lastDate, pos.entryPrice(), exitPrice,
                pos.stopLoss(), pos.target(), pos.quantity(), ExitReason.TIME_STOP,
                pos.entryPrice().subtract(pos.stopLoss()), netPnl, pnlPct, 0, mm[0], mm[1], BigDecimal.ONE));
        }
    }

    private PortfolioBacktestResult buildResult(List<PortfolioTrade> trades, List<DailyEquityPoint> equityCurve,
                                                 List<ExcludedSymbol> excluded, double annualRiskFreeRatePct) {
        PortfolioMetrics metrics = MetricsCalculator.compute(equityCurve, trades, annualRiskFreeRatePct);
        Map<String, Double> perSymbolContribution = MetricsCalculator.perSymbolPnlContributionPct(trades);
        boolean concentrationWarning = perSymbolContribution.values().stream()
            .anyMatch(pct -> Math.abs(pct) > SYMBOL_CONCENTRATION_THRESHOLD_PCT);

        return new PortfolioBacktestResult(trades, equityCurve, excluded, metrics, perSymbolContribution,
            MetricsCalculator.exitReasonBreakdown(trades), MetricsCalculator.perYearReturnPct(equityCurve),
            concentrationWarning);
    }

    /** Mutable per-run simulation state, kept out of instance fields so the engine stays stateless/reusable. */
    private static final class SimState {
        private BigDecimal cash;
        private BigDecimal equity;
        private final Map<String, OpenPosition> openPositions = new HashMap<>();
        private final Map<String, BigDecimal[]> excursions = new HashMap<>(); // symbol -> [maeMin, mfeMax]
        private final Map<String, BigDecimal> lastKnownPrice = new HashMap<>();
        private Map<String, StrategyDecision> pendingEntries = new HashMap<>();

        private SimState(BigDecimal initialCapital) {
            this.cash = initialCapital;
            this.equity = initialCapital;
        }
    }
}
