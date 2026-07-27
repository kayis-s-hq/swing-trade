package com.swingtrade.api.service;

import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.StrategyParams;
import com.swingtrade.strategy.TechnicalIndicators;
import com.swingtrade.strategy.TechnicalIndicators.CandleWithPrices;
import com.swingtrade.domain.store.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TechnicalAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TechnicalAnalysisService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final CandleStore candleStore;
    private final TechnicalIndicators technicalIndicators;

    public TechnicalAnalysisService(CandleStore candleStore,
                                    TechnicalIndicators technicalIndicators) {
        this.candleStore = candleStore;
        this.technicalIndicators = technicalIndicators;
    }

    public CompositeAnalysis.TechnicalScore compute(String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        List<OhlcvCandle> candles = candleStore.findBySymbol(sym);

        if (candles.size() < StrategyParams.MIN_CANDLES) {
            logger.warn("Insufficient candles for technical analysis: {} ({} candles)", sym, candles.size());
            return new CompositeAnalysis.TechnicalScore(0, "HOLD", 0.0, List.of());
        }

        // Reverse to chronological order
        List<OhlcvCandle> chrono = new ArrayList<>(candles);
        java.util.Collections.reverse(chrono);

        int lastIndex = chrono.size() - 1;
        OhlcvCandle latest = chrono.get(lastIndex);
        BigDecimal price = latest.close();

        // Extract price lists for TechnicalIndicators
        List<BigDecimal> closes = new ArrayList<>(chrono.size());
        List<CandleWithPrices> candleObjs = new ArrayList<>(chrono.size());
        for (OhlcvCandle c : chrono) {
            closes.add(c.close());
            candleObjs.add(new CandleWithPrices(c.open(), c.high(), c.low(), c.close(), BigDecimal.valueOf(c.volume())));
        }

        // Calculate indicators
        Double ema20 = technicalIndicators.calculateEMA(closes, StrategyParams.EMA_FAST);
        Double ema50 = technicalIndicators.calculateEMA(closes, StrategyParams.EMA_SLOW);
        Double rsi = technicalIndicators.calculateRSI(closes, StrategyParams.RSI_PERIOD);
        List<BigDecimal> volumes = chrono.stream()
            .map(c -> BigDecimal.valueOf(c.volume()))
            .toList();
        Double volumeMA = technicalIndicators.calculateVolumeMA(volumes,
            StrategyParams.VOLUME_MA_PERIOD
        );
        Double atr = technicalIndicators.calculateATR(candleObjs, StrategyParams.ATR_PERIOD);

        // 52-week high
        double maxHigh = chrono.stream()
            .mapToDouble(c -> c.high().doubleValue())
            .max()
            .orElse(price.doubleValue());

        // Scoring: each factor contributes -25 to +25
        int score = 0;
        List<String> indicators = new ArrayList<>();

        // 1. Price > EMA20 > EMA50 (bullish alignment)
        boolean emaBullish = ema20 != null && ema50 != null &&
            price.doubleValue() > ema20 && ema20 > ema50;
        score += emaBullish ? 25 : -25;
        indicators.add(String.format("EMA20>EMA50: %s (%.2f / %.2f / %.2f)",
            emaBullish ? "bullish" : "bearish", price.doubleValue(),
            ema20 != null ? ema20 : 0, ema50 != null ? ema50 : 0));

        // 2. RSI in 50-65 range (bullish zone)
        boolean rsiOK = rsi != null && rsi >= 50 && rsi <= 65;
        score += rsiOK ? 25 : -25;
        indicators.add(String.format("RSI(14): %s (%.1f)", rsiOK ? "bullish" : "bearish", rsi != null ? rsi : 0));

        // 3. Volume > 1.5x VolumeMA
        boolean volOK = volumeMA != null && volumeMA > 0 &&
            latest.volume() > StrategyParams.VOLUME_MULTIPLIER.doubleValue() * volumeMA;
        score += volOK ? 25 : -25;
        indicators.add(String.format("Volume: %s (%.1fx MA)", volOK ? "above" : "below",
            volumeMA != null && volumeMA > 0 ? (double) latest.volume() / volumeMA : 0));

        // 4. Price within 3% of 52-week high
        boolean highOK = maxHigh > 0 && price.doubleValue() >= StrategyParams.HIGH_PROXIMITY.doubleValue() * maxHigh;
        score += highOK ? 25 : -25;
        indicators.add(String.format("52W High: %s (%.1f%% away)",
            highOK ? "proximate" : "distant",
            maxHigh > 0 ? ((maxHigh - price.doubleValue()) / maxHigh * 100) : 0));

        // Signal determination
        String signal = score > 0 ? "BUY" : score < 0 ? "SELL" : "HOLD";
        double confidence = Math.abs(score) / 100.0;

        return new CompositeAnalysis.TechnicalScore(score, signal, confidence, indicators);
    }
}