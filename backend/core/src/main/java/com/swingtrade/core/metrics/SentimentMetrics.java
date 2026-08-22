package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SentimentMetrics {

    private static final Logger log = LoggerFactory.getLogger(SentimentMetrics.class);

    private final Counter analysesCompleted;
    private final Counter analysesFailed;
    private final Timer analysisDuration;

    public SentimentMetrics(MeterRegistry meterRegistry) {
        this.analysesCompleted = Counter.builder("sentiment.analyses.completed")
                .description("Sentiment analyses completed")
                .register(meterRegistry);

        this.analysesFailed = Counter.builder("sentiment.analyses.failed")
                .description("Sentiment analyses that failed")
                .register(meterRegistry);

        this.analysisDuration = Timer.builder("sentiment.analysis.duration")
                .description("Sentiment analysis duration in seconds")
                .register(meterRegistry);

        log.info("SentimentMetrics initialized");
    }

    public void recordCompleted() {
        analysesCompleted.increment();
    }

    public void recordFailed() {
        analysesFailed.increment();
    }

    public void recordDuration(Duration duration) {
        analysisDuration.record(duration);
    }
}