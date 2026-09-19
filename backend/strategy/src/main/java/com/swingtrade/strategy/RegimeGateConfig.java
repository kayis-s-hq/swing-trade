package com.swingtrade.strategy;

/**
 * Config for the {@code regimeGate} overlay (plan §5.5): blocks a BUY when the market index
 * (Nifty) is not in an uptrend.
 *
 * @param indexEma EMA period on the index used to define the regime (default 200)
 * @param mode     {@link Mode#CLOSE_ABOVE} (index close &gt; its EMA) or
 *                 {@link Mode#EMA_SLOPE_UP} (index EMA has risen over {@code slopeLookbackDays})
 * @param slopeLookbackDays calendar days back used by {@link Mode#EMA_SLOPE_UP}
 */
public record RegimeGateConfig(int indexEma, Mode mode, int slopeLookbackDays) {

    public RegimeGateConfig {
        if (indexEma <= 0) {
            throw new IllegalArgumentException("indexEma must be positive");
        }
        if (mode == null) {
            mode = Mode.CLOSE_ABOVE;
        }
        if (slopeLookbackDays <= 0) {
            slopeLookbackDays = 10;
        }
    }

    public static RegimeGateConfig defaults() {
        return new RegimeGateConfig(200, Mode.CLOSE_ABOVE, 10);
    }

    public enum Mode {
        CLOSE_ABOVE,
        EMA_SLOPE_UP
    }
}
