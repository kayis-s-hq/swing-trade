package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SentimentMetricsTest {

    @Test
    void recordsCompletedFailedAndTimedAnalyses() {
        var registry = new SimpleMeterRegistry();
        var metrics = new SentimentMetrics(registry);

        metrics.recordCompleted();
        metrics.recordFailed();
        metrics.recordDuration(Duration.ofSeconds(2));

        assertThat(registry.get("sentiment.analyses.completed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sentiment.analyses.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sentiment.analysis.duration").timer().count()).isEqualTo(1L);
    }
}
