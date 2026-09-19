package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KillSwitchMetricsTest {

    @Test
    void exposesCurrentStateAndCountsEachStateChange() {
        var registry = new SimpleMeterRegistry();
        var metrics = new KillSwitchMetrics(registry);

        assertThat(registry.get("killswitch.active").gauge().value()).isZero();

        metrics.setActive(true);
        metrics.setActive(true);
        metrics.setActive(false);

        assertThat(registry.get("killswitch.active").gauge().value()).isZero();
        assertThat(registry.get("killswitch.state_changed").tag("state", "enabled").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("killswitch.state_changed").tag("state", "disabled").counter().count())
                .isEqualTo(1.0);
    }
}
