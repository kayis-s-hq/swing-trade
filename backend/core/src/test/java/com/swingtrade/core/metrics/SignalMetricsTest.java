package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalMetricsTest {

    @Test
    void recordsGeneratedFilteredAndPerTypeSignals() {
        var registry = new SimpleMeterRegistry();
        var metrics = new SignalMetrics(registry);

        metrics.recordSignalGenerated();
        metrics.recordSignalGenerated();
        metrics.recordSignalFiltered();
        metrics.recordSignalType("buy");
        metrics.recordSignalType("buy");
        metrics.recordSignalType("sell");

        assertThat(registry.get("signals.generated").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("signals.filtered").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("signals.by_type").tag("type", "buy").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("signals.by_type").tag("type", "sell").counter().count()).isEqualTo(1.0);
    }
}
