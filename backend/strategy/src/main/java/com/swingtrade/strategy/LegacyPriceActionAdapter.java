package com.swingtrade.strategy;

import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.StrategyParams;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wraps the current, hardcoded price-action rules ({@link PriceActionStrategy} /
 * {@link PriceActionSignalEngine}) behind the new {@link SignalStrategy} SPI as type
 * {@value #TYPE}, with params equal to {@link StrategyParams}' constants and an
 * {@code entryScoreThreshold} of 1.0 - i.e. the same "4 of 4" confluence the live engine
 * enforces today (plan §3.4).
 *
 * <p>This adapter is additive only: {@link PriceActionSignalEngine} keeps running unchanged for
 * live signal generation and the existing backtest path. Nothing in this phase switches
 * production over to {@link SignalStrategy}/{@link MarketContext} - that is a later phase (plan
 * §9, order 2+). The adapter exists so the golden parity test
 * ({@code LegacyPriceActionAdapterGoldenParityTest}) can prove the new SPI reproduces today's
 * decisions exactly before anything is switched over.
 */
@Component
public class LegacyPriceActionAdapter implements SignalStrategy {

    public static final String TYPE = "BREAKOUT";

    private final TradingStrategy legacyRules;

    public LegacyPriceActionAdapter(PriceActionStrategy legacyRules) {
        this.legacyRules = legacyRules;
    }

    /**
     * Default params: exactly {@link StrategyParams}' hardcoded constants, plus exit params
     * (ATR stop, reward:risk, max hold, trailing) that {@link PriceActionSignalEngine} computes
     * (ATR) or doesn't use at all today (reward:risk/max hold/trailing) - those exit defaults are
     * new in this phase (plan finding F12: "ATR14 computed in live engine but unused for stops
     * live") and are deliberately conservative placeholders; they are not part of the golden
     * parity requirement, which only covers entry/signal-exit classification, not stop/target
     * levels.
     */
    public static StrategyParamsView defaultParams() {
        Map<String, Object> values = new HashMap<>();
        values.put("emaFast", StrategyParams.EMA_FAST);
        values.put("emaSlow", StrategyParams.EMA_SLOW);
        values.put("rsiPeriod", StrategyParams.RSI_PERIOD);
        values.put("rsiMin", StrategyParams.RSI_LOWER);
        values.put("rsiMax", StrategyParams.RSI_UPPER);
        values.put("volumeMaPeriod", StrategyParams.VOLUME_MA_PERIOD);
        values.put("volMult", StrategyParams.VOLUME_MULTIPLIER);
        values.put("weeklyHighPeriod", StrategyParams.FIFTY_TWO_WEEK_TRADING_DAYS);
        values.put("highProximity", StrategyParams.HIGH_PROXIMITY);
        values.put("entryScoreThreshold", BigDecimal.ONE);
        values.put("atrPeriod", StrategyParams.ATR_PERIOD);
        values.put("atrStopMult", BigDecimal.valueOf(2));
        values.put("rewardRisk", BigDecimal.valueOf(2));
        values.put("maxHoldDays", 20);
        values.put("trailAtrMult", BigDecimal.ZERO);
        return StrategyParamsView.of(values);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ParamSchema paramSchema() {
        return new ParamSchema(List.of(
            new ParamDef("emaFast", ParamType.INT, BigDecimal.valueOf(5), BigDecimal.valueOf(100),
                StrategyParams.EMA_FAST, "Fast EMA period", "trend"),
            new ParamDef("emaSlow", ParamType.INT, BigDecimal.valueOf(20), BigDecimal.valueOf(250),
                StrategyParams.EMA_SLOW, "Slow EMA period", "trend"),
            new ParamDef("rsiPeriod", ParamType.INT, BigDecimal.valueOf(2), BigDecimal.valueOf(50),
                StrategyParams.RSI_PERIOD, "RSI lookback period", "momentum"),
            new ParamDef("rsiMin", ParamType.DECIMAL, BigDecimal.valueOf(30), BigDecimal.valueOf(80),
                StrategyParams.RSI_LOWER, "RSI entry band lower bound", "momentum"),
            new ParamDef("rsiMax", ParamType.DECIMAL, BigDecimal.valueOf(30), BigDecimal.valueOf(80),
                StrategyParams.RSI_UPPER, "RSI entry band upper bound", "momentum"),
            new ParamDef("volumeMaPeriod", ParamType.INT, BigDecimal.valueOf(5), BigDecimal.valueOf(60),
                StrategyParams.VOLUME_MA_PERIOD, "Volume moving-average period", "volume"),
            new ParamDef("volMult", ParamType.DECIMAL, BigDecimal.valueOf(1.0), BigDecimal.valueOf(4.0),
                StrategyParams.VOLUME_MULTIPLIER, "Volume surge multiplier over its MA", "volume"),
            new ParamDef("weeklyHighPeriod", ParamType.INT, BigDecimal.valueOf(20), BigDecimal.valueOf(300),
                StrategyParams.FIFTY_TWO_WEEK_TRADING_DAYS, "Lookback for the weekly (52w) high", "trend"),
            new ParamDef("highProximity", ParamType.DECIMAL, BigDecimal.valueOf(0.85), BigDecimal.valueOf(1.0),
                StrategyParams.HIGH_PROXIMITY, "Required proximity to the weekly high", "trend"),
            new ParamDef("entryScoreThreshold", ParamType.DECIMAL, BigDecimal.valueOf(0.5), BigDecimal.ONE,
                BigDecimal.ONE, "Minimum score to fire a BUY", "scoring"),
            new ParamDef("atrPeriod", ParamType.INT, BigDecimal.valueOf(2), BigDecimal.valueOf(50),
                StrategyParams.ATR_PERIOD, "ATR lookback period", "exits"),
            new ParamDef("atrStopMult", ParamType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(10),
                BigDecimal.valueOf(2), "ATR multiplier for the stop distance", "exits"),
            new ParamDef("rewardRisk", ParamType.DECIMAL, BigDecimal.valueOf(0.5), BigDecimal.valueOf(10),
                BigDecimal.valueOf(2), "Reward:risk ratio for the target", "exits"),
            new ParamDef("maxHoldDays", ParamType.INT, BigDecimal.valueOf(1), BigDecimal.valueOf(120),
                20, "Maximum holding period in bars", "exits"),
            new ParamDef("trailAtrMult", ParamType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(10),
                BigDecimal.ZERO, "ATR multiplier for the trailing stop (0 disables it)", "exits")
        ));
    }

    @Override
    public List<CrossFieldRule> crossFieldRules() {
        return List.of(
            new CrossFieldRule("emaFast must be less than emaSlow", p ->
                toBigDecimal(p.get("emaFast")).compareTo(toBigDecimal(p.get("emaSlow"))) < 0),
            new CrossFieldRule("rsiMin must be less than rsiMax", p ->
                toBigDecimal(p.get("rsiMin")).compareTo(toBigDecimal(p.get("rsiMax"))) < 0)
        );
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    @Override
    public Set<IndicatorKey> requiredIndicators(StrategyParamsView params) {
        return EnumSet.of(IndicatorKey.EMA, IndicatorKey.RSI, IndicatorKey.ATR,
            IndicatorKey.VOLUME, IndicatorKey.VOLUME_MA, IndicatorKey.HIGHEST_HIGH);
    }

    @Override
    public int warmupBars(StrategyParamsView params) {
        return Math.max(params.getInt("emaSlow"), params.getInt("weeklyHighPeriod"));
    }

    @Override
    public StrategyDecision evaluateEntry(MarketContext ctx, int barIndex, StrategyParamsView params) {
        MarketContext.View view = ctx.view(barIndex);
        Indicators indicators = readIndicators(view, params);

        List<RuleOutcome> rules = new ArrayList<>();
        boolean trendAligned = legacyRules.trendAligned(indicators);
        rules.add(new RuleOutcome("trendAligned", trendAligned, BigDecimal.ONE, true,
            "price>ema" + params.getInt("emaFast") + ">ema" + params.getInt("emaSlow")));

        boolean rsiInRange = legacyRules.rsiInEntryRange(indicators);
        rules.add(new RuleOutcome("rsiInRange", rsiInRange, BigDecimal.ONE, true,
            "rsi in [" + params.getDecimal("rsiMin") + "," + params.getDecimal("rsiMax") + "]"));

        boolean volumeSurge = legacyRules.volumeSurge(indicators);
        rules.add(new RuleOutcome("volumeSurge", volumeSurge, BigDecimal.ONE, true,
            "volume > " + params.getDecimal("volMult") + "x volumeMA"));

        boolean nearWeeklyHigh = legacyRules.nearWeeklyHigh(indicators);
        rules.add(new RuleOutcome("nearWeeklyHigh", nearWeeklyHigh, BigDecimal.ONE, true,
            "price >= " + params.getDecimal("highProximity") + "x weeklyHigh"));

        BigDecimal score = score(rules);
        boolean mandatoryPass = rules.stream().filter(RuleOutcome::mandatory).allMatch(RuleOutcome::passed);
        boolean buy = mandatoryPass && score.compareTo(params.getDecimal("entryScoreThreshold")) >= 0;

        SignalType type = buy ? SignalType.BUY : SignalType.HOLD;
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

        return new StrategyDecision(type, score, rules, stop, target, reasoning);
    }

    @Override
    public ExitDecision evaluateExit(MarketContext ctx, int barIndex, OpenPosition position,
                                      StrategyParamsView params) {
        MarketContext.View view = ctx.view(barIndex);
        boolean signalExitTriggered = legacyRules.isSignalExit(exitIndicators(view, params));
        return UniformExitEvaluator.evaluate(view, position, params, signalExitTriggered);
    }

    /**
     * Test/golden-parity helper reproducing {@link PriceActionSignalEngine#analyze}'s exact
     * per-bar classification (BUY / SELL / HOLD) with no open-position tracking. Not part of the
     * {@link SignalStrategy} SPI: the legacy engine's per-bar classification checks the "signal
     * exit" rule confluence even when there is no open position, which the new SPI's
     * {@link #evaluateExit} (deliberately position-scoped) does not do. Package-private:
     * consumed only by the golden parity test.
     */
    SignalType classifyLegacyStyle(MarketContext ctx, int barIndex, StrategyParamsView params) {
        StrategyDecision entry = evaluateEntry(ctx, barIndex, params);
        if (entry.type() == SignalType.BUY) {
            return SignalType.BUY;
        }
        MarketContext.View view = ctx.view(barIndex);
        boolean signalExit = legacyRules.isSignalExit(exitIndicators(view, params));
        return signalExit ? SignalType.SELL : SignalType.HOLD;
    }

    private Indicators readIndicators(MarketContext.View view, StrategyParamsView params) {
        BigDecimal price = view.close();
        BigDecimal ema20 = view.ema(params.getInt("emaFast"));
        BigDecimal ema50 = view.ema(params.getInt("emaSlow"));
        BigDecimal rsi = view.rsi(params.getInt("rsiPeriod"));
        BigDecimal volume = view.volume();
        BigDecimal volumeMa = view.volumeMa(params.getInt("volumeMaPeriod"));
        int weeklyHighPeriod = Math.min(params.getInt("weeklyHighPeriod"), view.barIndex() + 1);
        BigDecimal weeklyHigh = view.highestHigh(weeklyHighPeriod);
        return new Indicators(price, ema20, ema50, rsi, volume, volumeMa, weeklyHigh);
    }

    private Indicators exitIndicators(MarketContext.View view, StrategyParamsView params) {
        BigDecimal price = view.close();
        BigDecimal ema20 = view.ema(params.getInt("emaFast"));
        BigDecimal ema50 = view.ema(params.getInt("emaSlow"));
        BigDecimal rsi = view.rsi(params.getInt("rsiPeriod"));
        return new Indicators(price, ema20, ema50, rsi, null, null, null);
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
