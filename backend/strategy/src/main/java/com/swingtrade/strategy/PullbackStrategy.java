package com.swingtrade.strategy;

import com.swingtrade.domain.Signal.SignalType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PULLBACK strategy type (plan §5.2): buy a shallow dip back to the 20-EMA inside an established
 * 50-EMA uptrend, confirmed by RSI recovering out of a dip band.
 *
 * <p>Rules:
 * <ul>
 *   <li><b>mandatory</b> {@code trendUp}: close &gt; EMA(trendEma) and EMA(trendEma) has risen
 *       over the last {@code slopeLookback} bars</li>
 *   <li>{@code touchedPullbackEma}: the low came within {@code touchPct} of EMA(pullbackEma) at
 *       some point in the last {@code touchLookback} bars</li>
 *   <li>{@code rsiRecovered}: RSI dipped below {@code rsiDip} and has since crossed back above
 *       {@code rsiTrigger}, within the last {@code touchLookback} bars</li>
 *   <li>{@code confirmation} (non-mandatory): today's close &gt; prior bar's high</li>
 * </ul>
 *
 * <p>Suggested stop (overrides the generic ATR-only stop other types use):
 * {@code min(swing low of last touchLookback bars, entry - atrStopMult*ATR)} per plan §5.2.
 */
@Component
public class PullbackStrategy implements SignalStrategy {

    public static final String TYPE = "PULLBACK";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ParamSchema paramSchema() {
        return new ParamSchema(List.of(
            new ParamDef("trendEma", ParamType.INT, BigDecimal.valueOf(10), BigDecimal.valueOf(200),
                50, "EMA period defining the primary uptrend", "trend"),
            new ParamDef("pullbackEma", ParamType.INT, BigDecimal.valueOf(5), BigDecimal.valueOf(100),
                20, "EMA period the pullback is measured against", "trend"),
            new ParamDef("touchPct", ParamType.DECIMAL, BigDecimal.valueOf(0.001), BigDecimal.valueOf(0.10),
                BigDecimal.valueOf(0.01), "Max proximity of low to pullbackEma to count as a touch", "trend"),
            new ParamDef("touchLookback", ParamType.INT, BigDecimal.ONE, BigDecimal.valueOf(30),
                3, "Bars to look back for a pullback-EMA touch and RSI recovery", "trend"),
            new ParamDef("rsiPeriod", ParamType.INT, BigDecimal.valueOf(2), BigDecimal.valueOf(50),
                14, "RSI lookback period", "momentum"),
            new ParamDef("rsiDip", ParamType.DECIMAL, BigDecimal.valueOf(10), BigDecimal.valueOf(50),
                BigDecimal.valueOf(40), "RSI level that must have been dipped below", "momentum"),
            new ParamDef("rsiTrigger", ParamType.DECIMAL, BigDecimal.valueOf(20), BigDecimal.valueOf(70),
                BigDecimal.valueOf(45), "RSI level the recovery must cross back above", "momentum"),
            new ParamDef("slopeLookback", ParamType.INT, BigDecimal.ONE, BigDecimal.valueOf(60),
                10, "Bars over which trendEma's slope must be positive", "trend"),
            new ParamDef("requireConfirmation", ParamType.BOOL, null, null,
                Boolean.FALSE, "If true, close > prior high becomes mandatory instead of scored", "confirmation"),
            new ParamDef("entryScoreThreshold", ParamType.DECIMAL, BigDecimal.valueOf(0.5), BigDecimal.ONE,
                BigDecimal.valueOf(0.75), "Minimum score to fire a BUY", "scoring"),
            new ParamDef("atrPeriod", ParamType.INT, BigDecimal.valueOf(2), BigDecimal.valueOf(50),
                14, "ATR lookback period", "exits"),
            new ParamDef("atrStopMult", ParamType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(10),
                BigDecimal.valueOf(2), "ATR multiplier for the stop distance", "exits"),
            new ParamDef("rewardRisk", ParamType.DECIMAL, BigDecimal.valueOf(0.5), BigDecimal.valueOf(10),
                BigDecimal.valueOf(2), "Reward:risk ratio for the target", "exits"),
            new ParamDef("maxHoldDays", ParamType.INT, BigDecimal.ONE, BigDecimal.valueOf(120),
                20, "Maximum holding period in bars", "exits"),
            new ParamDef("trailAtrMult", ParamType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(10),
                BigDecimal.ZERO, "ATR multiplier for the trailing stop (0 disables it)", "exits")
        ));
    }

    @Override
    public List<CrossFieldRule> crossFieldRules() {
        return List.of(
            new CrossFieldRule("pullbackEma must be less than trendEma", p ->
                toInt(p.get("pullbackEma")) < toInt(p.get("trendEma"))),
            new CrossFieldRule("rsiDip must be less than rsiTrigger", p ->
                toBigDecimal(p.get("rsiDip")).compareTo(toBigDecimal(p.get("rsiTrigger"))) < 0),
            new CrossFieldRule("touchLookback must be positive", p ->
                toInt(p.get("touchLookback")) > 0),
            new CrossFieldRule("slopeLookback must be positive", p ->
                toInt(p.get("slopeLookback")) > 0)
        );
    }

    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    @Override
    public Set<IndicatorKey> requiredIndicators(StrategyParamsView params) {
        return EnumSet.of(IndicatorKey.EMA, IndicatorKey.RSI, IndicatorKey.ATR, IndicatorKey.LOWEST_LOW);
    }

    @Override
    public int warmupBars(StrategyParamsView params) {
        return params.getInt("trendEma") + params.getInt("slopeLookback");
    }

    @Override
    public StrategyDecision evaluateEntry(MarketContext ctx, int barIndex, StrategyParamsView params) {
        MarketContext.View view = ctx.view(barIndex);

        int trendEma = params.getInt("trendEma");
        int pullbackEma = params.getInt("pullbackEma");
        int touchLookback = params.getInt("touchLookback");
        int slopeLookback = params.getInt("slopeLookback");
        int rsiPeriod = params.getInt("rsiPeriod");
        BigDecimal touchPct = params.getDecimal("touchPct");
        BigDecimal rsiDip = params.getDecimal("rsiDip");
        BigDecimal rsiTrigger = params.getDecimal("rsiTrigger");
        boolean requireConfirmation = params.getBoolean("requireConfirmation");

        List<RuleOutcome> rules = new ArrayList<>();

        boolean closeAboveTrend = view.close().compareTo(view.ema(trendEma)) > 0;
        int slopeStartIndex = Math.max(0, barIndex - slopeLookback);
        boolean trendSlopeUp = view.ema(trendEma).compareTo(view.ema(trendEma, slopeStartIndex)) > 0;
        boolean trendUp = closeAboveTrend && trendSlopeUp;
        rules.add(new RuleOutcome("trendUp", trendUp, BigDecimal.ONE, true,
            "close > EMA" + trendEma + " and EMA" + trendEma + " slope up over " + slopeLookback + " bars"));

        boolean touchedPullbackEma = false;
        int touchStartIndex = Math.max(0, barIndex - touchLookback + 1);
        for (int i = touchStartIndex; i <= barIndex; i++) {
            BigDecimal ema = view.ema(pullbackEma, i);
            BigDecimal low = view.low(i);
            BigDecimal proximity = ema.subtract(low).abs()
                .divide(ema.signum() == 0 ? BigDecimal.ONE : ema, 10, RoundingMode.HALF_UP);
            if (proximity.compareTo(touchPct) <= 0) {
                touchedPullbackEma = true;
                break;
            }
        }
        rules.add(new RuleOutcome("touchedPullbackEma", touchedPullbackEma, BigDecimal.ONE, false,
            "low within " + touchPct + " of EMA" + pullbackEma + " in last " + touchLookback + " bars"));

        boolean rsiRecovered = false;
        boolean sawDip = false;
        for (int i = touchStartIndex; i <= barIndex; i++) {
            BigDecimal rsi = view.rsi(rsiPeriod, i);
            if (!sawDip && rsi.compareTo(rsiDip) < 0) {
                sawDip = true;
            } else if (sawDip && rsi.compareTo(rsiTrigger) >= 0) {
                rsiRecovered = true;
                break;
            }
        }
        rules.add(new RuleOutcome("rsiRecovered", rsiRecovered, BigDecimal.ONE, false,
            "RSI dipped below " + rsiDip + " then crossed back above " + rsiTrigger
                + " within last " + touchLookback + " bars"));

        boolean confirmation = barIndex > 0 && view.close().compareTo(view.high(barIndex - 1)) > 0;
        rules.add(new RuleOutcome("confirmation", confirmation, BigDecimal.ONE, requireConfirmation,
            "close > prior bar's high"));

        BigDecimal score = score(rules);
        boolean mandatoryPass = rules.stream().filter(RuleOutcome::mandatory).allMatch(RuleOutcome::passed);
        boolean buy = mandatoryPass && score.compareTo(params.getDecimal("entryScoreThreshold")) >= 0;

        SignalType decisionType = buy ? SignalType.BUY : SignalType.HOLD;
        String reasoning = buy
            ? "All entry rules passed"
            : "Entry rules failed: " + rules.stream()
                .filter(rule -> !rule.passed())
                .map(RuleOutcome::key)
                .collect(Collectors.joining(", "));

        BigDecimal stop = null;
        BigDecimal target = null;
        if (buy) {
            BigDecimal price = view.close();
            BigDecimal atr = view.atr(params.getInt("atrPeriod"));
            BigDecimal atrStop = price.subtract(atr.multiply(params.getDecimal("atrStopMult")));
            BigDecimal swingLow = view.lowestLow(touchLookback);
            stop = swingLow.min(atrStop);
            BigDecimal risk = price.subtract(stop);
            target = price.add(risk.multiply(params.getDecimal("rewardRisk")));
        }

        return new StrategyDecision(decisionType, score, rules, stop, target, reasoning);
    }

    @Override
    public ExitDecision evaluateExit(MarketContext ctx, int barIndex, OpenPosition position,
                                      StrategyParamsView params) {
        MarketContext.View view = ctx.view(barIndex);
        int trendEma = params.getInt("trendEma");
        boolean signalExit = view.close().compareTo(view.ema(trendEma)) < 0;
        return UniformExitEvaluator.evaluate(view, position, params, signalExit);
    }

    private BigDecimal score(List<RuleOutcome> rules) {
        BigDecimal totalWeight = rules.stream().map(RuleOutcome::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal passedWeight = rules.stream()
            .filter(RuleOutcome::passed)
            .map(RuleOutcome::weight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return passedWeight.divide(totalWeight, 6, RoundingMode.HALF_UP);
    }
}
