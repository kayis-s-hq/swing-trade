package com.swingtrade.api.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.strategy.TechnicalIndicators.CandleWithPrices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FundamentalScorer {

    private static final Logger logger = LoggerFactory.getLogger(FundamentalScorer.class);
    private static final int MOMENTUM_DAYS = 30;
    private static final int VOLUME_HISTORICAL_DAYS = 126;

    private final CandleStore candleStore;
    private final com.swingtrade.strategy.TechnicalIndicators technicalIndicators;

    public FundamentalScorer(CandleStore candleStore,
                             com.swingtrade.strategy.TechnicalIndicators technicalIndicators) {
        this.candleStore = candleStore;
        this.technicalIndicators = technicalIndicators;
    }

    public com.swingtrade.domain.CompositeAnalysis.FundamentalScore compute(String symbol) {
        String sym = symbol.toUpperCase();
        List<OhlcvCandle> candles = candleStore.findAllBySymbolOrderByDateDesc(sym);

        if (candles.size() < 30) {
            logger.warn("Insufficient candles for fundamental scoring: {} ({} candles)", sym, candles.size());
            return new com.swingtrade.domain.CompositeAnalysis.FundamentalScore(0, List.of("Insufficient data"));
        }

        List<OhlcvCandle> chrono = prepareChronological(candles);
        BigDecimal price = chrono.get(chrono.size() - 1).close();

        int score = 0;
        List<String> factors = new ArrayList<>();

        score += applyVolatilityScore(chrono, price, factors);
        score += applyMomentumScore(chrono, price, factors);
        score += applyVolumeTrendScore(chrono, factors);
        score += applyPricePositionScore(chrono, price, factors);

        String signal = score > 0 ? "BULLISH" : score < 0 ? "BEARISH" : "NEUTRAL";
        return new com.swingtrade.domain.CompositeAnalysis.FundamentalScore(score, factors);
    }

    private List<OhlcvCandle> prepareChronological(List<OhlcvCandle> candles) {
        List<OhlcvCandle> chrono = new ArrayList<>(candles);
        java.util.Collections.reverse(chrono);
        return chrono;
    }

    private int applyVolatilityScore(List<OhlcvCandle> chrono, BigDecimal price, List<String> factors) {
        List<CandleWithPrices> candleObjs = toCandleObjs(chrono);
        Double atr = technicalIndicators.calculateATR(candleObjs, 14);
        if (atr != null && price.doubleValue() > 0) {
            double atrPct = (atr / price.doubleValue()) * 100;
            if (atrPct < 3) {
                factors.add(String.format("Volatility (ATR/Price): low (%.1f%%) = +25", atrPct));
                return 25;
            } else if (atrPct < 5) {
                factors.add(String.format("Volatility (ATR/Price): moderate (%.1f%%) = 0", atrPct));
                return 0;
            } else {
                factors.add(String.format("Volatility (ATR/Price): high (%.1f%%) = -25", atrPct));
                return -25;
            }
        }
        factors.add("Volatility: insufficient data = 0");
        return 0;
    }

    private int applyMomentumScore(List<OhlcvCandle> chrono, BigDecimal price, List<String> factors) {
        List<BigDecimal> closes = chrono.stream().map(OhlcvCandle::close).toList();
        if (closes.size() >= MOMENTUM_DAYS) {
            double momentum = (price.doubleValue() - closes.get(closes.size() - MOMENTUM_DAYS).doubleValue())
                / closes.get(closes.size() - MOMENTUM_DAYS).doubleValue() * 100;
            if (momentum > 5) {
                factors.add(String.format("Momentum (30d): positive (+%.1f%%) = +25", momentum));
                return 25;
            } else if (momentum > 0) {
                factors.add(String.format("Momentum (30d): mild positive (%.1f%%) = 0", momentum));
                return 0;
            } else if (momentum > -5) {
                factors.add(String.format("Momentum (30d): mild negative (%.1f%%) = 0", momentum));
                return 0;
            } else {
                factors.add(String.format("Momentum (30d): negative (%.1f%%) = -25", momentum));
                return -25;
            }
        }
        factors.add("Momentum (30d): insufficient data = 0");
        return 0;
    }

    private int applyVolumeTrendScore(List<OhlcvCandle> chrono, List<String> factors) {
        int recentVolDays = Math.min(10, chrono.size());
        int histVolDays = Math.min(VOLUME_HISTORICAL_DAYS, chrono.size() - recentVolDays);
        if (histVolDays > 0) {
            double recentVol = chrono.stream()
                .skip(Math.max(0, chrono.size() - recentVolDays))
                .mapToDouble(c -> c.volume().doubleValue())
                .average()
                .orElse(0);
            double histVol = chrono.stream()
                .limit(chrono.size() - Math.max(0, chrono.size() - histVolDays))
                .mapToDouble(c -> c.volume().doubleValue())
                .average()
                .orElse(0);
            if (histVol > 0) {
                double volRatio = recentVol / histVol;
                if (volRatio > 1.2) {
                    factors.add(String.format("Volume trend: increasing (%.2fx historical) = +25", volRatio));
                    return 25;
                } else if (volRatio > 0.8) {
                    factors.add(String.format("Volume trend: flat (%.2fx historical) = 0", volRatio));
                    return 0;
                } else {
                    factors.add(String.format("Volume trend: declining (%.2fx historical) = -25", volRatio));
                    return -25;
                }
            }
        }
        factors.add("Volume trend: insufficient data = 0");
        return 0;
    }

    private int applyPricePositionScore(List<OhlcvCandle> chrono, BigDecimal price, List<String> factors) {
        List<BigDecimal> closes = chrono.stream().map(OhlcvCandle::close).toList();
        Double sma50 = technicalIndicators.calculateSMA(closes, 50);
        if (sma50 != null) {
            if (price.doubleValue() > sma50) {
                factors.add(String.format("Price vs SMA50: above (%.2f > %.2f) = +25", price.doubleValue(), sma50));
                return 25;
            } else {
                factors.add(String.format("Price vs SMA50: below (%.2f < %.2f) = -25", price.doubleValue(), sma50));
                return -25;
            }
        }
        factors.add("Price vs SMA50: insufficient data = 0");
        return 0;
    }

    private List<CandleWithPrices> toCandleObjs(List<OhlcvCandle> chrono) {
        List<CandleWithPrices> candleObjs = new ArrayList<>(chrono.size());
        for (OhlcvCandle c : chrono) {
            candleObjs.add(new CandleWithPrices(c.open(), c.high(), c.low(), c.close(), BigDecimal.valueOf(c.volume())));
        }
        return candleObjs;
    }
}