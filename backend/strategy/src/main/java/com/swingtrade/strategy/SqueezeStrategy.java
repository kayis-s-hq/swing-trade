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
 * SQUEEZE strategy type (plan §5.3): buy the expansion out of a Bollinger-Band volatility
 * contraction, confirmed by a volume surge and an established uptrend.
 *
 * <p>Squeeze detection has two selectable definitions ({@code squeezeDefinition}):
 * <ul>
 *   <li>{@code BB_PCTILE}: BB(20,2) width's percentile rank over the last
 *       {@code squeezeLookback} bars was &le; {@code squeezePctile} at some point within the last
 *       {@code squeezeRecency} bars</li>
 *   <li>{@code BB_IN_KC}: Bollinger Bands sat entirely inside the Keltner Channel (upper BB &le;
 *       upper KC) at some point within the last {@code squeezeRecency} bars</li>
 * </ul>
 * followed by the expansion trigger on the current bar: close above the upper BB or upper
 * Keltner band, with a volume surge, in an established uptrend (close &gt; EMA50, mandatory).
 */
@Component
public class SqueezeStrategy implements SignalStrategy {

    public static final String TYPE = "SQUEEZE";

    private static final BigDecimal BB_K = BigDecimal.valueOf(2);
    private static final BigDecimal KC_ATR_MULT = BigDecimal.valueOf(1.5);

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ParamSchema paramSchema() {
        return new ParamSchema(List.of(
            new ParamDef("bbPeriod", ParamType.INT, BigDecimal.valueOf(5), BigDecimal.valueOf(60),
                20, "Bollinger Bands period", "volatility"),
            new ParamDef("trendEma", ParamType.INT, BigDecimal.valueOf(10), BigDecimal.valueOf(200),
                50, "EMA period defining the required uptrend", "trend"),
            new ParamDef("squeezeDefinition", ParamType.ENUM, null, null,
                "BB_PCTILE", "Squeeze detection: BB_PCTILE or BB_IN_KC", "volatility"),
            new ParamDef("squeezeLookback", ParamType.INT, BigDecimal.valueOf(20), BigDecimal.valueOf(500),
                120, "Bars over which the BB-width percentile is computed", "volatility"),
            new ParamDef("squeezePctile", ParamType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), "Max BB-width percentile counted as a squeeze", "volatility"),
            new ParamDef("squeezeRecency", ParamType.INT, BigDecimal.ONE, BigDecimal.valueOf(60),
                5, "How many recent bars are searched for a squeeze condition", "volatility"),
            new ParamDef("volumeMaPeriod", ParamType.INT, BigDecimal.valueOf(5), BigDecimal.valueOf(60),
                20, "Volume moving-average period", "volume"),
            new ParamDef("volMult", ParamType.DECIMAL, BigDecimal.valueOf(1.0), BigDecimal.valueOf(4.0),
                BigDecimal.valueOf(1.5), "Volume surge multiplier over its MA", "volume"),
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
            new CrossFieldRule("squeezeDefinition must be BB_PCTILE or BB_IN_KC", p -> {
                Object v = p.get("squeezeDefinition");
                return "BB_PCTILE".equals(v) || "BB_IN_KC".equals(v);
            }),
            new CrossFieldRule("squeezePctile must be in [0,100]", p -> {
                BigDecimal v = toBigDecimal(p.get("squeezePctile"));
                return v.compareTo(BigDecimal.ZERO) >= 0 && v.compareTo(BigDecimal.valueOf(100)) <= 0;
            }),
            new CrossFieldRule("squeezeRecency must be <= squeezeLookback", p ->
                toInt(p.get("squeezeRecency")) <= toInt(p.get("squeezeLookback")))
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
        return EnumSet.of(IndicatorKey.EMA, IndicatorKey.ATR, IndicatorKey.VOLUME, IndicatorKey.VOLUME_MA,
            IndicatorKey.BB_MIDDLE, IndicatorKey.BB_UPPER, IndicatorKey.BB_LOWER, IndicatorKey.KELTNER_UPPER);
    }

    @Override
    public int warmupBars(StrategyParamsView params) {
        return Math.max(params.getInt("trendEma"), params.getInt("squeezeLookback")) + params.getInt("bbPeriod");
    }

    @Override
    public StrategyDecision evaluateEntry(MarketContext ctx, int barIndex, StrategyParamsView params) {
        MarketContext.View view = ctx.view(barIndex);

        int bbPeriod = params.getInt("bbPeriod");
        int trendEma = params.getInt("trendEma");
        String squeezeDefinition = params.getString("squeezeDefinition");
        int squeezeLookback = Math.min(params.getInt("squeezeLookback"), barIndex + 1);
        BigDecimal squeezePctile = params.getDecimal("squeezePctile");
        int squeezeRecency = params.getInt("squeezeRecency");
        int volumeMaPeriod = params.getInt("volumeMaPeriod");
        BigDecimal volMult = params.getDecimal("volMult");

        List<RuleOutcome> rules = new ArrayList<>();

        boolean trendAligned = view.close().compareTo(view.ema(trendEma)) > 0;
        rules.add(new RuleOutcome("trendAligned", trendAligned, BigDecimal.ONE, true,
            "close > EMA" + trendEma));

        boolean squeezed = "BB_IN_KC".equals(squeezeDefinition)
            ? recentBbInsideKeltner(view, barIndex, bbPeriod, squeezeRecency)
            : recentBbWidthPercentileBelow(view, barIndex, bbPeriod, squeezeLookback, squeezeRecency, squeezePctile);
        rules.add(new RuleOutcome("squeezed", squeezed, BigDecimal.ONE, true,
            "squeeze (" + squeezeDefinition + ") within last " + squeezeRecency + " bars"));

        boolean expansion = view.close().compareTo(view.bbUpper(bbPeriod, BB_K)) > 0
            || view.close().compareTo(view.keltnerUpper(bbPeriod, KC_ATR_MULT)) > 0;
        rules.add(new RuleOutcome("expansion", expansion, BigDecimal.ONE, true,
            "close > upper BB or > upper Keltner"));

        boolean volumeSurge = view.volume().compareTo(view.volumeMa(volumeMaPeriod).multiply(volMult)) > 0;
        rules.add(new RuleOutcome("volumeSurge", volumeSurge, BigDecimal.ONE, true,
            "volume > " + volMult + "x volumeMA"));

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
            stop = price.subtract(atr.multiply(params.getDecimal("atrStopMult")));
            BigDecimal risk = price.subtract(stop);
            target = price.add(risk.multiply(params.getDecimal("rewardRisk")));
        }

        return new StrategyDecision(decisionType, score, rules, stop, target, reasoning);
    }

    private boolean recentBbInsideKeltner(MarketContext.View view, int barIndex, int bbPeriod, int recency) {
        int start = Math.max(0, barIndex - recency + 1);
        for (int i = start; i <= barIndex; i++) {
            if (view.bbUpper(bbPeriod, BB_K, i).compareTo(view.keltnerUpper(bbPeriod, KC_ATR_MULT, i)) <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean recentBbWidthPercentileBelow(MarketContext.View view, int barIndex, int bbPeriod,
                                                  int lookback, int recency, BigDecimal pctileThreshold) {
        int start = Math.max(0, barIndex - recency + 1);
        for (int i = start; i <= barIndex; i++) {
            BigDecimal percentile = widthPercentile(view, i, bbPeriod, lookback);
            if (percentile.compareTo(pctileThreshold) <= 0) {
                return true;
            }
        }
        return false;
    }

    /** Percentile rank (0-100) of the width at {@code index} within the trailing {@code lookback} bars. */
    private BigDecimal widthPercentile(MarketContext.View view, int index, int bbPeriod, int lookback) {
        int windowLookback = Math.min(lookback, index + 1);
        int start = Math.max(0, index - windowLookback + 1);
        BigDecimal currentWidth = view.bbWidth(bbPeriod, BB_K, index);
        int countLessOrEqual = 0;
        int total = 0;
        for (int i = start; i <= index; i++) {
            total++;
            if (view.bbWidth(bbPeriod, BB_K, i).compareTo(currentWidth) <= 0) {
                countLessOrEqual++;
            }
        }
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(countLessOrEqual)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
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
