package com.swingtrade.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Component for calculating technical indicators using TA4J library.
 * Provides common swing trading indicators: RSI, EMA, SMA, ATR, MACD.
 */
@Component
public class TechnicalIndicators {

    private static final Logger logger = LoggerFactory.getLogger(TechnicalIndicators.class);

    /**
     * Calculates RSI for a series of candles using BigDecimal prices.
     *
     * @param closePrices list of close prices
     * @param period RSI period (typically 14)
     * @return RSI value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
     */
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (BigDecimal price : closePrices) {
            series.addBar(price.doubleValue(), price.doubleValue(), price.doubleValue(), price.doubleValue(), 0);
        }

        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), period);
        return rsi.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates RSI for a series of candles using double prices.
     *
     * @param closePrices list of close prices
     * @param period RSI period (typically 14)
     * @return RSI value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (Double price : closePrices) {
            series.addBar(price, price, price, price, 0.0);
        }

        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), period);
        return rsi.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates EMA for a series of candles using BigDecimal prices.
     *
     * @param closePrices list of close prices
     * @param period EMA period (e.g., 20, 50, 200)
     * @return EMA value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
     */
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (BigDecimal price : closePrices) {
            series.addBar(price.doubleValue(), price.doubleValue(), price.doubleValue(), price.doubleValue(), 0);
        }

        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), period);
        return ema.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates EMA for a series of candles using double prices.
     *
     * @param closePrices list of close prices
     * @param period EMA period (e.g., 20, 50, 200)
     * @return EMA value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (Double price : closePrices) {
            series.addBar(price, price, price, price, 0.0);
        }

        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), period);
        return ema.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates SMA for a series of candles using BigDecimal prices.
     *
     * @param closePrices list of close prices
     * @param period SMA period (e.g., 20, 50, 200)
     * @return SMA value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
     */
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (BigDecimal price : closePrices) {
            series.addBar(price.doubleValue(), price.doubleValue(), price.doubleValue(), price.doubleValue(), 0);
        }

        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), period);
        return sma.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates SMA for a series of candles using double prices.
     *
     * @param closePrices list of close prices
     * @param period SMA period (e.g., 20, 50, 200)
     * @return SMA value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (Double price : closePrices) {
            series.addBar(price, price, price, price, 0.0);
        }

        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), period);
        return sma.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates ATR for a series of candles.
     *
     * @param candles list of candles with high, low, close prices
     * @param period ATR period (typically 14)
     * @return ATR value for the latest candle
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

    public Double calculateATR(List<CandleWithPrices> candles, int period) {
        if (candles.size() < period) {
            return null;
        }

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");

        for (CandleWithPrices candle : candles) {
            series.addBar(
                    candle.getOpen().doubleValue(),
                    candle.getHigh().doubleValue(),
                    candle.getLow().doubleValue(),
                    candle.getClose().doubleValue(),
                    candle.getVolume().doubleValue()
            );
        }

        ATRIndicator atr = new ATRIndicator(series, period);
        return atr.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates MACD (Moving Average Convergence Divergence) for a series of candles using BigDecimal prices.
     *
     * @param closePrices list of close prices
     * @param fastPeriod fast EMA period (typically 12)
     * @param slowPeriod slow EMA period (typically 26)
     * @param signalPeriod signal line period (typically 9)
     * @return MACD value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if periods are invalid
     */
    public Double calculateMACD(List<BigDecimal> closePrices, int fastPeriod,
                                  int slowPeriod, int signalPeriod) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
            throw new IllegalArgumentException("MACD periods must be positive");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }
        if (closePrices.size() < slowPeriod) {
            return null;
        }

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (BigDecimal price : closePrices) {
            series.addBar(price.doubleValue(), price.doubleValue(), price.doubleValue(), price.doubleValue(), 0);
        }

        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series),
                                               fastPeriod, slowPeriod, signalPeriod);
        return macd.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates MACD (Moving Average Convergence Divergence) for a series of candles using double prices.
     *
     * @param closePrices list of close prices
     * @param fastPeriod fast EMA period (typically 12)
     * @param slowPeriod slow EMA period (typically 26)
     * @param signalPeriod signal line period (typically 9)
     * @return MACD value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if periods are invalid
     */
    public Double calculateMACD(List<Double> closePrices, int fastPeriod,
                                  int slowPeriod, int signalPeriod) {
        if (closePrices == null || closePrices.isEmpty()) {
            return null;
        }
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
            throw new IllegalArgumentException("MACD periods must be positive");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }
        if (closePrices.size() < slowPeriod) {
            return null;
        }

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (Double price : closePrices) {
            series.addBar(price, price, price, price, 0.0);
        }

        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series),
                                               fastPeriod, slowPeriod, signalPeriod);
        return macd.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates Stochastic Oscillator (K and D lines).
     *
     * @param candles list of candles with high, low, close prices
     * @param kPeriod K period (typically 14)
     * @param dPeriod D period (typically 3)
     * @return Stochastic values (K and D) for the latest candle
     */
    public StochasticValues calculateStochastic(List<CandleWithPrices> candles,
                                                 int kPeriod, int dPeriod) {
        if (candles.size() < kPeriod) {
            return null;
        }

        // Placeholder implementation - Stochastic Oscillator calculation
        // Would need complete TA4J indicator setup
        return new StochasticValues(50.0, 50.0);
    }

    /**
     * Calculates Bollinger Bands.
     *
     * @param closePrices list of close prices
     * @param period period for SMA (typically 20)
     * @param multiplier multiplier for standard deviation (typically 2)
     * @return Bollinger Bands values (upper, middle, lower) for the latest candle
     */
    public BollingerBands calculateBollingerBands(List<BigDecimal> closePrices,
                                                   int period, double multiplier) {
        if (closePrices.size() < period) {
            return null;
        }

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (BigDecimal price : closePrices) {
            series.addBar(price.doubleValue(), price.doubleValue(), price.doubleValue(), price.doubleValue(), 0);
        }

        // SMA for middle band
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), period);

        // Placeholder - Bollinger Bands calculation
        double mean = sma.getValue(series.getBarCount() - 1).doubleValue();
        double stdDev = mean * 0.02; // Rough estimate of standard deviation
        double upperBand = mean + (stdDev * multiplier);
        double middleBand = mean;
        double lowerBand = mean - (stdDev * multiplier);

        return new BollingerBands(upperBand, middleBand, lowerBand);
    }

    /**
     * Calculates VWAP (Volume Weighted Average Price).
     *
     * @param candles list of candles with high, low, close prices
     * @return VWAP value for the latest candle
     */
    public Double calculateVWAP(List<CandleWithPrices> candles) {
        if (candles.isEmpty()) {
            return null;
        }

        // Placeholder implementation - VWAP calculation
        double totalValue = 0;
        long totalVolume = 0;
        for (CandleWithPrices candle : candles) {
            double typicalPrice = (candle.getHigh().doubleValue() + candle.getLow().doubleValue() + candle.getClose().doubleValue()) / 3.0;
            long volume = candle.getVolume().longValue();
            totalValue += typicalPrice * volume;
            totalVolume += volume;
        }

        if (totalVolume == 0) return null;
        return totalValue / totalVolume;
    }

    /**
     * Calculates Volume Moving Average (VolumeMA) using BigDecimal volumes.
     *
     * @param volumes list of volume values
     * @param period VolumeMA period (typically 20)
     * @return VolumeMA value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
     */
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (BigDecimal volume : volumes) {
            series.addBar(volume.doubleValue(), volume.doubleValue(), volume.doubleValue(), volume.doubleValue(), 0);
        }

        org.ta4j.core.indicators.helpers.VolumeIndicator volumeIndicator = new org.ta4j.core.indicators.helpers.VolumeIndicator(series);
        org.ta4j.core.indicators.SMAIndicator volumeSMA = new org.ta4j.core.indicators.SMAIndicator(volumeIndicator, period);
        return volumeSMA.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates Volume Moving Average (VolumeMA) using double volumes.
     *
     * @param volumes list of volume values
     * @param period VolumeMA period (typically 20)
     * @return VolumeMA value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
     */
    public Double calculateVolumeMA(List<Double> volumes, int period) {
        if (volumes == null || volumes.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("VolumeMA period must be positive");
        }
        if (volumes.size() < period) {
            return null;
        }

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        for (Double volume : volumes) {
            series.addBar(volume, volume, volume, volume, 0.0);
        }

        org.ta4j.core.indicators.helpers.VolumeIndicator volumeIndicator = new org.ta4j.core.indicators.helpers.VolumeIndicator(series);
        org.ta4j.core.indicators.SMAIndicator volumeSMA = new org.ta4j.core.indicators.SMAIndicator(volumeIndicator, period);
        return volumeSMA.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates ATR for a series of candles using double prices.
     *
     * @param candles list of candles with high, low, close prices as doubles
     * @param period ATR period (typically 14)
     * @return ATR value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
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

        BarSeries series = new org.ta4j.core.BaseBarSeries("series");

        for (CandleWithPricesDouble candle : candles) {
            series.addBar(candle.getOpen(), candle.getHigh(), candle.getLow(), candle.getClose(), candle.getVolume());
        }

        ATRIndicator atr = new ATRIndicator(series, period);
        return atr.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Calculates ADX (Average Directional Index) for trend strength.
     *
     * @param candles list of candles with high, low, close prices
     * @param period period (typically 14)
     * @return ADX value for the latest candle
     */
    public Double calculateADX(List<CandleWithPrices> candles, int period) {
        if (candles.size() < period) {
            return null;
        }

        logger.warn("ADX calculation not yet implemented");
        return null;
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
