package com.swingtrade.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Applies explicitly sourced corporate actions to a candle for historical analysis. */
public final class HistoricalCandleAdjuster {
    private HistoricalCandleAdjuster() {}

    /**
     * Adjusts prices before each action's ex-date. The action factor is a price multiplier;
     * cash dividends are subtracted before the factor is applied. Volume is inversely scaled
     * by the combined split factor. Raw persisted candles are never mutated.
     */
    public static OhlcvCandle adjust(OhlcvCandle candle, List<CorporateAction> actions) {
        BigDecimal open = candle.open();
        BigDecimal high = candle.high();
        BigDecimal low = candle.low();
        BigDecimal close = candle.close();
        BigDecimal volumeFactor = BigDecimal.ONE;
        MathContext mc = MathContext.DECIMAL128;

        for (CorporateAction action : actions.stream()
                .filter(a -> a.effectiveDate().isAfter(candle.date()))
                .sorted(Comparator.comparing(CorporateAction::effectiveDate))
                .toList()) {
            BigDecimal cash = action.cashAmount() == null ? BigDecimal.ZERO : action.cashAmount();
            open = open.subtract(cash, mc);
            high = high.subtract(cash, mc);
            low = low.subtract(cash, mc);
            close = close.subtract(cash, mc);
            if (action.adjustmentFactor() != null) {
                BigDecimal factor = action.adjustmentFactor();
                open = open.multiply(factor, mc);
                high = high.multiply(factor, mc);
                low = low.multiply(factor, mc);
                close = close.multiply(factor, mc);
                volumeFactor = volumeFactor.divide(factor, mc);
            }
        }
        if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0) {
            throw new IllegalStateException("Corporate-action adjustment produced a non-positive price for "
                    + candle.symbol() + " on " + candle.date());
        }
        Long volume = candle.volume();
        if (volume != null) {
            volume = BigDecimal.valueOf(volume).multiply(volumeFactor, mc)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        return new OhlcvCandle(candle.symbol(), candle.date(), open, high, low, close, volume, close);
    }
}
