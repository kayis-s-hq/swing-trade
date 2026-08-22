package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JobOrchestratorMetrics {

    private static final Logger log = LoggerFactory.getLogger(JobOrchestratorMetrics.class);

    private final Counter runsCompleted;
    private final Counter runsFailed;
    private final Timer runDuration;
    private final AtomicInteger activeRuns;

    public JobOrchestratorMetrics(MeterRegistry meterRegistry) {
        this.activeRuns = new AtomicInteger(0);

        this.runsCompleted = Counter.builder("job.runs.completed")
                .description("Total job runs completed")
                .register(meterRegistry);

        this.runsFailed = Counter.builder("job.runs.failed")
                .description("Total job runs failed")
                .register(meterRegistry);

        this.runDuration = Timer.builder("job.run.duration")
                .description("Job run duration in seconds")
                .register(meterRegistry);

        Gauge.builder("job.runs.active", activeRuns, AtomicInteger::get)
                .description("Currently active job runs")
                .register(meterRegistry);

        log.info("JobOrchestratorMetrics initialized");
    }

    public void recordRunStarted() {
        activeRuns.incrementAndGet();
    }

    public void recordRunCompleted(long durationMs) {
        activeRuns.decrementAndGet();
        runsCompleted.increment();
        runDuration.record(Duration.ofMillis(durationMs));
    }

    public void recordRunFailed(long durationMs) {
        activeRuns.decrementAndGet();
        runsFailed.increment();
        runDuration.record(Duration.ofMillis(durationMs));
    }
}