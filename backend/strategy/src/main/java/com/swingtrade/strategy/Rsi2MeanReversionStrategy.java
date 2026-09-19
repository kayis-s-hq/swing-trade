package com.swingtrade.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * RSI(2) mean reversion for stocks above their long-term trend, exiting when
 * price reclaims the short-term average.
 *
 * <p>{@link Indicators} currently carries one RSI and EMA20/EMA50 values, so
 * this bean expects the indicator pipeline to map those fields to RSI(2), the
 * 200-day average, and the 5-day average respectively when this strategy is
 * evaluated. The existing production pipeline remains unchanged and does not
 * opt into this strategy.
 */
@Component
public class Rsi2MeanReversionStrategy implements TradingStrategy {

    public static final String NAME = "RSI2_MEAN_REVERSION";
    public static final BigDecimal RSI_ENTRY_THRESHOLD = new BigDecimal("10");

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
        return trendAligned(i) && i.rsi().compareTo(RSI_ENTRY_THRESHOLD) < 0;
    }

    @Override
    public boolean isSignalExit(Indicators i) {
        return i.price().compareTo(i.ema20()) > 0;
    }

    @Override
    public boolean trendAligned(Indicators i) {
        return i.price().compareTo(i.ema50()) > 0;
    }

    @Override
    public boolean rsiInEntryRange(Indicators i) {
        return i.rsi().compareTo(RSI_ENTRY_THRESHOLD) < 0;
    }

    @Override
    public boolean volumeSurge(Indicators i) {
        return true;
    }

    @Override
    public boolean nearWeeklyHigh(Indicators i) {
        return true;
    }

    @Override
    public boolean closeBelowEma20(Indicators i) {
        return false;
    }

    @Override
    public boolean ema20BelowEma50(Indicators i) {
        return false;
    }

    @Override
    public boolean rsiBelowLowerBound(Indicators i) {
        return false;
    }
}
