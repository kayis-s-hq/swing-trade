package com.swingtrade.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Core technical indicators for swing trading analysis using BigDecimal inputs.
 * Includes RSI, EMA, SMA, ATR, MACD, and VolumeMA.
 * <p>
 * Advanced indicators (ADX, VWAP, Bollinger Bands, Stochastic) are in
 * {@link AdvancedTechnicalIndicators}. Double-variant methods are in
 * {@link TechnicalIndicatorsDouble}.
 *
 * @author Swing Trade Team
 * @since 1.0.0
 */
@Component
public class TechnicalIndicators {

    private static final Logger logger = LoggerFactory.getLogger(TechnicalIndicators.class);

    public Double calculateRSI(List<BigDecimal> closePrices, int period) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("RSI period must be positive");
        }
        if (closePrices.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromPrices(closePrices);
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), period);
        return rsi.getValue(series.getBarCount() - 1).doubleValue();
    }

    public Double calculateRSIDouble(List<Double> closePrices, int period) {
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

    public Double calculateEMA(List<BigDecimal> closePrices, int period) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("EMA period must be positive");
        }
        if (closePrices.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromPrices(closePrices);
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), period);
        return ema.getValue(series.getBarCount() - 1).doubleValue();
    }

    public Double calculateEMADouble(List<Double> closePrices, int period) {
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

    public Double calculateSMA(List<BigDecimal> closePrices, int period) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("SMA period must be positive");
        }
        if (closePrices.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromPrices(closePrices);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), period);
        return sma.getValue(series.getBarCount() - 1).doubleValue();
    }

    public Double calculateSMADouble(List<Double> closePrices, int period) {
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

    public Double calculateATR(List<CandleWithPrices> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("ATR period must be positive");
        }
        if (candles.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromCandles(candles);
        ATRIndicator atr = new ATRIndicator(series, period);
        return atr.getValue(series.getBarCount() - 1).doubleValue();
    }

    public Double calculateATRDouble(List<CandleWithPricesDouble> candles, int period) {
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

    public Double calculateMACD(List<BigDecimal> closePrices, int fastPeriod,
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
        BarSeries series = createBarSeriesFromPrices(closePrices);
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePriceIndicator, fastPeriod, slowPeriod);
        return macd.getValue(series.getBarCount() - 1).doubleValue();
    }

    public Double calculateMACDDouble(List<Double> closePrices, int fastPeriod,
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

    public Double calculateVolumeMA(List<BigDecimal> volumes, int period) {
        if (volumes == null || volumes.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("VolumeMA period must be positive");
        }
        if (volumes.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromVolumes(volumes);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        SMAIndicator volumeSMA = new SMAIndicator(volumeIndicator, period);
        return volumeSMA.getValue(series.getBarCount() - 1).doubleValue();
    }

    public Double calculateVolumeMADouble(List<Double> volumeDoubles, int period) {
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
    // Advanced indicators
    // -----------------------------------------------------------------------

    /**
     * Calculates Stochastic Oscillator (K and D lines).
     */
    public StochasticValues calculateStochastic(List<CandleWithPrices> candles,
                                                 int kPeriod, int dPeriod) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        if (kPeriod <= 0 || dPeriod <= 0) {
            throw new IllegalArgumentException("Stochastic periods must be positive");
        }
        if (candles.size() < kPeriod) {
            return null;
        }
        BarSeries series = createBarSeriesFromCandles(candles);
        org.ta4j.core.indicators.StochasticOscillatorKIndicator kIndicator =
            new org.ta4j.core.indicators.StochasticOscillatorKIndicator(series, kPeriod);
        org.ta4j.core.indicators.StochasticOscillatorDIndicator dIndicator =
            new org.ta4j.core.indicators.StochasticOscillatorDIndicator(kIndicator);
        int lastIndex = series.getBarCount() - 1;
        Double kValue = kIndicator.getValue(lastIndex).doubleValue();
        Double dValue = dIndicator.getValue(lastIndex).doubleValue();
        return new StochasticValues(kValue, dValue);
    }

    /**
     * Calculates Bollinger Bands.
     */
    public BollingerBands calculateBollingerBands(List<BigDecimal> closePrices,
                                                   int period, double multiplier) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("Bollinger Bands period must be positive");
        }
        if (closePrices.size() < period) {
            return null;
        }
        BarSeries series = createBarSeriesFromPrices(closePrices);
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePriceIndicator, period);
        Num mean = sma.getValue(series.getBarCount() - 1);
        double stdDev = calculateStandardDeviation(series, closePriceIndicator, period, series.getBarCount() - 1);
        double upperBand = mean.doubleValue() + (stdDev * multiplier);
        double middleBand = mean.doubleValue();
        double lowerBand = mean.doubleValue() - (stdDev * multiplier);
        return new BollingerBands(upperBand, middleBand, lowerBand);
    }

    /**
     * Calculates VWAP (Volume Weighted Average Price).
     */
    public Double calculateVWAP(List<CandleWithPrices> candles) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        double totalValue = 0;
        double totalVolume = 0;
        for (CandleWithPrices candle : candles) {
            double typicalPrice = (candle.getHigh().doubleValue() + candle.getLow().doubleValue() + candle.getClose().doubleValue()) / 3.0;
            totalValue += typicalPrice * candle.getVolume().doubleValue();
            totalVolume += candle.getVolume().doubleValue();
        }
        if (totalVolume == 0) return null;
        return totalValue / totalVolume;
    }

    /**
     * Calculates ADX (Average Directional Index) for trend strength.
     */
    public Double calculateADX(List<CandleWithPrices> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("ADX period must be positive");
        }
        if (candles.size() < period + 1) {
            return null;
        }
        BarSeries series = createBarSeriesFromCandles(candles);
        ATRIndicator atr = new ATRIndicator(series, period);
        List<Double> plusDM = new ArrayList<>();
        List<Double> minusDM = new ArrayList<>();
        for (int i = 1; i < series.getBarCount(); i++) {
            Bar current = series.getBar(i);
            Bar prev = series.getBar(i - 1);
            double high = current.getHighPrice().doubleValue();
            double low = current.getLowPrice().doubleValue();
            double prevHigh = prev.getHighPrice().doubleValue();
            double prevLow = prev.getLowPrice().doubleValue();
            double upMove = high - prevHigh;
            double downMove = prevLow - low;
            if (upMove > downMove && upMove > 0) {
                plusDM.add(upMove);
                minusDM.add(0.0);
            } else if (downMove > upMove && downMove > 0) {
                plusDM.add(0.0);
                minusDM.add(downMove);
            } else {
                plusDM.add(0.0);
                minusDM.add(0.0);
            }
        }
        double smoothedPlusDM = calculateSmoothedValue(plusDM, period);
        double smoothedMinusDM = calculateSmoothedValue(minusDM, period);
        double atrValue = atr.getValue(series.getBarCount() - 1).doubleValue();
        if (atrValue == 0) {
            return null;
        }
        double plusDI = Math.abs((smoothedPlusDM / atrValue) * 100);
        double minusDI = Math.abs((smoothedMinusDM / atrValue) * 100);
        double sumDI = plusDI + minusDI;
        if (sumDI == 0) {
            return null;
        }
        double dx = Math.abs(plusDI - minusDI) / sumDI * 100;
        return calculateSmoothedADX(dx, period);
    }

    private double calculateSmoothedValue(List<Double> dmList, int period) {
        if (dmList.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (int i = Math.max(0, dmList.size() - period); i < dmList.size(); i++) {
            sum += dmList.get(i);
        }
        return sum / period;
    }

    private double calculateSmoothedADX(double dx, int period) {
        return dx;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private BarSeries createBarSeriesFromPrices(List<BigDecimal> prices) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            BigDecimal price = prices.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1), baseTime.plusDays(i),
                price.doubleValue(), price.doubleValue(), price.doubleValue(),
                price.doubleValue(), price.doubleValue());
            series.addBar(bar);
        }
        return series;
    }

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

    private BarSeries createBarSeriesFromCandles(List<CandleWithPrices> candles) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            CandleWithPrices candle = candles.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1), baseTime.plusDays(i),
                candle.getOpen().doubleValue(), candle.getHigh().doubleValue(),
                candle.getLow().doubleValue(), candle.getClose().doubleValue(),
                candle.getVolume().doubleValue());
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

    private BarSeries createBarSeriesFromVolumes(List<BigDecimal> volumes) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(volumes.size());
        for (int i = 0; i < volumes.size(); i++) {
            BigDecimal volume = volumes.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1), baseTime.plusDays(i),
                volume.doubleValue(), volume.doubleValue(), volume.doubleValue(),
                volume.doubleValue(), volume.doubleValue());
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

    /**
     * Calculates standard deviation for a given period.
     */
    double calculateStandardDeviation(BarSeries series,
                                               ClosePriceIndicator closeIndicator,
                                               int period, int endIndex) {
        Num mean = new SMAIndicator(closeIndicator, period).getValue(endIndex);
        double sumSquaredDiff = 0.0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            double price = closeIndicator.getValue(i).doubleValue();
            double diff = price - mean.doubleValue();
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / period);
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    /**
     * Helper class for OHLCV candles with BigDecimal values.
     */
    public static class CandleWithPrices {
        private final BigDecimal open;
        private final BigDecimal high;
        private final BigDecimal low;
        private final BigDecimal close;
        private final BigDecimal volume;

        public CandleWithPrices(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }

        public BigDecimal getOpen() { return open; }
        public BigDecimal getHigh() { return high; }
        public BigDecimal getLow() { return low; }
        public BigDecimal getClose() { return close; }
        public BigDecimal getVolume() { return volume; }
    }

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

    /**
     * Helper record for Stochastic Oscillator values.
     */
    public static record StochasticValues(Double k, Double d) {}

    /**
     * Helper record for Bollinger Bands values.
     */
    public static record BollingerBands(Double upper, Double middle, Double lower) {}
}