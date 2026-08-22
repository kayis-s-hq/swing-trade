package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LlmMetrics {

    private static final Logger log = LoggerFactory.getLogger(LlmMetrics.class);

    private final Timer callDuration;
    private final Counter callsSuccessful;
    private final Counter callsFailed;
    private final Counter sentimentAnalyzed;

    public LlmMetrics(MeterRegistry meterRegistry) {
        this.callDuration = Timer.builder("llm.call.duration")
                .description("LLM call duration in seconds")
                .register(meterRegistry);

        this.callsSuccessful = Counter.builder("llm.calls")
                .description("LLM call outcomes")
                .tag("result", "success")
                .register(meterRegistry);

        this.callsFailed = Counter.builder("llm.calls")
                .description("LLM call outcomes")
                .tag("result", "failure")
                .register(meterRegistry);

        this.sentimentAnalyzed = Counter.builder("llm.sentiment.analyzed")
                .description("Sentiment analyses performed via LLM")
                .register(meterRegistry);

        log.info("LlmMetrics initialized");
    }

    public void recordCall(Duration duration, boolean success) {
        callDuration.record(duration);
        if (success) {
            callsSuccessful.increment();
        } else {
            callsFailed.increment();
        }
    }

    public void recordSentimentAnalyzed() {
        sentimentAnalyzed.increment();
    }
}