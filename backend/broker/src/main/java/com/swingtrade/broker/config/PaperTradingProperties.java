package com.swingtrade.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Paper trading configuration properties.
 * Centralizes all paper.trading.* properties that were previously dead config.
 */
@Component
@ConfigurationProperties(prefix = "paper.trading")
public class PaperTradingProperties {

    private boolean enabled = true;
    private int maxConcurrentPositions = 5;
    private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(200000);
    private BigDecimal initialBalance = BigDecimal.valueOf(1000000);
    private long signalExecutionDelay = 30000;
    private boolean positionSizeLimitsEnabled = true;
    private int positionSizeAlertThreshold = 80;
    private boolean orderExecutionEnabled = true;
    private long orderExecutionLatencyMillis = 100;
    private boolean statePersistenceEnabled = true;
    private String snapshotCron = "0 45 15 * * MON-FRI";
    private String monitorCron = "0 30 15 * * MON-FRI";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxConcurrentPositions() { return maxConcurrentPositions; }
    public void setMaxConcurrentPositions(int maxConcurrentPositions) { this.maxConcurrentPositions = maxConcurrentPositions; }

    public BigDecimal getMaxCapitalPerPosition() { return maxCapitalPerPosition; }
    public void setMaxCapitalPerPosition(BigDecimal maxCapitalPerPosition) { this.maxCapitalPerPosition = maxCapitalPerPosition; }

    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }

    public long getSignalExecutionDelay() { return signalExecutionDelay; }
    public void setSignalExecutionDelay(long signalExecutionDelay) { this.signalExecutionDelay = signalExecutionDelay; }

    public boolean isPositionSizeLimitsEnabled() { return positionSizeLimitsEnabled; }
    public void setPositionSizeLimitsEnabled(boolean positionSizeLimitsEnabled) { this.positionSizeLimitsEnabled = positionSizeLimitsEnabled; }

    public int getPositionSizeAlertThreshold() { return positionSizeAlertThreshold; }
    public void setPositionSizeAlertThreshold(int positionSizeAlertThreshold) { this.positionSizeAlertThreshold = positionSizeAlertThreshold; }

    public boolean isOrderExecutionEnabled() { return orderExecutionEnabled; }
    public void setOrderExecutionEnabled(boolean orderExecutionEnabled) { this.orderExecutionEnabled = orderExecutionEnabled; }

    public long getOrderExecutionLatencyMillis() { return orderExecutionLatencyMillis; }
    public void setOrderExecutionLatencyMillis(long orderExecutionLatencyMillis) { this.orderExecutionLatencyMillis = orderExecutionLatencyMillis; }

    public boolean isStatePersistenceEnabled() { return statePersistenceEnabled; }
    public void setStatePersistenceEnabled(boolean statePersistenceEnabled) { this.statePersistenceEnabled = statePersistenceEnabled; }

    public String getSnapshotCron() { return snapshotCron; }
    public void setSnapshotCron(String snapshotCron) { this.snapshotCron = snapshotCron; }

    public String getMonitorCron() { return monitorCron; }
    public void setMonitorCron(String monitorCron) { this.monitorCron = monitorCron; }
}