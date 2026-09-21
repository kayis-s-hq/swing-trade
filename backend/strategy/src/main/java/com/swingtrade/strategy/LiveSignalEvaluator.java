package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Live evaluation of a resolved {@link SignalStrategy}: builds a {@link MarketContext} from the
 * candles the live path has and calls {@link SignalStrategy#evaluateEntry}/{@link
 * SignalStrategy#evaluateExit} at the last bar. This is the same call the backtest makes at bar
 * {@code i}, so live and backtest cannot drift (multi-strategy plan design principle 6); the
 * parity is pinned by {@code LiveSignalEvaluatorParityTest}.
 */
public final class LiveSignalEvaluator {

    private LiveSignalEvaluator() {
    }

    /**
     * @param decision the strategy's entry decision for the last bar
     * @param date     date of the evaluated (last) bar
     * @param close    close of the evaluated bar - the reference entry price
     * @param barIndex index of the evaluated bar within the supplied candles
     */
    public record EntryEvaluation(StrategyDecision decision, LocalDate date, BigDecimal close, int barIndex) {
    }

    /**
     * Evaluates entry rules at the last of {@code chronologicalCandles}. Empty when there is not
     * enough history for the strategy's warm-up (the caller reports that as a skip).
     */
    public static Optional<EntryEvaluation> evaluateEntry(String symbol, List<OhlcvCandle> chronologicalCandles,
                                                          ResolvedStrategy.Signal resolved) {
        if (!hasWarmup(chronologicalCandles, resolved)) {
            return Optional.empty();
        }
        int last = chronologicalCandles.size() - 1;
        MarketContext ctx = MarketContext.of(symbol, chronologicalCandles);
        StrategyDecision decision = resolved.strategy().evaluateEntry(ctx, last, resolved.params());
        OhlcvCandle bar = chronologicalCandles.get(last);
        return Optional.of(new EntryEvaluation(decision, bar.date(), bar.close(), last));
    }

    /**
     * Evaluates exit rules at the last bar for an open position described by its entry facts.
     * The entry bar is the last candle on or before {@code entryDate}; the high-water mark is the
     * highest close since then (seeded at {@code entryPrice}).
     */
    public static Optional<ExitDecision> evaluateExit(String symbol, List<OhlcvCandle> chronologicalCandles,
                                                      ResolvedStrategy.Signal resolved, LocalDate entryDate,
                                                      BigDecimal entryPrice, BigDecimal stopLoss,
                                                      BigDecimal target, int quantity) {
        if (!hasWarmup(chronologicalCandles, resolved)) {
            return Optional.empty();
        }
        int last = chronologicalCandles.size() - 1;
        int entryIndex = 0;
        for (int i = last; i >= 0; i--) {
            if (!chronologicalCandles.get(i).date().isAfter(entryDate)) {
                entryIndex = i;
                break;
            }
        }
        BigDecimal highWaterMark = entryPrice;
        for (int i = entryIndex; i <= last; i++) {
            highWaterMark = highWaterMark.max(chronologicalCandles.get(i).close());
        }
        OpenPosition position = new OpenPosition(entryIndex, entryDate, entryPrice, stopLoss, target,
            quantity, highWaterMark);
        MarketContext ctx = MarketContext.of(symbol, chronologicalCandles);
        return Optional.of(resolved.strategy().evaluateExit(ctx, last, position, resolved.params()));
    }

    private static boolean hasWarmup(List<OhlcvCandle> candles, ResolvedStrategy.Signal resolved) {
        return candles != null && candles.size() > resolved.strategy().warmupBars(resolved.params());
    }
}
