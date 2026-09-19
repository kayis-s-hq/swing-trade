package com.swingtrade.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, deterministic eligibility checks over caller-supplied facts.
 * No exchange status, event calendar, price band, or liquidity value is
 * inferred by this policy.
 */
public final class EligibilityPolicy {
    public static final BigDecimal DEFAULT_MINIMUM_AVERAGE_TRADED_VALUE = new BigDecimal("50000000");
    public static final int DEFAULT_MINIMUM_LIQUIDITY_OBSERVATIONS = 20;
    public static final int DEFAULT_EVENT_WINDOW_TRADING_DAYS = 5;

    private final BigDecimal minimumAverageTradedValue;
    private final int minimumLiquidityObservations;
    private final int eventWindowTradingDays;

    public EligibilityPolicy() {
        this(DEFAULT_MINIMUM_AVERAGE_TRADED_VALUE,
            DEFAULT_MINIMUM_LIQUIDITY_OBSERVATIONS,
            DEFAULT_EVENT_WINDOW_TRADING_DAYS);
    }

    public EligibilityPolicy(BigDecimal minimumAverageTradedValue,
                             int minimumLiquidityObservations,
                             int eventWindowTradingDays) {
        if (minimumAverageTradedValue == null || minimumAverageTradedValue.signum() < 0) {
            throw new IllegalArgumentException("Minimum average traded value cannot be null or negative");
        }
        if (minimumLiquidityObservations < 1 || eventWindowTradingDays < 0) {
            throw new IllegalArgumentException("Policy bounds are invalid");
        }
        this.minimumAverageTradedValue = minimumAverageTradedValue;
        this.minimumLiquidityObservations = minimumLiquidityObservations;
        this.eventWindowTradingDays = eventWindowTradingDays;
    }

    public EligibilityAssessment assess(EligibilityInputs inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("Eligibility inputs are required");
        }
        List<EligibilityAssessment.RejectionReason> reasons = new ArrayList<>();
        if (inputs.liquidityObservationCount() < minimumLiquidityObservations) {
            reasons.add(EligibilityAssessment.RejectionReason.INSUFFICIENT_LIQUIDITY_OBSERVATIONS);
        }
        if (inputs.averageTradedValue().compareTo(minimumAverageTradedValue) < 0) {
            reasons.add(EligibilityAssessment.RejectionReason.INSUFFICIENT_AVERAGE_TRADED_VALUE);
        }
        if (inputs.asmFlag()) reasons.add(EligibilityAssessment.RejectionReason.ASM_SURVEILLANCE);
        if (inputs.gsmFlag()) reasons.add(EligibilityAssessment.RejectionReason.GSM_SURVEILLANCE);
        if (inputs.futuresAndOptionsBan()) reasons.add(EligibilityAssessment.RejectionReason.FUTURES_AND_OPTIONS_BAN);
        if (!inputs.priceBandEligible()) reasons.add(EligibilityAssessment.RejectionReason.PRICE_BAND);
        if (withinWindow(inputs.results())) reasons.add(EligibilityAssessment.RejectionReason.RESULTS_TOO_CLOSE);
        if (withinWindow(inputs.boardMeeting())) reasons.add(EligibilityAssessment.RejectionReason.BOARD_MEETING_TOO_CLOSE);
        return new EligibilityAssessment(reasons.isEmpty(), reasons);
    }

    private boolean withinWindow(EligibilityInputs.EventWindow event) {
        return event.known() && event.tradingDaysUntil() <= eventWindowTradingDays;
    }
}
