package com.swingtrade.strategy;

import java.math.BigDecimal;

/**
 * Indicator readings for a single bar, shared between the live signal engine and the
 * backtest simulator so a {@link TradingStrategy} implementation evaluates entry/exit
 * rules identically in both places.
 */
public record Indicators(
    BigDecimal price,
    BigDecimal ema20,
    BigDecimal ema50,
    BigDecimal rsi,
    BigDecimal volume,
    BigDecimal volumeMa,
    BigDecimal weeklyHigh
) {
}
