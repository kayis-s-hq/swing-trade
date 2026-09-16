package com.swingtrade.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * A bounded pullback strategy for a stock whose primary trend remains positive.
 *
 * <p>The current {@link Indicators} contract is a single-bar contract, so the
 * pullback is defined as price being close to (and slightly below) EMA20 with
 * RSI recovering from a neutral pullback range. It intentionally does not
 * introduce a second data contract or alter the production strategy.
 */
@Component
public class PullbackInUptrendStrategy implements TradingStrategy {

    public static final String NAME = "PULLBACK_UPTREND";
    static final BigDecimal MIN_PULLBACK_PRICE_RATIO = new BigDecimal("0.97");
    static final BigDecimal MAX_PULLBACK_PRICE_RATIO = new BigDecimal("1.01");
    static final BigDecimal MIN_HIGH_RATIO = new BigDecimal("0.90");
    static final BigDecimal MIN_RSI = new BigDecimal("40");
    static final BigDecimal MAX_RSI = new BigDecimal("58");
    static final BigDecimal MAX_VOLUME_RATIO = new BigDecimal("1.20");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEntrySignal(Indicators i) {
        return trendAligned(i) && rsiInEntryRange(i) && volumeSurge(i)
            && pullbackNearEma20(i) && nearWeeklyHigh(i);
    }

    @Override
    public boolean trendAligned(Indicators i) {
        return i.price().compareTo(i.ema50()) > 0 && i.ema20().compareTo(i.ema50()) > 0;
    }

    @Override
    public boolean rsiInEntryRange(Indicators i) {
        return i.rsi().compareTo(MIN_RSI) >= 0 && i.rsi().compareTo(MAX_RSI) <= 0;
    }

    @Override
    public boolean volumeSurge(Indicators i) {
        return i.volume().compareTo(i.volumeMa().multiply(MAX_VOLUME_RATIO)) <= 0;
    }

    @Override
    public boolean nearWeeklyHigh(Indicators i) {
        return i.price().compareTo(i.weeklyHigh().multiply(MIN_HIGH_RATIO)) >= 0;
    }

    private boolean pullbackNearEma20(Indicators i) {
        BigDecimal ratio = i.price().divide(i.ema20(), 8, java.math.RoundingMode.HALF_UP);
        return ratio.compareTo(MIN_PULLBACK_PRICE_RATIO) >= 0
            && ratio.compareTo(MAX_PULLBACK_PRICE_RATIO) <= 0;
    }

    @Override
    public boolean closeBelowEma20(Indicators i) {
        return i.price().compareTo(i.ema20().multiply(new BigDecimal("0.95"))) < 0;
    }

    @Override
    public boolean ema20BelowEma50(Indicators i) {
        return i.ema20().compareTo(i.ema50()) < 0;
    }

    @Override
    public boolean rsiBelowLowerBound(Indicators i) {
        return i.rsi().compareTo(MIN_RSI) < 0;
    }
}
