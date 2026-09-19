package com.swingtrade.strategy;

import java.math.BigDecimal;

/**
 * Exit logic shared by every {@link SignalStrategy} type (plan §3.3): ATR stop, reward:risk
 * target, optional signal exit, optional trailing stop, and a max-holding-days time stop.
 *
 * <p>Precedence, checked in this order every bar: {@link ExitReason#STOP_LOSS} then
 * {@link ExitReason#TARGET_HIT} then {@link ExitReason#SIGNAL_EXIT} then
 * {@link ExitReason#TRAILING_STOP} then {@link ExitReason#TIME_STOP}. When a bar's low breaches the
 * stop <em>and</em> its high reaches the target in the same bar, {@link ExitReason#STOP_LOSS} is
 * returned - the conservative assumption that the stop was touched first intrabar.
 *
 * <p>Required params (read via {@link StrategyParamsView}): {@code trailAtrMult} (0 disables the
 * trailing stop) and {@code atrPeriod} (only read when {@code trailAtrMult > 0}), and
 * {@code maxHoldDays}.
 */
public final class UniformExitEvaluator {

    private UniformExitEvaluator() {
    }

    /**
     * @param view                the bar to evaluate the position against (bounded at the
     *                            current bar - see {@link MarketContext#view(int)})
     * @param position            the open position
     * @param params              resolved strategy params
     * @param signalExitTriggered whether the strategy's own (type-specific) signal-exit rule
     *                            fired on this bar
     */
    public static ExitDecision evaluate(MarketContext.View view, OpenPosition position,
                                         StrategyParamsView params, boolean signalExitTriggered) {
        BigDecimal low = view.low();
        BigDecimal high = view.high();
        BigDecimal open = view.open();
        BigDecimal close = view.close();

        if (low.compareTo(position.stopLoss()) <= 0) {
            BigDecimal exitPrice = open.compareTo(position.stopLoss()) <= 0 ? open : position.stopLoss();
            return ExitDecision.exit(ExitReason.STOP_LOSS, exitPrice,
                "Bar low " + low + " breached stop loss " + position.stopLoss());
        }

        if (high.compareTo(position.target()) >= 0) {
            BigDecimal exitPrice = open.compareTo(position.target()) >= 0 ? open : position.target();
            return ExitDecision.exit(ExitReason.TARGET_HIT, exitPrice,
                "Bar high " + high + " reached target " + position.target());
        }

        if (signalExitTriggered) {
            return ExitDecision.exit(ExitReason.SIGNAL_EXIT, close, "Strategy signal-exit rule triggered");
        }

        BigDecimal trailAtrMult = params.getDecimal("trailAtrMult");
        if (trailAtrMult.signum() > 0) {
            int atrPeriod = params.getInt("atrPeriod");
            BigDecimal atr = view.atr(atrPeriod);
            BigDecimal highWaterMark = position.highWaterMark().max(close);
            BigDecimal trailingStop = highWaterMark.subtract(atr.multiply(trailAtrMult));
            if (close.compareTo(trailingStop) <= 0) {
                return ExitDecision.exit(ExitReason.TRAILING_STOP, close,
                    "Close " + close + " breached trailing stop " + trailingStop);
            }
        }

        int maxHoldDays = params.getInt("maxHoldDays");
        if ((view.barIndex() - position.entryIndex()) >= maxHoldDays) {
            return ExitDecision.exit(ExitReason.TIME_STOP, close,
                "Held " + (view.barIndex() - position.entryIndex()) + " bars, max is " + maxHoldDays);
        }

        return ExitDecision.hold();
    }
}
