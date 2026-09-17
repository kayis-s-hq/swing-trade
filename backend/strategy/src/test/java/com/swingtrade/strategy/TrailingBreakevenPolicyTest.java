package com.swingtrade.strategy;

import com.swingtrade.domain.RiskManagementPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TrailingBreakevenPolicyTest {
    private final TrailingBreakevenPolicy policy = new TrailingBreakevenPolicy(1.0, 0.05);

    @Test
    void takesConfiguredPartialAtTwoRiskMultiples() {
        RiskManagementPolicy.RiskManagementDecision decision = policy.evaluate(context(120, 119, 100, false));

        assertThat(decision.partialExitRatio()).isEqualByComparingTo("0.5");
        assertThat(decision.stopPrice()).isEqualByComparingTo("120");
        assertThat(decision.reason()).isEqualTo("PARTIAL_TARGET");
    }

    @Test
    void trailsRemainderFromHighestCompletedCloseAfterPartial() {
        RiskManagementPolicy.RiskManagementDecision decision = policy.evaluate(context(100, 104, 110, true));

        assertThat(decision.exit()).isTrue();
        assertThat(decision.stopPrice()).isEqualByComparingTo("104.50000000");
        assertThat(decision.reason()).isEqualTo("TRAILING_STOP");
    }

    private RiskManagementPolicy.RiskManagementContext context(double high, double low,
                                                                 double highest, boolean partialTaken) {
        return new RiskManagementPolicy.RiskManagementContext(
                BigDecimal.valueOf(100), BigDecimal.valueOf(90), BigDecimal.valueOf(125),
                BigDecimal.valueOf(high), BigDecimal.valueOf(low), BigDecimal.valueOf(high),
                BigDecimal.valueOf(highest), 4, partialTaken);
    }
}
