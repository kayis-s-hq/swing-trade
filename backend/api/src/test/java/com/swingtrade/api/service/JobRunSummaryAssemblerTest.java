package com.swingtrade.api.service;

import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobRunSummaryAssembler degraded and skipped counts")
class JobRunSummaryAssemblerTest {

    private static JobRunStageEntity stage(String symbol, String name, String status, String details) {
        var e = new JobRunStageEntity();
        e.setSymbol(symbol);
        e.setStageName(name);
        e.setStatus(status);
        e.setDetails(details);
        return e;
    }

    @Test
    void countsDegradedStagesAndSkippedStrategiesAcrossSymbols() {
        var run = new JobRunEntity();
        run.setRunId(UUID.randomUUID());
        run.setStatus("COMPLETED_WITH_WARNINGS");
        String signalDetails = "{\"strategies\":[{\"variantId\":\"pullback-v1\",\"version\":1,\"outcome\":\"SKIPPED\","
            + "\"reason\":\"unsupported strategy type PULLBACK\"},{\"variantId\":\"breakout-v1\",\"version\":1,"
            + "\"outcome\":\"EVALUATED\"}],\"reason\":\"STRATEGY_SKIPPED\"}";
        var stages = List.of(
            stage("TCS", "SIGNAL", "DEGRADED", signalDetails),
            stage("INFY", "SIGNAL", "DEGRADED", signalDetails),
            stage("TCS", "SENTIMENT", "DEGRADED", "{\"source\":\"KEYWORD_FALLBACK\",\"reason\":\"KEYWORD_FALLBACK\"}"),
            stage("INFY", "SENTIMENT", "COMPLETED", null));

        var summary = JobRunSummaryAssembler.assemble(run, stages);

        assertThat(summary.degradedStages()).isEqualTo(3);
        assertThat(summary.skippedStrategies()).isEqualTo(2);
        assertThat(summary.skippedStrategyBreakdown()).singleElement().satisfies(s -> {
            assertThat(s.variantId()).isEqualTo("pullback-v1");
            assertThat(s.outcome()).isEqualTo("SKIPPED");
            assertThat(s.symbols()).isEqualTo(2);
        });
        assertThat(summary.degradedStageBreakdown()).extracting(d -> d.stage() + ":" + d.reason() + ":" + d.count())
            .containsExactlyInAnyOrder("SIGNAL:STRATEGY_SKIPPED:2", "SENTIMENT:KEYWORD_FALLBACK:1");
        assertThat(summary.stageStats().get("SENTIMENT").degraded()).isEqualTo(1);
    }

    @Test
    void cleanRunHasZeroCounts() {
        var run = new JobRunEntity();
        run.setRunId(UUID.randomUUID());
        run.setStatus("COMPLETED");

        var summary = JobRunSummaryAssembler.assemble(run, List.of(stage("TCS", "SIGNAL", "COMPLETED", null)));

        assertThat(summary.degradedStages()).isZero();
        assertThat(summary.skippedStrategies()).isZero();
    }
}
