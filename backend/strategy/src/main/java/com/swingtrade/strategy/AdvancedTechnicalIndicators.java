package com.swingtrade.strategy;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.StochasticOscillatorDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced technical indicators for swing trading analysis.
 * Includes ADX, VWAP, Bollinger Bands, and Stochastic Oscillator.
 * <p>
 * Core indicators (RSI, EMA, SMA, ATR, MACD, VolumeMA) are in {@link TechnicalIndicators}.
 *
 * @author Swing Trade Team
 */
public class AdvancedTechnicalIndicators {

    /**
     * Calculates Stochastic Oscillator (K and D lines).
     *
     * @param candles list of candles with high, low, close prices
     * @param kPeriod K period (typically 14)
     * @param dPeriod D period (typically 3)
     * @return Stochastic values (K and D) for the latest candle
     */
    public StochasticValues calculateStochastic(List<TechnicalIndicators.CandleWithPrices> candles,
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

        StochasticOscillatorKIndicator kIndicator =
            new StochasticOscillatorKIndicator(series, kPeriod);
        StochasticOscillatorDIndicator dIndicator =
            new StochasticOscillatorDIndicator(kIndicator);

        int lastIndex = series.getBarCount() - 1;
        Double kValue = kIndicator.getValue(lastIndex).doubleValue();
        Double dValue = dIndicator.getValue(lastIndex).doubleValue();

        return new StochasticValues(kValue, dValue);
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
     * Calculates standard deviation for a given period.
     */
    private double calculateStandardDeviation(BarSeries series,
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

    /**
     * Calculates VWAP (Volume Weighted Average Price).
     *
     * @param candles list of candles with high, low, close prices
     * @return VWAP value for the latest candle
     */
    public Double calculateVWAP(List<TechnicalIndicators.CandleWithPrices> candles) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        double totalValue = 0;
        double totalVolume = 0;
        for (TechnicalIndicators.CandleWithPrices candle : candles) {
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
    public Double calculateADX(List<TechnicalIndicators.CandleWithPrices> candles, int period) {
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
        double adx = calculateSmoothedADX(dx, period);

        return adx;
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

    private BarSeries createBarSeriesFromCandles(List<TechnicalIndicators.CandleWithPrices> candles) {
        BarSeries series = new org.ta4j.core.BaseBarSeries("series");
        ZonedDateTime baseTime = ZonedDateTime.now().minusDays(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            TechnicalIndicators.CandleWithPrices candle = candles.get(i);
            Bar bar = new org.ta4j.core.BaseBar(
                Duration.ofDays(1), baseTime.plusDays(i),
                candle.getOpen().doubleValue(), candle.getHigh().doubleValue(),
                candle.getLow().doubleValue(), candle.getClose().doubleValue(),
                candle.getVolume().doubleValue());
            series.addBar(bar);
        }
        return series;
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    /**
     * Helper record for Stochastic Oscillator values.
     */
    public static record StochasticValues(Double k, Double d) {}

    /**
     * Helper record for Bollinger Bands values.
     */
    public static record BollingerBands(Double upper, Double middle, Double lower) {}
}