package com.swingtrade.domain;

import com.swingtrade.domain.policy.MarketRegimePolicy;
import com.swingtrade.domain.policy.RelativeStrengthPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPolicyContractTest {

    @Test
    void policiesAreContractsThatAcceptCandleLists() {
        MarketRegimePolicy regimePolicy = candles -> MarketRegimeAssessment.unavailable("test");
        RelativeStrengthPolicy strengthPolicy = (stock, index) -> RelativeStrengthAssessment.unavailable("test");

        assertThat(regimePolicy.assess(List.of()).eligible()).isFalse();
        assertThat(strengthPolicy.assess(List.of(), List.of()).eligible()).isFalse();
    }

    @Test
    void unavailableAssessmentsFailClosed() {
        assertThat(MarketRegimeAssessment.unavailable("INDEX_DATA_UNAVAILABLE"))
                .satisfies(a -> {
                    assertThat(a.regime()).isEqualTo(MarketRegime.UNKNOWN);
                    assertThat(a.eligible()).isFalse();
                });
        assertThat(RelativeStrengthAssessment.unavailable("INDEX_DATA_UNAVAILABLE").eligible()).isFalse();
    }
}
