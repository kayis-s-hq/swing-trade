package com.swingtrade.strategy;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Technical indicator calculations using primitive double inputs.
 * Core BigDecimal variants are in {@link TechnicalIndicators}.
 * <p>
 * This class provides the same indicators but accepts double primitives
 * to avoid BigDecimal allocation overhead when precision is not critical.
 *
 * @author Swing Trade Team
 */
public class TechnicalIndicatorsDouble {

    /**
     * Calculates RSI for a series of candles using double prices.
     */
    public Double calculateRSI(List<Double> closePrices, int period) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("RSI period must be positive");
        }
        if (closePrices.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromDoubles(closePrices);
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), period);
        return rsi.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates EMA for a series of candles using double prices.
     */
    public Double calculateEMA(List<Double> closePrices, int period) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("EMA period must be positive");
        }
        if (closePrices.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromDoubles(closePrices);
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), period);
        return ema.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates SMA for a series of candles using double prices.
     */
    public Double calculateSMA(List<Double> closePrices, int period) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("SMA period must be positive");
        }
        if (closePrices.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromDoubles(closePrices);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), period);
        return sma.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates ATR for a series of candles using double values.
     */
    public Double calculateATR(List<CandleWithPricesDouble> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("ATR period must be positive");
        }
        if (candles.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromDoublesCandles(candles);
        ATRIndicator atr = new ATRIndicator(series, period);
        return atr.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates MACD using double prices.
     */
    public Double calculateMACD(List<Double> closePrices, int fastPeriod,
                                 int slowPeriod, int signalPeriod) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (fastPeriod <= 0 || slowPeriod <= 0) {
            throw new IllegalArgumentException("MACD periods must be positive");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }
        if (closePrices.size() < slowPeriod) {
            return null;
        }
        BarSeries series = createBarSeriesFromDoubles(closePrices);
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePriceIndicator, fastPeriod, slowPeriod);
        return macd.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates Volume Moving Average using double volumes.
     */
    public Double calculateVolumeMA(List<Double> volumeDoubles, int period) {
        if (volumeDoubles == null || volumeDoubles.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("VolumeMA period must be positive");
        }
        if (volumeDoubles.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromDoublesVolumes(volumeDoubles);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        SMAIndicator volumeSMA = new SMAIndicator(volumeIndicator, period);
        return volumeSMA.getValue(series.getBarCount() - 1).doubleValue();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private BarSeries createBarSeriesFromDoubles(List<Double> prices) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            Double price = prices.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1), baseTime.plusDays(i),
                price, price, price, price, 0.0);
            series.addBar(bar);
        }
        return series;
    }

    private BarSeries createBarSeriesFromDoublesCandles(List<CandleWithPricesDouble> candles) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            CandleWithPricesDouble candle = candles.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1), baseTime.plusDays(i),
                candle.getOpen(), candle.getHigh(), candle.getLow(),
                candle.getClose(), candle.getVolume());
            series.addBar(bar);
        }
        return series;
    }

    private BarSeries createBarSeriesFromDoublesVolumes(List<Double> volumes) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(volumes.size());
        for (int i = 0; i < volumes.size(); i++) {
            Double volume = volumes.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1), baseTime.plusDays(i),
                volume, volume, volume, volume, volume);
            series.addBar(bar);
        }
        return series;
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    /**
     * Helper class for OHLCV candles with double values.
     */
    public static class CandleWithPricesDouble {
        private final double open;
        private final double high;
        private final double low;
        private final double close;
        private final double volume;

        public CandleWithPricesDouble(double open, double high, double low, double close, double volume) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }

        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
        public double getVolume() { return volume; }
    }
}