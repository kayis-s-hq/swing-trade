package com.swingtrade.domain.policy;

import com.swingtrade.domain.MarketRegime;
import com.swingtrade.domain.MarketRegimeAssessment;
import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketRegimePolicyTest {

    @Test
    void assessDelegatesTheCompleteIndexCandleSeriesToThePolicy() {
        List<OhlcvCandle> candles = List.of(OhlcvCandle.of("NIFTY", null, null, null, null,
                null, null));
        MarketRegimePolicy policy = input -> new MarketRegimeAssessment(
                MarketRegime.BULLISH, !input.isEmpty(), null, "TEST");

        MarketRegimeAssessment result = policy.assess(candles);

        assertThat(result.regime()).isEqualTo(MarketRegime.BULLISH);
        assertThat(result.eligible()).isTrue();
    }
}
