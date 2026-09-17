package com.swingtrade.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * A bounded volatility-squeeze family strategy using the existing single-bar
 * indicator contract.
 *
 * <p>Because {@link Indicators} does not carry a range or ATR history, this
 * family uses sustained volume contraction as its observable squeeze proxy and
 * requires an EMA-aligned, near-high setup. A future richer indicator contract
 * can replace that proxy without changing strategy selection or live defaults.
 */
@Component
public class VolatilitySqueezeStrategy implements TradingStrategy {

    public static final String NAME = "VOLATILITY_SQUEEZE";
    static final BigDecimal MAX_SQUEEZE_VOLUME_RATIO = new BigDecimal("0.80");
    static final BigDecimal MIN_RSI = new BigDecimal("50");
    static final BigDecimal MAX_RSI = new BigDecimal("70");
    static final BigDecimal MIN_HIGH_RATIO = new BigDecimal("0.97");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean regimeFilterEnabled() {
        return true;
    }

    @Override
    public boolean isEntrySignal(Indicators i) {
        return trendAligned(i) && rsiInEntryRange(i) && volumeSurge(i) && nearWeeklyHigh(i);
    }

    @Override
    public boolean trendAligned(Indicators i) {
        return i.price().compareTo(i.ema20()) >= 0 && i.ema20().compareTo(i.ema50()) > 0;
    }

    @Override
    public boolean rsiInEntryRange(Indicators i) {
        return i.rsi().compareTo(MIN_RSI) >= 0 && i.rsi().compareTo(MAX_RSI) <= 0;
    }

    @Override
    public boolean volumeSurge(Indicators i) {
        return i.volume().compareTo(i.volumeMa().multiply(MAX_SQUEEZE_VOLUME_RATIO)) <= 0;
    }

    @Override
    public boolean nearWeeklyHigh(Indicators i) {
        return i.price().compareTo(i.weeklyHigh().multiply(MIN_HIGH_RATIO)) >= 0;
    }

    @Override
    public boolean closeBelowEma20(Indicators i) {
        return i.price().compareTo(i.ema20()) < 0;
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
