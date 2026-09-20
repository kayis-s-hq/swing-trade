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

package com.swingtrade.strategy;

import com.swingtrade.core.metrics.SignalMetrics;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.OhlcvDataQuality;
import com.swingtrade.domain.MarketRegimeAssessment;
import com.swingtrade.domain.RelativeStrengthAssessment;
import com.swingtrade.domain.policy.MarketRegimePolicy;
import com.swingtrade.domain.policy.RelativeStrengthPolicy;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.StrategyParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2 price-action strategy engine.
 * Loads OHLCV history for a symbol, computes EMA20/EMA50/RSI14/ATR14/VolumeMA20 via TA4J,
 * and applies the swing-trade entry rules to produce a {@link SignalResult}.
 */
@Component
public class PriceActionSignalEngine {

    private static final Logger logger = LoggerFactory.getLogger(PriceActionSignalEngine.class);

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String MARKET_INDEX_SYMBOL = "NIFTY50";

    // Package-private (not private): reused directly by BacktestEngine so the backtest's
    // entry rules can never drift from the live signal engine's thresholds.
    static final int EMA_FAST_PERIOD = StrategyParams.EMA_FAST;
    static final int EMA_SLOW_PERIOD = StrategyParams.EMA_SLOW;
    static final int RSI_PERIOD = StrategyParams.RSI_PERIOD;
    static final int ATR_PERIOD = StrategyParams.ATR_PERIOD;
    static final int VOLUME_MA_PERIOD = StrategyParams.VOLUME_MA_PERIOD;
    static final int FIFTY_TWO_WEEK_TRADING_DAYS = StrategyParams.FIFTY_TWO_WEEK_TRADING_DAYS;

    static final BigDecimal RSI_LOWER_BOUND = StrategyParams.RSI_LOWER;
    static final BigDecimal RSI_UPPER_BOUND = StrategyParams.RSI_UPPER;
    static final BigDecimal VOLUME_MULTIPLIER = StrategyParams.VOLUME_MULTIPLIER;
    static final BigDecimal HIGH_PROXIMITY_THRESHOLD = StrategyParams.HIGH_PROXIMITY;

    static final int MIN_REQUIRED_CANDLES = StrategyParams.MIN_CANDLES;
    static final BigDecimal MAX_ANALYTICAL_GAP_RATIO = new BigDecimal("0.75");

    private final CandleStore candleStore;
    private final SignalMetrics signalMetrics;
    private final TradingStrategy strategy;
    private final MarketRegimePolicy marketRegimePolicy;
    private final RelativeStrengthPolicy relativeStrengthPolicy;

    public PriceActionSignalEngine(CandleStore candleStore, SignalMetrics signalMetrics, PriceActionStrategy strategy) {
        this(candleStore, signalMetrics, strategy, new BoundedMarketRegimePolicy(), new BoundedRelativeStrengthPolicy());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PriceActionSignalEngine(CandleStore candleStore, SignalMetrics signalMetrics, PriceActionStrategy strategy,
                                   MarketRegimePolicy marketRegimePolicy) {
        this(candleStore, signalMetrics, strategy, marketRegimePolicy, new BoundedRelativeStrengthPolicy());
    }

    public PriceActionSignalEngine(CandleStore candleStore, SignalMetrics signalMetrics, PriceActionStrategy strategy,
                                   MarketRegimePolicy marketRegimePolicy,
                                   RelativeStrengthPolicy relativeStrengthPolicy) {
        this.candleStore = candleStore;
        this.signalMetrics = signalMetrics;
        this.strategy = strategy;
        this.marketRegimePolicy = marketRegimePolicy;
        this.relativeStrengthPolicy = relativeStrengthPolicy;
    }

    /**
     * Analyzes the latest candle history for a symbol and produces a signal result.
     *
     * @param symbol the stock symbol to analyze
     * @return the resulting signal with the indicator readings that produced it
     * @throws IllegalArgumentException if symbol is null/blank
     * @throws IllegalStateException    if there isn't enough candle history to compute EMA50
     */
    public SignalResult generateSignal(String symbol) {
        return generateSignal(symbol, strategy);
    }

    /**
     * Generates a signal using an explicitly selected strategy. The indicator
     * calculation remains shared with the default live path; only rule
     * evaluation is selected per call.
     */
    public SignalResult generateSignal(String symbol, TradingStrategy selectedStrategy) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (selectedStrategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }

        var candles = BacktestEngine.getDescendingCandles(symbol, candleStore, MIN_REQUIRED_CANDLES);

        return analyze(symbol, candles, selectedStrategy);
    }

    /**
     * Analyzes a chronologically-ordered (oldest first) list of candles for a symbol.
     *
     * @param symbol               the stock symbol
     * @param chronologicalCandles candles ordered oldest to newest
     * @return the resulting signal with the indicator readings that produced it
     */
    public SignalResult analyze(String symbol, List<OhlcvCandle> chronologicalCandles) {
        return analyze(symbol, chronologicalCandles, strategy);
    }

    /** Analyzes candles with an explicitly selected strategy. */
    public SignalResult analyze(String symbol, List<OhlcvCandle> chronologicalCandles,
                                TradingStrategy selectedStrategy) {
        if (selectedStrategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        BarSeries series = buildBarSeries(symbol, chronologicalCandles);
        int lastIndex = series.getBarCount() - 1;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema20Indicator = new EMAIndicator(closePrice, EMA_FAST_PERIOD);
        EMAIndicator ema50Indicator = new EMAIndicator(closePrice, EMA_SLOW_PERIOD);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, RSI_PERIOD);
        ATRIndicator atrIndicator = new ATRIndicator(series, ATR_PERIOD);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        SMAIndicator volumeMaIndicator = new SMAIndicator(volumeIndicator, VOLUME_MA_PERIOD);
        int weeklyHighPeriod = Math.min(FIFTY_TWO_WEEK_TRADING_DAYS, series.getBarCount());
        HighestValueIndicator weeklyHighIndicator =
            new HighestValueIndicator(new HighPriceIndicator(series), weeklyHighPeriod);

        BigDecimal price = numToBigDecimal(closePrice.getValue(lastIndex));
        BigDecimal ema20 = numToBigDecimal(ema20Indicator.getValue(lastIndex));
        BigDecimal ema50 = numToBigDecimal(ema50Indicator.getValue(lastIndex));
        BigDecimal rsi = numToBigDecimal(rsiIndicator.getValue(lastIndex));
        BigDecimal atr = numToBigDecimal(atrIndicator.getValue(lastIndex));
        BigDecimal volume = numToBigDecimal(volumeIndicator.getValue(lastIndex));
        BigDecimal volumeMa = numToBigDecimal(volumeMaIndicator.getValue(lastIndex));
        BigDecimal weeklyHigh = numToBigDecimal(weeklyHighIndicator.getValue(lastIndex));
        LocalDate date = chronologicalCandles.get(chronologicalCandles.size() - 1).date();

        Indicators indicators = new Indicators(price, ema20, ema50, rsi, volume, volumeMa, weeklyHigh);

        MarketRegimeAssessment regime = selectedStrategy.regimeFilterEnabled()
            ? assessMarketRegime()
            : null;
        RelativeStrengthAssessment relativeStrength = selectedStrategy.relativeStrengthFilterEnabled()
            ? assessRelativeStrength(chronologicalCandles, date)
            : null;

        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        boolean trendAligned = selectedStrategy.trendAligned(indicators);
        recordRule(trendAligned, passed, failed,
            "Price > EMA20 > EMA50 (price=" + fmt(price) + ", ema20=" + fmt(ema20) + ", ema50=" + fmt(ema50) + ")");

        boolean rsiInRange = selectedStrategy.rsiInEntryRange(indicators);
        recordRule(rsiInRange, passed, failed, selectedStrategy.entryRsiDescription() + " (rsi=" + fmt(rsi) + ")");

        boolean volumeSurge = selectedStrategy.volumeSurge(indicators);
        recordRule(volumeSurge, passed, failed,
            "Volume > 1.5x VolumeMA20 (volume=" + fmt(volume) + ", threshold=" + fmt(volumeMa.multiply(VOLUME_MULTIPLIER)) + ")");

        boolean nearWeeklyHigh = selectedStrategy.nearWeeklyHigh(indicators);
        recordRule(nearWeeklyHigh, passed, failed,
            "Price within 3% of 52-week high (price=" + fmt(price) + ", 52wHigh=" + fmt(weeklyHigh) + ")");

        int rulesPassed = (trendAligned ? 1 : 0) + (rsiInRange ? 1 : 0) + (volumeSurge ? 1 : 0) + (nearWeeklyHigh ? 1 : 0);
        // BacktestEngine.tryEnter evaluates entry through the same TradingStrategy instance so
        // the backtest can never drift from this strategy's confluence or thresholds.
        boolean technicalEntry = selectedStrategy.isEntrySignal(indicators);
        boolean enoughRulesPassed = selectedStrategy.isEntryEligible(indicators, regime, relativeStrength);
        if (technicalEntry && !enoughRulesPassed) {
            String regimeReason = regime == null ? "REGIME_ASSESSMENT_UNAVAILABLE" : regime.reason();
            if (selectedStrategy.regimeFilterEnabled() && (regime == null || !regime.eligible())) {
                failed.add("Market regime gate failed (" + regimeReason + ")");
            }
            if (selectedStrategy.relativeStrengthFilterEnabled()
                && (relativeStrength == null || !relativeStrength.eligible())) {
                String reason = relativeStrength == null ? "INDEX_DATA_UNAVAILABLE" : relativeStrength.reason();
                failed.add("Relative-strength gate failed (" + reason + ")");
            }
        }

        SignalType type;
        String reasoning;
        if (enoughRulesPassed) {
            type = SignalType.BUY;
            reasoning = "%s passed: %s".formatted(selectedStrategy.entryConfluenceDescription(),
                String.join("; ", passed));
        } else {
            // Exit confluence: ANY 1 of 3 trend/RSI conditions fires a SELL — deliberately looser
            // than the strict "all 4 of 4" entry confluence ("enter carefully, exit quickly").
            // Conditions A and C are the literal inverse of two of the four entry sub-conditions
            // above, so BUY and SELL can never both be true for the same candle.
            List<String> exitPassed = new ArrayList<>();
            List<String> exitFailed = new ArrayList<>();

            boolean closeBelowEma20 = selectedStrategy.closeBelowEma20(indicators);
            recordRule(closeBelowEma20, exitPassed, exitFailed,
                "Close < EMA20 (close=%s, ema20=%s)".formatted(fmt(price), fmt(ema20)));

            boolean ema20BelowEma50 = selectedStrategy.ema20BelowEma50(indicators);
            recordRule(ema20BelowEma50, exitPassed, exitFailed,
                "EMA20 < EMA50 (ema20=%s, ema50=%s)".formatted(fmt(ema20), fmt(ema50)));

            boolean rsiBelowLowerBound = selectedStrategy.rsiBelowLowerBound(indicators);
            recordRule(rsiBelowLowerBound, exitPassed, exitFailed,
                "RSI < 50 (rsi=%s)".formatted(fmt(rsi)));

            if (selectedStrategy.isSignalExit(indicators)) {
                type = SignalType.SELL;
                reasoning = "Exit rule triggered (%d of 3): %s".formatted(exitPassed.size(), String.join("; ", exitPassed));
            } else {
                type = SignalType.HOLD;
                reasoning = "Entry rules failed (%d of 4 passed; required %d): %s".formatted(
                    rulesPassed, selectedStrategy.requiredEntryRules(), String.join("; ", failed));
            }
        }

        logger.debug("Signal for {} on {}: {} ({})", symbol, date, type, reasoning);

        signalMetrics.recordSignalGenerated();
        signalMetrics.recordSignalType(type.name().toLowerCase());

        return new SignalResult(symbol, date, type, rsi, ema20, ema50, atr, reasoning);
    }

    private MarketRegimeAssessment assessMarketRegime() {
        try {
            MarketRegimeAssessment assessment = marketRegimePolicy.assess(
                candleStore.findTopBySymbolOrderByDateDesc(MARKET_INDEX_SYMBOL,
                    BoundedMarketRegimePolicy.LOOKBACK_DAYS));
            return assessment != null
                ? assessment
                : MarketRegimeAssessment.unavailable("REGIME_ASSESSMENT_UNAVAILABLE");
        } catch (RuntimeException e) {
            logger.warn("Market regime assessment failed; configured entry will be blocked", e);
            return MarketRegimeAssessment.unavailable("REGIME_ASSESSMENT_UNAVAILABLE");
        }
    }

    private RelativeStrengthAssessment assessRelativeStrength(List<OhlcvCandle> stockCandles, LocalDate asOf) {
        try {
            List<OhlcvCandle> indexCandles = candleStore
                .findTopBySymbolOrderByDateDesc(MARKET_INDEX_SYMBOL, BoundedRelativeStrengthPolicy.LOOKBACK_DAYS * 2)
                .stream()
                .filter(candle -> candle.date() != null && !candle.date().isAfter(asOf))
                .toList();
            RelativeStrengthAssessment assessment = relativeStrengthPolicy.assess(stockCandles, indexCandles);
            return assessment != null
                ? assessment
                : RelativeStrengthAssessment.unavailable("INDEX_DATA_UNAVAILABLE");
        } catch (RuntimeException e) {
            logger.warn("Relative-strength assessment failed; configured entry will be blocked", e);
            return RelativeStrengthAssessment.unavailable("INDEX_DATA_UNAVAILABLE");
        }
    }

    private void recordRule(boolean conditionMet, List<String> passed, List<String> failed, String description) {
        if (conditionMet) {
            passed.add(description);
        } else {
            failed.add(description);
        }
    }

    BarSeries buildBarSeries(String symbol, List<OhlcvCandle> chronologicalCandles) {
        BarSeries series = new BaseBarSeries(symbol, DecimalNum.valueOf(0));
        OhlcvDataQuality.Assessment quality = OhlcvDataQuality.quarantineUnexplainedGaps(
            chronologicalCandles, MAX_ANALYTICAL_GAP_RATIO);
        if (!quality.quarantined().isEmpty()) {
            logger.warn("Quarantined {} candle(s) from analytical series for {}: {}",
                quality.quarantined().size(), symbol, quality.quarantined().get(0).reason());
        }
        for (OhlcvCandle candle : quality.accepted()) {
            OhlcvCandle analyticalCandle = candle.adjustedForAnalysis();
            ZonedDateTime endTime = analyticalCandle.date().atStartOfDay(MARKET_ZONE);
            Long volume = analyticalCandle.volume();
            Bar bar = new BaseBar(
                Duration.ofDays(1),
                endTime,
                analyticalCandle.open(),
                analyticalCandle.high(),
                analyticalCandle.low(),
                analyticalCandle.close(),
                BigDecimal.valueOf(volume != null ? volume : 0L)
            );
            series.addBar(bar);
        }
        return series;
    }

    private BigDecimal numToBigDecimal(Num value) {
        return (BigDecimal) value.getDelegate();
    }

    private String fmt(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
