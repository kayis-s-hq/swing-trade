package com.swingtrade.data.entity;

import com.swingtrade.domain.SentimentResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentimentResultEntityTest {

    @Test
    void roundTripsLlmAuditRequestId() {
        String requestId = "2c4f60f5-8e65-43c7-b2b8-0c83b9a7b4df";
        SentimentResult result = new SentimentResult(
            null, "TCS", LocalDate.of(2026, 9, 17), SentimentResult.SentimentScore.POSITIVE,
            "Strong result", "", 0.9, LocalDate.of(2026, 9, 17), List.of(), List.of(),
            "prompt", "model", 1, "LLM", List.of(42L), requestId);

        SentimentResult roundTripped = SentimentResultEntity.fromDomain(result).toDomain();

        assertThat(roundTripped.auditRequestId()).isEqualTo(requestId);
        assertThat(roundTripped.articleIds()).containsExactly(42L);
    }
}
