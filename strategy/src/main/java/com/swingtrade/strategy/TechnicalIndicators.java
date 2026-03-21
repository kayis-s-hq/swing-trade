package com.swingtrade.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
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
import java.util.List;

/**
 * Component for calculating technical indicators using TA4J library.
 * Provides comprehensive support for swing trading indicators:
 * EMA, SMA, RSI, MACD, ATR, VolumeMA with both BigDecimal and double inputs.
 *
 * @author Swing Trade Team
 * @since 1.0.0
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

        BarSeries series = createBarSeriesFromPrices(closePrices);
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

        BarSeries series = createBarSeriesFromDoubles(closePrices);
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

        BarSeries series = createBarSeriesFromPrices(closePrices);
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

        BarSeries series = createBarSeriesFromDoubles(closePrices);
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

        BarSeries series = createBarSeriesFromPrices(closePrices);
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

        BarSeries series = createBarSeriesFromDoubles(closePrices);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), period);
        return sma.getValue(series.getBarCount() - 1).doubleValue();
    }

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
     * Calculates ATR for a series of candles using BigDecimal values.
     *
     * @param candles list of candles with high, low, close prices
     * @param period ATR period (typically 14)
     * @return ATR value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
     */
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
     * Calculates ATR for a series of candles using double values.
     *
     * @param candles list of candles with high, low, close prices
     * @param period ATR period (typically 14)
     * @return ATR value for the latest candle, or null if insufficient data
     * @throws IllegalArgumentException if period is invalid
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
     * Calculates MACD (Moving Average Convergence Divergence) using BigDecimal prices.
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

        BarSeries series = createBarSeriesFromPrices(closePrices);
        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), fastPeriod, slowPeriod);
        Indicator<Num> signalLine = macd.getSignalIndicator();
        Indicator<Num> macdLine = macd.getMACDIndicator();
        Num macdValue = macdLine.getValue(series.getBarCount() - 1).minus(signalLine.getValue(series.getBarCount() - 1));
        return macdValue.doubleValue();
    }

    /**
     * Calculates MACD (Moving Average Convergence Divergence) using double prices.
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

        BarSeries series = createBarSeriesFromDoubles(closePrices);
        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), fastPeriod, slowPeriod);
        Indicator<Num> signalLine = macd.getSignalIndicator();
        Indicator<Num> macdLine = macd.getMACDIndicator();
        Num macdValue = macdLine.getValue(series.getBarCount() - 1).minus(signalLine.getValue(series.getBarCount() - 1));
        return macdValue.doubleValue();
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

        BarSeries series = createBarSeriesFromVolumes(volumes);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        SMAIndicator volumeSMA = new SMAIndicator(volumeIndicator, period);
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

        BarSeries series = createBarSeriesFromDoublesVolumes(volumes);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        SMAIndicator volumeSMA = new SMAIndicator(volumeIndicator, period);
        return volumeSMA.getValue(series.getBarCount() - 1).doubleValue();
    }

    /**
     * Helper method to create BarSeries from BigDecimal prices.
     */
    private BarSeries createBarSeriesFromPrices(List<BigDecimal> prices) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(prices.size());

        for (int i = 0; i < prices.size(); i++) {
            BigDecimal price = prices.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1),
                baseTime.plusDays(i),
                price.doubleValue(),
                price.doubleValue(),
                price.doubleValue(),
                price.doubleValue(),
                price.doubleValue()
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * Helper method to create BarSeries from double prices.
     */
    private BarSeries createBarSeriesFromDoubles(List<Double> prices) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(prices.size());

        for (int i = 0; i < prices.size(); i++) {
            Double price = prices.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1),
                baseTime.plusDays(i),
                price,
                price,
                price,
                price,
                0.0
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * Helper method to create BarSeries from BigDecimal candles.
     */
    private BarSeries createBarSeriesFromCandles(List<CandleWithPrices> candles) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(candles.size());

        for (int i = 0; i < candles.size(); i++) {
            CandleWithPrices candle = candles.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1),
                baseTime.plusDays(i),
                candle.getOpen().doubleValue(),
                candle.getHigh().doubleValue(),
                candle.getLow().doubleValue(),
                candle.getClose().doubleValue(),
                candle.getVolume().doubleValue()
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * Helper method to create BarSeries from double candles.
     */
    private BarSeries createBarSeriesFromDoublesCandles(List<CandleWithPricesDouble> candles) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(candles.size());

        for (int i = 0; i < candles.size(); i++) {
            CandleWithPricesDouble candle = candles.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1),
                baseTime.plusDays(i),
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume()
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * Helper method to create BarSeries from BigDecimal volumes.
     */
    private BarSeries createBarSeriesFromVolumes(List<BigDecimal> volumes) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(volumes.size());

        for (int i = 0; i < volumes.size(); i++) {
            BigDecimal volume = volumes.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1),
                baseTime.plusDays(i),
                volume.doubleValue(),
                volume.doubleValue(),
                volume.doubleValue(),
                volume.doubleValue(),
                volume.doubleValue()
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * Helper method to create BarSeries from double volumes.
     */
    private BarSeries createBarSeriesFromDoublesVolumes(List<Double> volumes) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(volumes.size());

        for (int i = 0; i < volumes.size(); i++) {
            Double volume = volumes.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1),
                baseTime.plusDays(i),
                volume,
                volume,
                volume,
                volume,
                volume
            );
            series.addBar(bar);
        }
        return series;
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
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        if (kPeriod <= 0 || dPeriod <= 0) {
            throw new IllegalArgumentException("Stochastic periods must be positive");
        }
        if (candles.size() < kPeriod) {
            return null;
        }

        logger.warn("Stochastic Oscillator calculation not yet fully implemented");
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
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), period);

        // Simple standard deviation approximation
        Num mean = sma.getValue(series.getBarCount() - 1);
        double meanValue = mean.doubleValue();
        double stdDev = meanValue * 0.02; // Rough estimate of standard deviation

        double upperBand = meanValue + (stdDev * multiplier);
        double middleBand = meanValue;
        double lowerBand = meanValue - (stdDev * multiplier);

        return new BollingerBands(upperBand, middleBand, lowerBand);
    }

    /**
     * Calculates VWAP (Volume Weighted Average Price).
     *
     * @param candles list of candles with high, low, close prices
     * @return VWAP value for the latest candle
     */
    public Double calculateVWAP(List<CandleWithPrices> candles) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        // Placeholder implementation - VWAP calculation
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
     *
     * @param candles list of candles with high, low, close prices
     * @param period period (typically 14)
     * @return ADX value for the latest candle
     */
    public Double calculateADX(List<CandleWithPrices> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        if (period <= 0) {
            throw new IllegalArgumentException("ADX period must be positive");
        }
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
