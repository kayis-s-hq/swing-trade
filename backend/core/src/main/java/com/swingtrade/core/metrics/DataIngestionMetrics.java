package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataIngestionMetrics {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionMetrics.class);

    private final Counter candlesIngested;
    private final Timer ingestionDuration;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> fetchFailures = new ConcurrentHashMap<>();

    public DataIngestionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.candlesIngested = Counter.builder("data.candles_ingested")
                .description("Total OHLCV candles ingested")
                .register(meterRegistry);

        this.ingestionDuration = Timer.builder("data.ingestion.duration")
                .description("Data ingestion duration in seconds")
                .register(meterRegistry);

        log.info("DataIngestionMetrics initialized");
    }

    public void recordCandleIngested() {
        candlesIngested.increment();
    }

    public void recordIngestion(Duration duration) {
        ingestionDuration.record(duration);
    }

    public void recordFetchFailure(String source) {
        fetchFailures
                .computeIfAbsent(source, s -> Counter.builder("data.fetch_failures")
                        .description("Data fetch failures by source")
                        .tag("source", s)
                        .register(meterRegistry));
        fetchFailures.get(source).increment();
    }
}