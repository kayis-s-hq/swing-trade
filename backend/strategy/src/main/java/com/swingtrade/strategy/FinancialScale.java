package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Explicit scales and rounding rules for the backtest and signal financial math, so that
 * serialized values never carry unbounded scales and results are reproducible.
 *
 * <ul>
 *   <li>{@link #MONEY_SCALE} (4 dp, HALF_UP): currency amounts - P&amp;L, capital, equity, notionals.
 *       Four places keep sub-paisa precision through accumulation while bounding JSON output.</li>
 *   <li>{@link #PERCENT_SCALE} (4 dp, HALF_UP): percentages expressed as {@code 12.5} meaning 12.5%.</li>
 *   <li>{@link #INDICATOR_SCALE} (6 dp, HALF_UP): technical-indicator readings (RSI, EMA, ATR).</li>
 *   <li>Share quantities are rounded {@link RoundingMode#DOWN} (never buy a fraction we cannot afford).</li>
 *   <li>{@link #RATIO} (DECIMAL64): intermediate division precision for ratios before the final rounding.</li>
 * </ul>
 *
 * <p>Price levels (entry, exit, stop, target) are not rounded here: they keep the precision of the
 * underlying market data so stop/target comparisons stay exact.</p>
 */
public final class FinancialScale {

    public static final int MONEY_SCALE = 4;
    public static final int PERCENT_SCALE = 4;
    public static final int INDICATOR_SCALE = 6;
    public static final MathContext RATIO = MathContext.DECIMAL64;

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_INT = BigDecimal.valueOf(Integer.MAX_VALUE);

    private FinancialScale() {
    }

    /** Rounds a currency amount to {@link #MONEY_SCALE}. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Rounds a percentage to {@link #PERCENT_SCALE}. */
    public static BigDecimal percent(BigDecimal value) {
        return value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Rounds an indicator reading to {@link #INDICATOR_SCALE}. */
    public static BigDecimal indicator(BigDecimal value) {
        return value.setScale(INDICATOR_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Converts a {@code double} configuration value (capital, brokerage, a fraction) to an exact
     * decimal via its shortest string representation, so {@code 0.001} becomes {@code 0.001} rather
     * than the binary approximation.
     */
    static BigDecimal of(double value) {
        return BigDecimal.valueOf(value);
    }

    /** {@code numerator / denominator * 100} rounded to {@link #PERCENT_SCALE}; zero when the denominator is zero. */
    static BigDecimal percentOf(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        return numerator.multiply(HUNDRED).divide(denominator, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Floors a non-negative share count (RoundingMode.DOWN) and saturates at {@link Integer#MAX_VALUE}
     * rather than overflowing.
     */
    static int wholeShares(BigDecimal value) {
        BigDecimal floored = value.setScale(0, RoundingMode.DOWN);
        if (floored.compareTo(MAX_INT) > 0) {
            return Integer.MAX_VALUE;
        }
        return floored.intValue();
    }
}
