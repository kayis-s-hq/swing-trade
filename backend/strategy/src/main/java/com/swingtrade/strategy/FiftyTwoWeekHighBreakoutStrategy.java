package com.swingtrade.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * A 52-week-high breakout setup: an EMA-aligned close at the supplied rolling
 * high with at least twice average volume.
 *
 * <p>The current indicator contract supplies the rolling high rather than the
 * prior bar's high. Exact prior-252-bar breakout semantics therefore depend on
 * the caller supplying a prior-bar high in {@link Indicators#weeklyHigh()}.
 */
@Component
public class FiftyTwoWeekHighBreakoutStrategy implements TradingStrategy {

    public static final String NAME = "52W_HIGH_BREAKOUT";
    public static final BigDecimal VOLUME_MULTIPLIER = new BigDecimal("2.0");

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
        return trendAligned(i) && volumeSurge(i) && nearWeeklyHigh(i);
    }

    @Override
    public boolean trendAligned(Indicators i) {
        return i.price().compareTo(i.ema20()) > 0 && i.ema20().compareTo(i.ema50()) > 0;
    }

    @Override
    public boolean rsiInEntryRange(Indicators i) {
        return true;
    }

    @Override
    public boolean volumeSurge(Indicators i) {
        return i.volume().compareTo(i.volumeMa().multiply(VOLUME_MULTIPLIER)) >= 0;
    }

    @Override
    public boolean nearWeeklyHigh(Indicators i) {
        return i.price().compareTo(i.weeklyHigh()) > 0;
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
        return false;
    }
}
