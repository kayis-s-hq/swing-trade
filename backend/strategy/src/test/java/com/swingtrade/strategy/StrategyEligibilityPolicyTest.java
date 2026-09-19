package com.swingtrade.strategy;

import com.swingtrade.domain.RelativeStrengthAssessment;
import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyEligibilityPolicyTest {

    @Test
    void entryEligibilityRequiresAnEligibleRelativeStrengthAssessment() {
        var strategy = new PriceActionStrategy();
        var indicators = new Indicators(
            new java.math.BigDecimal("103"), new java.math.BigDecimal("100"),
            new java.math.BigDecimal("95"), new java.math.BigDecimal("60"),
            new java.math.BigDecimal("200"), new java.math.BigDecimal("100"),
            new java.math.BigDecimal("105"));
        var eligibility = new StrategyEligibilityPolicy();

        assertThat(eligibility.isEntryEligible(strategy, indicators,
            new RelativeStrengthAssessment(true, 10, 5, 5, null, "STOCK_OUTPERFORMING_INDEX")))
            .isTrue();
        assertThat(eligibility.isEntryEligible(strategy, indicators,
            RelativeStrengthAssessment.unavailable("INDEX_DATA_UNAVAILABLE")))
            .isFalse();
        assertThat(eligibility.isEntryEligible(strategy, indicators, null)).isFalse();
    }

    @Test
    void missingIndexProducesUnavailableAssessmentAndFailsClosed() {
        var eligibility = new StrategyEligibilityPolicy();

        RelativeStrengthAssessment assessment = eligibility.assessRelativeStrength(
            candles(63, 100, 0.2), null);

        assertThat(assessment.eligible()).isFalse();
        assertThat(assessment.reason()).isEqualTo("INDEX_DATA_UNAVAILABLE");
    }

    private static List<OhlcvCandle> candles(int count, double start, double dailyPercent) {
        List<OhlcvCandle> result = new ArrayList<>();
        BigDecimal price = BigDecimal.valueOf(start);
        for (int i = 0; i < count; i++) {
            result.add(OhlcvCandle.of("TEST", LocalDate.of(2020, 1, 1).plusDays(i),
                price, price, price, price, 1L));
            price = price.multiply(BigDecimal.valueOf(1 + dailyPercent / 100));
        }
        return result;
    }
}
