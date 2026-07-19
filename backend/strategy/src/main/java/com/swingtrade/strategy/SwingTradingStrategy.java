package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Signal.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing trading strategy based on technical analysis.
 * Combines multiple factors: moving averages, RSI, MACD, and volume.
 */
@Component
public class SwingTradingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SwingTradingStrategy.class);

    private final TechnicalIndicators indicators;

    // Strategy parameters
    private final int emaFastPeriod = 20;
    private final int emaSlowPeriod = 50;
    private final int rsiPeriod = 14;
    private final int atrPeriod = 14;
    private final int macdFastPeriod = 12;
    private final int macdSlowPeriod = 26;
    private final int macdSignalPeriod = 9;

    // RSI thresholds
    private final double rsiOversoldThreshold = 30;
    private final double rsiOverboughtThreshold = 70;

    public SwingTradingStrategy(TechnicalIndicators indicators) {
        this.indicators = indicators;
    }

    /**
     * Analyzes a stock and generates a trading signal.
     *
     * @param candles list of OHLCV candles (ordered from oldest to newest)
     * @return trading signal with confidence score
     */
    public Signal analyze(List<OhlcvCandle> candles) {
        if (candles == null || candles.size() < emaSlowPeriod) {
            logger.warn("Not enough candles for analysis (need {}, got {})", emaSlowPeriod,
                        candles == null ? 0 : candles.size());
            return Signal.create(candles.get(0).symbol(), LocalDate.now(), SignalType.HOLD, BigDecimal.ZERO, "Insufficient data");
        }

        logger.debug("Analyzing {} candles for signal generation", candles.size());

        // Extract price data
        List<BigDecimal> closePrices = new ArrayList<>();
        List<TechnicalIndicators.CandleWithPrices> candlePrices = new ArrayList<>();

        for (OhlcvCandle candle : candles) {
            closePrices.add(candle.close());
            candlePrices.add(new TechnicalIndicators.CandleWithPrices(
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    candle.close(),
                    candle.volume() != null ? BigDecimal.valueOf(candle.volume()) : BigDecimal.ZERO
            ));
        }

        // Calculate indicators
        Double emaFast = indicators.calculateEMA(closePrices, emaFastPeriod);
        Double emaSlow = indicators.calculateEMA(closePrices, emaSlowPeriod);
        Double rsi = indicators.calculateRSI(closePrices, rsiPeriod);
        Double atr = indicators.calculateATR(candlePrices, atrPeriod);
        Double macd = indicators.calculateMACD(closePrices, macdFastPeriod, macdSlowPeriod, macdSignalPeriod);

        // Get latest candle for comparison
        OhlcvCandle latest = candles.get(candles.size() - 1);
        OhlcvCandle previous = candles.size() > 1 ? candles.get(candles.size() - 2) : latest;

        // Collect signal factors
        List<String> buyFactors = new ArrayList<>();
        List<String> sellFactors = new ArrayList<>();
        int buyScore = 0;
        int sellScore = 0;

        // Factor 1: EMA Crossover
        if (emaFast != null && emaSlow != null) {
            if (emaFast > emaSlow) {
                buyFactors.add("Fast EMA > Slow EMA (bullish)");
                buyScore++;
            } else {
                sellFactors.add("Fast EMA < Slow EMA (bearish)");
                sellScore++;
            }
        }

        // Factor 2: RSI levels
        if (rsi != null) {
            if (rsi < rsiOversoldThreshold) {
                buyFactors.add("RSI oversold at " + String.format("%.2f", rsi));
                buyScore++;
            } else if (rsi > rsiOverboughtThreshold) {
                sellFactors.add("RSI overbought at " + String.format("%.2f", rsi));
                sellScore++;
            } else if (rsi < 50 && rsi > 30) {
                buyFactors.add("RSI in bullish zone (30-50)");
                buyScore++;
            }
        }

        // Factor 3: MACD signal
        if (macd != null) {
            if (macd > 0) {
                buyFactors.add("MACD positive");
                buyScore++;
            } else {
                sellFactors.add("MACD negative");
                sellScore++;
            }
        }

        // Factor 4: Price vs EMA
        if (emaFast != null) {
            if (latest.close().doubleValue() > emaFast) {
                buyFactors.add("Price above fast EMA");
                buyScore++;
            }
        }

        // Factor 5: Volume confirmation
        double avgVolume = calculateAverageVolume(candles, 10);
        if (latest.volume() > avgVolume) {
            if (latest.isBullish()) {
                buyFactors.add("Above average volume with bullish candle");
                buyScore++;
            } else {
                sellFactors.add("Above average volume with bearish candle");
                sellScore++;
            }
        }

        // Factor 6: Price trend
        double priceChange = latest.close()
                .subtract(previous.close())
                .divide(previous.close(), 6, BigDecimal.ROUND_HALF_UP)
                .doubleValue() * 100;
        if (priceChange > 0) {
            buyFactors.add("Price up " + String.format("%.2f", priceChange) + "%");
            buyScore++;
        } else {
            sellFactors.add("Price down " + String.format("%.2f", Math.abs(priceChange)) + "%");
            sellScore++;
        }

        // Calculate signal
        return calculateSignal(buyScore, sellScore, buyFactors, sellFactors, candles.get(0).symbol(), latest.date());
    }

    /**
     * Calculates average volume over a period.
     *
     * @param candles list of candles
     * @param period number of candles to average
     * @return average volume
     */
    private double calculateAverageVolume(List<OhlcvCandle> candles, int period) {
        if (candles.size() < period) {
            period = candles.size();
        }

        long totalVolume = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            totalVolume += candles.get(i).volume();
        }
        return totalVolume / period;
    }

    /**
     * Calculates signal based on scores and factors.
     *
     * @param buyScore score for buy signals
     * @param sellScore score for sell signals
     * @param buyFactors list of buy factors
     * @param sellFactors list of sell factors
     * @param date the analysis date
     * @return trading signal
     */
    private Signal calculateSignal(int buyScore, int sellScore,
                                    List<String> buyFactors, List<String> sellFactors,
                                    String symbol, LocalDate date) {
        int totalFactors = buyScore + sellScore;
        double confidence = totalFactors > 0 ? (double) buyScore / totalFactors : 0.5;

        SignalType signalType;
        if (buyScore >= 4 && buyScore > sellScore) {
            signalType = SignalType.BUY;
        } else if (sellScore >= 4 && sellScore > buyScore) {
            signalType = SignalType.SELL;
        } else {
            signalType = SignalType.HOLD;
            confidence = 0.5;
        }

        logger.info("Signal: {} (confidence: {}%) - Factors: {} buy, {} sell",
                    String.format("%.0f", confidence * 100), signalType, buyScore, sellScore);

        if (signalType == SignalType.BUY && !buyFactors.isEmpty()) {
            logger.debug("Buy factors: {}", buyFactors);
        }
        if (signalType == SignalType.SELL && !sellFactors.isEmpty()) {
            logger.debug("Sell factors: {}", sellFactors);
        }

        return Signal.create(symbol, date, signalType, BigDecimal.valueOf(confidence), String.join("; ", buyFactors));
    }

    /**
     * Generates entry signal only (buy analysis).
     *
     * @param candles list of OHLCV candles
     * @return true if entry signal is positive
     */
    public boolean shouldEnter(List<OhlcvCandle> candles) {
        Signal signal = analyze(candles);
        return signal.type() == Signal.SignalType.BUY;
    }

    /**
     * Generates exit signal (sell analysis).
     *
     * @param candles list of OHLCV candles
     * @return true if exit signal is positive
     */
    public boolean shouldExit(List<OhlcvCandle> candles) {
        Signal signal = analyze(candles);
        return signal.type() == Signal.SignalType.SELL;
    }
}
