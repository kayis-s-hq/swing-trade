package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DataIngestionMetricsTest {

    @Test
    void recordsCandlesDurationsFailuresAndRateLimitHitsBySource() {
        var registry = new SimpleMeterRegistry();
        var metrics = new DataIngestionMetrics(registry);

        metrics.recordCandleIngested();
        metrics.recordCandleIngested();
        metrics.recordIngestion(Duration.ofMillis(250));
        metrics.recordFetchFailure("broker");
        metrics.recordFetchFailure("broker");
        metrics.recordFetchFailure("vendor");
        metrics.recordRateLimitWait("broker");
        metrics.recordRateLimitHit("broker");

        assertThat(registry.get("data.candles_ingested").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("data.ingestion.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("data.ingestion.duration").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(250.0);
        assertThat(registry.get("data.fetch_failures").tag("source", "broker").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("data.fetch_failures").tag("source", "vendor").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("data.rate_limit_hits").tag("source", "broker").counter().count()).isEqualTo(2.0);
    }
}
