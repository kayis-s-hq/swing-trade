package com.swingtrade.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiTradeMetricsTest {

    private MeterRegistry meterRegistry;
    private ApiTradeMetrics tradeMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        tradeMetrics = new ApiTradeMetrics(meterRegistry);
    }

    @Test
    void testRecordTradeOpen_IncrementsCounter() {
        // Arrange
        Counter before = meterRegistry.counter("trades.open");
        assertEquals(0, before.count());

        // Act
        tradeMetrics.recordTradeOpen();

        // Assert
        assertEquals(1, before.count());
    }

    @Test
    void testRecordTradeClose_WithStopLoss() {
        // Arrange
        Counter before = meterRegistry.counter("trades.outcome", "reason", "stop_loss");
        assertEquals(0, before.count());

        // Act
        tradeMetrics.recordTradeClose("STOP_LOSS");

        // Assert
        assertEquals(1, before.count());
    }

    @Test
    void testRecordTradeClose_WithTargetHit() {
        // Arrange
        Counter before = meterRegistry.counter("trades.outcome", "reason", "target_hit");
        assertEquals(0, before.count());

        // Act
        tradeMetrics.recordTradeClose("TARGET_HIT");

        // Assert
        assertEquals(1, before.count());
    }

    @Test
    void testRecordTradeClose_WithTimeStop() {
        // Arrange
        Counter before = meterRegistry.counter("trades.outcome", "reason", "time_stop");
        assertEquals(0, before.count());

        // Act
        tradeMetrics.recordTradeClose("TIME_STOP");

        // Assert
        assertEquals(1, before.count());
    }

    @Test
    void testRecordTradeClose_WithTrendBreak() {
        // Arrange
        Counter before = meterRegistry.counter("trades.outcome", "reason", "trend_break");
        assertEquals(0, before.count());

        // Act
        tradeMetrics.recordTradeClose("TREND_BREAK");

        // Assert
        assertEquals(1, before.count());
    }

    @Test
    void testRecordTradeClose_WithManual() {
        // Arrange
        Counter before = meterRegistry.counter("trades.outcome", "reason", "manual");
        assertEquals(0, before.count());

        // Act
        tradeMetrics.recordTradeClose("MANUAL");

        // Assert
        assertEquals(1, before.count());
    }

    @Test
    void testRecordTradeClose_InvalidReason() {
        // Act - should not throw exception for invalid reason
        assertDoesNotThrow(() -> tradeMetrics.recordTradeClose("INVALID_REASON"));
    }

    @Test
    void testRecordTradeDuration_RecordsTimer() {
        // Act
        assertDoesNotThrow(() -> tradeMetrics.recordTradeDuration(10.5));
    }

    @Test
    void testRecordTradePnL_RecordsTimer() {
        // Act
        assertDoesNotThrow(() -> tradeMetrics.recordTradePnL(1500.0));
        assertDoesNotThrow(() -> tradeMetrics.recordTradePnL(-500.0));
    }

    @Test
    void testRecordSymbolTrade_CreatesCounter() {
        // Act
        tradeMetrics.recordSymbolTrade("RELIANCE");
        tradeMetrics.recordSymbolTrade("RELIANCE");
        tradeMetrics.recordSymbolTrade("TCS");

        // Assert
        Counter relianceCounter = meterRegistry.counter("trades.per.symbol", "symbol", "RELIANCE");
        assertEquals(2, relianceCounter.count());

        Counter tcsCounter = meterRegistry.counter("trades.per.symbol", "symbol", "TCS");
        assertEquals(1, tcsCounter.count());
    }

    @Test
    void testConstructor_RegistersAllCounters() {
        // Assert - verify all expected counters are registered
        assertNotNull(meterRegistry.get("trades.open").meter());
        assertNotNull(meterRegistry.get("trades.closed").meter());
        assertNotNull(meterRegistry.get("trades.outcome").meter());
        assertNotNull(meterRegistry.get("trades.duration").timer());
        assertNotNull(meterRegistry.get("trades.pnl").timer());
    }
}
