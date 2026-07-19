package com.swingtrade.domain;

import java.math.BigDecimal;

/**
 * Shared constants for strategy parameters.
 * All strategy engines reference this class to stay in sync.
 */
public final class StrategyParams {

    // EMA periods
    public static final int EMA_FAST = 20;
    public static final int EMA_SLOW = 50;

    // Common indicator periods
    public static final int RSI_PERIOD = 14;
    public static final int ATR_PERIOD = 14;
    public static final int VOLUME_MA_PERIOD = 20;

    // 52-week trading days (~252)
    public static final int FIFTY_TWO_WEEK_TRADING_DAYS = 252;

    // RSI entry band
    public static final BigDecimal RSI_LOWER = BigDecimal.valueOf(50);
    public static final BigDecimal RSI_UPPER = BigDecimal.valueOf(65);

    // Entry thresholds
    public static final BigDecimal VOLUME_MULTIPLIER = BigDecimal.valueOf(1.5);
    public static final BigDecimal HIGH_PROXIMITY = BigDecimal.valueOf(0.97);

    // Candle requirements
    public static final int MIN_CANDLES = EMA_SLOW;

    private StrategyParams() {}
}