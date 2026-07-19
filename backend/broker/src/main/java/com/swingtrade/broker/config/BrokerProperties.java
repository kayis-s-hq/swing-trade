package com.swingtrade.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centralized broker, risk, and notification configuration.
 * Replaces scattered @Value annotations across broker module classes.
 */
@Component
@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    // Position limits
    private int maxConcurrentPositions = 3;
    private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(10000);

    // Trade sizing
    private BigDecimal maxCapitalPerTrade = BigDecimal.valueOf(50000);
    private BigDecimal initialCapital = BigDecimal.valueOf(1000000);
    private BigDecimal maxPositionSizePercentage = BigDecimal.valueOf(10);
    private BigDecimal minPositionSizePercentage = BigDecimal.valueOf(1);

    // Kill switch
    private boolean killSwitchEnabled = true;
    private boolean killSwitchActive = false;

    // Circuit breaker
    private BigDecimal dailyLossCircuitBreaker = BigDecimal.valueOf(2.0);

    // Mode
    private String mode = "paper";

    public int getMaxConcurrentPositions() { return maxConcurrentPositions; }
    public void setMaxConcurrentPositions(int maxConcurrentPositions) { this.maxConcurrentPositions = maxConcurrentPositions; }

    public BigDecimal getMaxCapitalPerPosition() { return maxCapitalPerPosition; }
    public void setMaxCapitalPerPosition(BigDecimal maxCapitalPerPosition) { this.maxCapitalPerPosition = maxCapitalPerPosition; }

    public BigDecimal getMaxCapitalPerTrade() { return maxCapitalPerTrade; }
    public void setMaxCapitalPerTrade(BigDecimal maxCapitalPerTrade) { this.maxCapitalPerTrade = maxCapitalPerTrade; }

    public BigDecimal getInitialCapital() { return initialCapital; }
    public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }

    public BigDecimal getMaxPositionSizePercentage() { return maxPositionSizePercentage; }
    public void setMaxPositionSizePercentage(BigDecimal maxPositionSizePercentage) { this.maxPositionSizePercentage = maxPositionSizePercentage; }

    public BigDecimal getMinPositionSizePercentage() { return minPositionSizePercentage; }
    public void setMinPositionSizePercentage(BigDecimal minPositionSizePercentage) { this.minPositionSizePercentage = minPositionSizePercentage; }

    public boolean isKillSwitchEnabled() { return killSwitchEnabled; }
    public void setKillSwitchEnabled(boolean killSwitchEnabled) { this.killSwitchEnabled = killSwitchEnabled; }

    public boolean isKillSwitchActive() { return killSwitchActive; }
    public void setKillSwitchActive(boolean killSwitchActive) { this.killSwitchActive = killSwitchActive; }

    public BigDecimal getDailyLossCircuitBreaker() { return dailyLossCircuitBreaker; }
    public void setDailyLossCircuitBreaker(BigDecimal dailyLossCircuitBreaker) { this.dailyLossCircuitBreaker = dailyLossCircuitBreaker; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}