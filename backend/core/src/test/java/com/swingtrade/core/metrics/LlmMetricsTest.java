package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmMetricsTest {

    @Test
    void recordsCallOutcomesDurationAndSentimentAnalyses() {
        var registry = new SimpleMeterRegistry();
        var metrics = new LlmMetrics(registry);

        metrics.recordCall(Duration.ofMillis(100), true);
        metrics.recordCall(Duration.ofMillis(300), false);
        metrics.recordSentimentAnalyzed();

        assertThat(registry.get("llm.call.duration").timer().count()).isEqualTo(2L);
        assertThat(registry.get("llm.calls").tag("result", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("llm.calls").tag("result", "failure").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("llm.sentiment.analyzed").counter().count()).isEqualTo(1.0);
    }
}
