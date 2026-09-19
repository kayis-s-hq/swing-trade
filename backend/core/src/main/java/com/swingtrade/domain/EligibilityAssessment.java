package com.swingtrade.domain;

import java.util.List;

/** Result of evaluating explicit eligibility facts. */
public record EligibilityAssessment(boolean eligible, List<RejectionReason> rejectionReasons) {
    public EligibilityAssessment {
        rejectionReasons = List.copyOf(rejectionReasons);
    }

    public enum RejectionReason {
        INSUFFICIENT_LIQUIDITY_OBSERVATIONS,
        INSUFFICIENT_AVERAGE_TRADED_VALUE,
        ASM_SURVEILLANCE,
        GSM_SURVEILLANCE,
        FUTURES_AND_OPTIONS_BAN,
        PRICE_BAND,
        RESULTS_TOO_CLOSE,
        BOARD_MEETING_TOO_CLOSE
    }
}
