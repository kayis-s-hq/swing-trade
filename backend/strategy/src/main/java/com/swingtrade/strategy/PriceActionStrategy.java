package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyParams;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The production price-action strategy: 4-rule BUY confluence (price &gt; EMA20 &gt; EMA50,
 * RSI 50-65, volume &gt; 1.5x its 20-day average, price within 3% of its 52-week high) and a
 * 1-of-3 SELL confluence (close &lt; EMA20, EMA20 &lt; EMA50, RSI &lt; 50).
 *
 * <p>This is the single source of truth for these thresholds - both
 * {@link PriceActionSignalEngine} (live) and {@link BacktestEngine} (backtest) evaluate
 * rules through this class instead of each hard-coding its own copy of the comparisons.
 */
@Component
public class PriceActionStrategy implements TradingStrategy {

    public static final String NAME = "PRICE_ACTION";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean trendAligned(Indicators i) {
        return i.price().compareTo(i.ema20()) > 0 && i.ema20().compareTo(i.ema50()) > 0;
    }

    @Override
    public boolean rsiInEntryRange(Indicators i) {
        return i.rsi().compareTo(StrategyParams.RSI_LOWER) >= 0 && i.rsi().compareTo(StrategyParams.RSI_UPPER) <= 0;
    }

    @Override
    public boolean volumeSurge(Indicators i) {
        BigDecimal threshold = i.volumeMa().multiply(StrategyParams.VOLUME_MULTIPLIER);
        return i.volume().compareTo(threshold) > 0;
    }

    @Override
    public boolean nearWeeklyHigh(Indicators i) {
        BigDecimal threshold = i.weeklyHigh().multiply(StrategyParams.HIGH_PROXIMITY);
        return i.price().compareTo(threshold) >= 0;
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
        return i.rsi().compareTo(StrategyParams.RSI_LOWER) < 0;
    }
}
