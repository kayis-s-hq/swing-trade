package com.swingtrade.domain.policy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RelativeStrengthAssessment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeStrengthPolicyTest {

    @Test
    void assessDelegatesBothStockAndIndexCandleSeries() {
        List<OhlcvCandle> stock = List.of(OhlcvCandle.of("TCS", null, null, null, null, null, null));
        List<OhlcvCandle> index = List.of(OhlcvCandle.of("NIFTY", null, null, null, null, null, null));
        RelativeStrengthPolicy policy = (stockCandles, indexCandles) -> new RelativeStrengthAssessment(
                stockCandles.size() == indexCandles.size(), 5.0, 3.0, 2.0, null, "TEST");

        RelativeStrengthAssessment result = policy.assess(stock, index);

        assertThat(result.eligible()).isTrue();
        assertThat(result.excessReturnPct()).isEqualTo(2.0);
    }
}
