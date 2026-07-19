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

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
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
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

    private final OhlcvCandleRepository candleRepository;

    public PriceActionSignalEngine(OhlcvCandleRepository candleRepository) {
        this.candleRepository = candleRepository;
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
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }

        List<OhlcvCandleEntity> descendingCandles = candleRepository.findAllBySymbolOrderByDateDesc(symbol);
        if (descendingCandles.size() < MIN_REQUIRED_CANDLES) {
            throw new IllegalStateException(
                "Insufficient candle history for " + symbol + ": need at least "
                    + MIN_REQUIRED_CANDLES + " candles, found " + descendingCandles.size());
        }

        List<OhlcvCandleEntity> chronologicalCandles = new ArrayList<>(descendingCandles);
        Collections.reverse(chronologicalCandles);

        return analyze(symbol, chronologicalCandles);
    }

    /**
     * Analyzes a chronologically-ordered (oldest first) list of candles for a symbol.
     *
     * @param symbol               the stock symbol
     * @param chronologicalCandles candles ordered oldest to newest
     * @return the resulting signal with the indicator readings that produced it
     */
    SignalResult analyze(String symbol, List<OhlcvCandleEntity> chronologicalCandles) {
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
        LocalDate date = chronologicalCandles.get(chronologicalCandles.size() - 1).getDate();

        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        boolean priceAboveEma20 = price.compareTo(ema20) > 0;
        boolean ema20AboveEma50 = ema20.compareTo(ema50) > 0;
        boolean trendAligned = priceAboveEma20 && ema20AboveEma50;
        recordRule(trendAligned, passed, failed,
            "Price > EMA20 > EMA50 (price=" + fmt(price) + ", ema20=" + fmt(ema20) + ", ema50=" + fmt(ema50) + ")");

        boolean rsiInRange = rsi.compareTo(RSI_LOWER_BOUND) >= 0 && rsi.compareTo(RSI_UPPER_BOUND) <= 0;
        recordRule(rsiInRange, passed, failed, "RSI between 50-65 (rsi=" + fmt(rsi) + ")");

        BigDecimal volumeThreshold = volumeMa.multiply(VOLUME_MULTIPLIER);
        boolean volumeSurge = volume.compareTo(volumeThreshold) > 0;
        recordRule(volumeSurge, passed, failed,
            "Volume > 1.5x VolumeMA20 (volume=" + fmt(volume) + ", threshold=" + fmt(volumeThreshold) + ")");

        BigDecimal highProximityThreshold = weeklyHigh.multiply(HIGH_PROXIMITY_THRESHOLD);
        boolean nearWeeklyHigh = price.compareTo(highProximityThreshold) >= 0;
        recordRule(nearWeeklyHigh, passed, failed,
            "Price within 3% of 52-week high (price=" + fmt(price) + ", 52wHigh=" + fmt(weeklyHigh) + ")");

        boolean allRulesPassed = trendAligned && rsiInRange && volumeSurge && nearWeeklyHigh;
        SignalType type = allRulesPassed ? SignalType.BUY : SignalType.HOLD;

        String reasoning = allRulesPassed
            ? "All entry rules passed: " + String.join("; ", passed)
            : "Entry rules failed: " + String.join("; ", failed);

        logger.debug("Signal for {} on {}: {} ({})", symbol, date, type, reasoning);

        return new SignalResult(symbol, date, type, rsi.doubleValue(), ema20.doubleValue(),
            ema50.doubleValue(), atr.doubleValue(), reasoning);
    }

    private void recordRule(boolean conditionMet, List<String> passed, List<String> failed, String description) {
        if (conditionMet) {
            passed.add(description);
        } else {
            failed.add(description);
        }
    }

    BarSeries buildBarSeries(String symbol, List<OhlcvCandleEntity> chronologicalCandles) {
        BarSeries series = new BaseBarSeries(symbol);
        for (OhlcvCandleEntity candle : chronologicalCandles) {
            ZonedDateTime endTime = candle.getDate().atStartOfDay(MARKET_ZONE);
            Long volume = candle.getVolume();
            Bar bar = new BaseBar(
                Duration.ofDays(1),
                endTime,
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                BigDecimal.valueOf(volume != null ? volume : 0L)
            );
            series.addBar(bar);
        }
        return series;
    }

    private BigDecimal numToBigDecimal(Num value) {
        return BigDecimal.valueOf(value.doubleValue());
    }

    private String fmt(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
