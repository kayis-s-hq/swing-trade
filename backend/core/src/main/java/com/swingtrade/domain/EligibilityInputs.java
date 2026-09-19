package com.swingtrade.domain;

import java.math.BigDecimal;

/**
 * Facts supplied by an upstream market-data or compliance source for one
 * symbol/date. This type deliberately does not fetch, infer, or default any
 * external market facts.
 */
public record EligibilityInputs(
    String symbol,
    BigDecimal averageTradedValue,
    int liquidityObservationCount,
    boolean asmFlag,
    boolean gsmFlag,
    boolean futuresAndOptionsBan,
    boolean priceBandEligible,
    EventWindow results,
    EventWindow boardMeeting
) {
    public EligibilityInputs {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (averageTradedValue == null || averageTradedValue.signum() < 0) {
            throw new IllegalArgumentException("Average traded value cannot be null or negative");
        }
        if (liquidityObservationCount < 0) {
            throw new IllegalArgumentException("Liquidity observation count cannot be negative");
        }
        if (results == null || boardMeeting == null) {
            throw new IllegalArgumentException("Event windows are required");
        }
    }

    /** Explicit event evidence. known=false means the source found no event. */
    public record EventWindow(boolean known, int tradingDaysUntil) {
        public EventWindow {
            if (known && tradingDaysUntil < 0) {
                throw new IllegalArgumentException("Trading days until event cannot be negative");
            }
            if (!known && tradingDaysUntil != -1) {
                throw new IllegalArgumentException("Unknown event window must use -1 days");
            }
        }

        public static EventWindow found(int tradingDaysUntil) {
            return new EventWindow(true, tradingDaysUntil);
        }

        public static EventWindow noneFound() {
            return new EventWindow(false, -1);
        }
    }
}
