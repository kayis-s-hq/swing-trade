package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SignalMetrics {

    private static final Logger log = LoggerFactory.getLogger(SignalMetrics.class);

    private final Counter signalsGenerated;
    private final Counter signalsFiltered;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> signalsByType = new ConcurrentHashMap<>();

    public SignalMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.signalsGenerated = Counter.builder("signals.generated")
                .description("Total signals generated")
                .register(meterRegistry);

        this.signalsFiltered = Counter.builder("signals.filtered")
                .description("Total signals filtered out")
                .register(meterRegistry);

        log.info("SignalMetrics initialized");
    }

    public void recordSignalGenerated() {
        signalsGenerated.increment();
    }

    public void recordSignalFiltered() {
        signalsFiltered.increment();
    }

    public void recordSignalType(String type) {
        signalsByType
                .computeIfAbsent(type, t -> Counter.builder("signals.by_type")
                        .description("Signals by type (buy, hold, sell)")
                        .tag("type", t)
                        .register(meterRegistry));
        signalsByType.get(type).increment();
    }
}