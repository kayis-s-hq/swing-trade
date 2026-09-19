package com.swingtrade.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradeMetricsTest {

    @Test
    void recordsOpenCloseOutcomesDurationsPnlAndSymbols() {
        var registry = new SimpleMeterRegistry();
        var metrics = new TradeMetrics(registry);

        metrics.recordTradeOpen();
        metrics.recordTradeClose("stop_loss");
        metrics.recordTradeClose("TARGET_HIT");
        metrics.recordTradeClose("time_stop");
        metrics.recordTradeClose("trend_break");
        metrics.recordTradeClose("manual");
        metrics.recordTradeClose("unknown");
        metrics.recordTradeDuration(1.5);
        metrics.recordTradePnL(-42.0);
        metrics.recordSymbolTrade("TCS");
        metrics.recordSymbolTrade("TCS");
        metrics.recordSymbolTrade("INFY");

        assertThat(registry.get("trades.open").tag("broker", "paper").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("trades.closed").tag("broker", "paper").counter().count()).isEqualTo(6.0);
        assertThat(registry.get("trades.outcome").tag("reason", "stop_loss").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("trades.outcome").tag("reason", "target_hit").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("trades.outcome").tag("reason", "time_stop").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("trades.outcome").tag("reason", "trend_break").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("trades.outcome").tag("reason", "manual").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("trades.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("trades.pnl").timer().count()).isEqualTo(1L);
        assertThat(registry.get("trades.per.symbol").tag("symbol", "TCS").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("trades.per.symbol").tag("symbol", "INFY").counter().count()).isEqualTo(1.0);
    }
}
