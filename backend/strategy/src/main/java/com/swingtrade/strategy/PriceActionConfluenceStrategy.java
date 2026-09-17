package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyParams;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Opt-in price-action variant requiring any three of the four entry rules.
 *
 * <p>The default bean keeps the production RSI band (50-65), while the public
 * parameterized constructor and {@link #fromParameters(Map)} allow backtests to
 * evaluate an RSI range without changing the live {@link PriceActionStrategy}.
 */
@Component
public class PriceActionConfluenceStrategy implements TradingStrategy {

    public static final String NAME = "PRICE_ACTION_3_OF_4";
    public static final String RSI_LOWER_PARAM = "rsiLower";
    public static final String RSI_UPPER_PARAM = "rsiUpper";
    public static final int REQUIRED_ENTRY_RULES = 3;

    private final BigDecimal rsiLower;
    private final BigDecimal rsiUpper;

    public PriceActionConfluenceStrategy() {
        this(StrategyParams.RSI_LOWER, StrategyParams.RSI_UPPER);
    }

    public PriceActionConfluenceStrategy(BigDecimal rsiLower, BigDecimal rsiUpper) {
        if (rsiLower == null || rsiUpper == null
            || rsiLower.compareTo(BigDecimal.ZERO) < 0
            || rsiUpper.compareTo(new BigDecimal("100")) > 0
            || rsiLower.compareTo(rsiUpper) > 0) {
            throw new IllegalArgumentException("RSI range must satisfy 0 <= lower <= upper <= 100");
        }
        this.rsiLower = rsiLower;
        this.rsiUpper = rsiUpper;
    }

    /** Creates a backtest variant from the existing strategy-config parameter map. */
    public static PriceActionConfluenceStrategy fromParameters(Map<String, ?> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Strategy parameters cannot be null");
        }
        return new PriceActionConfluenceStrategy(
            decimalParameter(parameters, RSI_LOWER_PARAM, StrategyParams.RSI_LOWER),
            decimalParameter(parameters, RSI_UPPER_PARAM, StrategyParams.RSI_UPPER));
    }

    private static BigDecimal decimalParameter(Map<String, ?> parameters, String key, BigDecimal defaultValue) {
        Object value = parameters.get(key);
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        if (value instanceof String string) {
            try {
                return new BigDecimal(string.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Strategy parameter " + key + " must be numeric", e);
            }
        }
        throw new IllegalArgumentException("Strategy parameter " + key + " must be numeric");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int requiredEntryRules() {
        return REQUIRED_ENTRY_RULES;
    }

    @Override
    public String entryConfluenceDescription() {
        return "At least 3 of 4 entry rules passed";
    }

    @Override
    public String entryRsiDescription() {
        return "RSI between %s-%s".formatted(rsiLower.stripTrailingZeros().toPlainString(),
            rsiUpper.stripTrailingZeros().toPlainString());
    }

    @Override
    public boolean isEntrySignal(Indicators i) {
        int passed = (trendAligned(i) ? 1 : 0)
            + (rsiInEntryRange(i) ? 1 : 0)
            + (volumeSurge(i) ? 1 : 0)
            + (nearWeeklyHigh(i) ? 1 : 0);
        return passed >= REQUIRED_ENTRY_RULES;
    }

    @Override
    public boolean trendAligned(Indicators i) {
        return i.price().compareTo(i.ema20()) > 0 && i.ema20().compareTo(i.ema50()) > 0;
    }

    @Override
    public boolean rsiInEntryRange(Indicators i) {
        return i.rsi().compareTo(rsiLower) >= 0 && i.rsi().compareTo(rsiUpper) <= 0;
    }

    @Override
    public boolean volumeSurge(Indicators i) {
        return i.volume().compareTo(i.volumeMa().multiply(StrategyParams.VOLUME_MULTIPLIER)) > 0;
    }

    @Override
    public boolean nearWeeklyHigh(Indicators i) {
        return i.price().compareTo(i.weeklyHigh().multiply(StrategyParams.HIGH_PROXIMITY)) >= 0;
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
        return i.rsi().compareTo(rsiLower) < 0;
    }
}
