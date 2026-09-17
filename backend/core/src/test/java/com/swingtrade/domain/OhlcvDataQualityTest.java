package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcvDataQualityTest {

    @Test
    void quarantinesLargeGapButKeepsRawCandleUnchanged() {
        OhlcvCandle first = candle("2024-01-01", "100");
        OhlcvCandle gap = candle("2024-01-02", "200");

        OhlcvDataQuality.Assessment result = OhlcvDataQuality.quarantineUnexplainedGaps(
            List.of(first, gap), new BigDecimal("0.50"));

        assertThat(result.accepted()).containsExactly(first);
        assertThat(result.quarantined()).extracting(OhlcvDataQuality.QuarantinedCandle::candle)
            .containsExactly(gap);
        assertThat(gap.open()).isEqualByComparingTo("200");
    }

    @Test
    void adjustedCorporateActionDoesNotLookLikeAnUnexplainedGap() {
        OhlcvCandle first = new OhlcvCandle("T", LocalDate.parse("2024-01-01"),
            new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"),
            new BigDecimal("100"), 1L, new BigDecimal("100"));
        OhlcvCandle split = new OhlcvCandle("T", LocalDate.parse("2024-01-02"),
            new BigDecimal("50"), new BigDecimal("55"), new BigDecimal("45"),
            new BigDecimal("50"), 1L, new BigDecimal("100"));

        assertThat(OhlcvDataQuality.quarantineUnexplainedGaps(List.of(first, split), new BigDecimal("0.50"))
            .quarantined()).isEmpty();
    }

    @Test
    void quarantinesMissingPricesWithoutChangingNeighbors() {
        OhlcvCandle first = candle("2024-01-01", "100");
        OhlcvCandle malformed = new OhlcvCandle("T", LocalDate.parse("2024-01-02"),
            BigDecimal.ZERO, new BigDecimal("90"), new BigDecimal("95"),
            new BigDecimal("100"), 1L, new BigDecimal("100"));
        OhlcvCandle third = candle("2024-01-03", "101");

        assertThat(OhlcvDataQuality.quarantineUnexplainedGaps(List.of(first, malformed, third), new BigDecimal("0.50"))
            .accepted()).containsExactly(first, third);
    }

    private static OhlcvCandle candle(String date, String price) {
        BigDecimal value = new BigDecimal(price);
        return OhlcvCandle.of("T", LocalDate.parse(date), value, value, value, value, 1L);
    }
}
