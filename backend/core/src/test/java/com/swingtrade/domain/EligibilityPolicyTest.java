package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EligibilityPolicyTest {
    private final EligibilityPolicy policy = new EligibilityPolicy();

    @Test
    void acceptsOnlyWhenAllExplicitFactsPass() {
        EligibilityAssessment result = policy.assess(inputs(true, false, false, true,
            EligibilityInputs.EventWindow.noneFound(), EligibilityInputs.EventWindow.noneFound()));

        assertThat(result.eligible()).isTrue();
        assertThat(result.rejectionReasons()).isEmpty();
    }

    @Test
    void rejectsLiquiditySurveillancePriceBandAndEventsWithStableReasons() {
        EligibilityAssessment result = policy.assess(new EligibilityInputs(
            "TCS", new BigDecimal("49999999"), 19, true, true, true, false,
            EligibilityInputs.EventWindow.found(5), EligibilityInputs.EventWindow.found(2)));

        assertThat(result.eligible()).isFalse();
        assertThat(result.rejectionReasons()).containsExactly(
            EligibilityAssessment.RejectionReason.INSUFFICIENT_LIQUIDITY_OBSERVATIONS,
            EligibilityAssessment.RejectionReason.INSUFFICIENT_AVERAGE_TRADED_VALUE,
            EligibilityAssessment.RejectionReason.ASM_SURVEILLANCE,
            EligibilityAssessment.RejectionReason.GSM_SURVEILLANCE,
            EligibilityAssessment.RejectionReason.FUTURES_AND_OPTIONS_BAN,
            EligibilityAssessment.RejectionReason.PRICE_BAND,
            EligibilityAssessment.RejectionReason.RESULTS_TOO_CLOSE,
            EligibilityAssessment.RejectionReason.BOARD_MEETING_TOO_CLOSE);
    }

    @Test
    void eventOutsideBoundDoesNotRejectAndUnknownIsNotInferredAsAnEvent() {
        EligibilityAssessment result = policy.assess(inputs(true, false, false, true,
            EligibilityInputs.EventWindow.found(6), EligibilityInputs.EventWindow.noneFound()));

        assertThat(result.eligible()).isTrue();
    }

    @Test
    void validatesExplicitInputsAndEventWindows() {
        assertThatThrownBy(() -> new EligibilityInputs("TCS", BigDecimal.ONE, -1,
            false, false, false, true, EligibilityInputs.EventWindow.noneFound(),
            EligibilityInputs.EventWindow.noneFound()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EligibilityInputs.EventWindow.found(-1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EligibilityInputs.EventWindow(false, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static EligibilityInputs inputs(boolean liquidity, boolean asm, boolean foBan,
                                            boolean priceBandEligible,
                                            EligibilityInputs.EventWindow results,
                                            EligibilityInputs.EventWindow boardMeeting) {
        return new EligibilityInputs("TCS", liquidity ? new BigDecimal("50000000") : BigDecimal.ZERO,
            liquidity ? 20 : 0, asm, false, foBan, priceBandEligible, results, boardMeeting);
    }
}
