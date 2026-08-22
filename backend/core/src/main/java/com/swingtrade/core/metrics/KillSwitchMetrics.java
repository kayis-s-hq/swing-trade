package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KillSwitchMetrics {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchMetrics.class);

    private final MeterRegistry meterRegistry;
    private final java.util.concurrent.atomic.AtomicBoolean active;
    private final Map<String, Counter> stateChanges = new ConcurrentHashMap<>();

    public KillSwitchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.active = new java.util.concurrent.atomic.AtomicBoolean(false);

        Gauge.builder("killswitch.active", active, v -> v.get() ? 1.0 : 0.0)
                .description("Kill switch active state")
                .register(meterRegistry);

        log.info("KillSwitchMetrics initialized");
    }

    public void setActive(boolean value) {
        active.set(value);
        String state = value ? "enabled" : "disabled";
        stateChanges
                .computeIfAbsent(state, s -> Counter.builder("killswitch.state_changed")
                        .description("Kill switch state changes")
                        .tag("state", s)
                        .register(meterRegistry));
        stateChanges.get(state).increment();
    }
}