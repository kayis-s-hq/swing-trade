package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobOrchestratorMetrics tests")
class JobOrchestratorMetricsTest {

    private SimpleMeterRegistry registry;
    private JobOrchestratorMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new JobOrchestratorMetrics(registry);
    }

    private double counter(String name) {
        return registry.get(name).counter().count();
    }

    private double activeGauge() {
        return registry.get("job.runs.active").gauge().value();
    }

    @Nested
    @DisplayName("run lifecycle counters")
    class RunLifecycleCounters {

        @Test
        @DisplayName("Tracks active, completed and failed runs")
        void shouldTrackActiveRunsCompletedAndFailedCounters() {
            metrics.recordRunStarted();
            metrics.recordRunStarted();
            assertThat(activeGauge()).isEqualTo(2.0);

            metrics.recordRunCompleted(1_000L);
            assertThat(activeGauge()).isEqualTo(1.0);
            assertThat(counter("job.runs.completed")).isEqualTo(1.0);

            metrics.recordRunFailed(500L);
            assertThat(activeGauge()).isZero();
            assertThat(counter("job.runs.failed")).isEqualTo(1.0);
            assertThat(registry.get("job.run.duration").timer().count()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("recordRunReaped")
    class RecordRunReaped {

        @Test
        @DisplayName("Counts a reaped run as failed without decrementing the active gauge")
        void shouldRecordReapedRunAsFailedWithoutTouchingActiveGauge() {
            metrics.recordRunStarted();

            metrics.recordRunReaped();

            assertThat(counter("job.runs.failed")).isEqualTo(1.0);
            assertThat(activeGauge()).isEqualTo(1.0);
            assertThat(registry.get("job.run.duration").timer().count()).isZero();
        }
    }

    @Nested
    @DisplayName("recordRunCancelled")
    class RecordRunCancelled {

        @Test
        @DisplayName("Decrements the active gauge without counting as a failure")
        void shouldDecrementActiveGaugeOnCancel() {
            metrics.recordRunStarted();

            metrics.recordRunCancelled();

            assertThat(activeGauge()).isZero();
            assertThat(counter("job.runs.failed")).isZero();
            assertThat(counter("job.runs.completed")).isZero();
        }
    }

    @Nested
    @DisplayName("degradation counters")
    class DegradationCounters {

        @Test
        @DisplayName("Counts skipped strategies per variant and reason")
        void shouldCountSkippedStrategiesByVariantAndReason() {
            metrics.recordStrategySkipped("pullback-v1", "UNSUPPORTED_TYPE");
            metrics.recordStrategySkipped("pullback-v1", "UNSUPPORTED_TYPE");
            metrics.recordStrategySkipped("squeeze-v1", "INSUFFICIENT_HISTORY");

            assertThat(registry.get("strategy.skipped")
                .tags("variant", "pullback-v1", "reason", "UNSUPPORTED_TYPE").counter().count()).isEqualTo(2.0);
            assertThat(registry.get("strategy.skipped")
                .tags("variant", "squeeze-v1", "reason", "INSUFFICIENT_HISTORY").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Counts degraded stages per stage and reason")
        void shouldCountDegradedStagesByStageAndReason() {
            metrics.recordStageDegraded("SENTIMENT", "KEYWORD_FALLBACK");

            assertThat(registry.get("stage.degraded")
                .tags("stage", "SENTIMENT", "reason", "KEYWORD_FALLBACK").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Counts a run completed with warnings as completed, not failed")
        void shouldCountRunWithWarningsAsCompleted() {
            metrics.recordRunStarted();
            metrics.recordRunCompleted(10L);

            assertThat(counter("job.runs.completed")).isEqualTo(1.0);
        }
    }
}
