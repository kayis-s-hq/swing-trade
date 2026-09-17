package com.swingtrade.strategy;

import com.swingtrade.domain.MarketRegime;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RelativeStrengthAssessment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedMarketPolicyTest {

    @Test
    void marketRegimeUsesAdjustedCloseAndAcceptsBullishIndex() {
        List<OhlcvCandle> candles = candles(200, 100, 0.1);

        var result = new BoundedMarketRegimePolicy().assess(candles);

        assertThat(result.regime()).isEqualTo(MarketRegime.BULLISH);
        assertThat(result.eligible()).isTrue();
    }

    @Test
    void bothPoliciesFailClosedWhenIndexIsUnavailable() {
        List<OhlcvCandle> stock = candles(63, 100, 0.2);

        assertThat(new BoundedMarketRegimePolicy().assess(List.of()).eligible()).isFalse();
        RelativeStrengthAssessment assessment = new BoundedRelativeStrengthPolicy().assess(stock, List.of());

        assertThat(assessment.eligible()).isFalse();
        assertThat(assessment.reason()).isEqualTo("INDEX_DATA_UNAVAILABLE");
    }

    @Test
    void relativeStrengthFailsClosedWhenIndexArgumentIsMissing() {
        RelativeStrengthAssessment assessment = new BoundedRelativeStrengthPolicy().assess(candles(63, 100, 0.2), null);

        assertThat(assessment.eligible()).isFalse();
        assertThat(assessment.reason()).isEqualTo("INDEX_DATA_UNAVAILABLE");
    }

    @Test
    void relativeStrengthRequiresOutperformanceOverBoundedWindow() {
        List<OhlcvCandle> stock = candles(63, 100, 0.5);
        List<OhlcvCandle> index = candles(63, 100, 0.1);

        RelativeStrengthAssessment result = new BoundedRelativeStrengthPolicy().assess(stock, index);

        assertThat(result.eligible()).isTrue();
        assertThat(result.excessReturnPct()).isPositive();
    }

    @Test
    void policiesDoNotMutateOrRequireAlignedInputOrdering() {
        List<OhlcvCandle> stock = candles(63, 100, 0.5);
        List<OhlcvCandle> index = candles(63, 100, 0.1);
        List<OhlcvCandle> original = new ArrayList<>(stock);
        java.util.Collections.reverse(stock);

        assertThat(new BoundedRelativeStrengthPolicy().assess(stock, index).eligible()).isTrue();
        assertThat(stock).containsExactlyElementsOf(new ArrayList<>(original).reversed());
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
