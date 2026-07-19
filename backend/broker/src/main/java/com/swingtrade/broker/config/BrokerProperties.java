package com.swingtrade.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centralized broker/risk configuration.
 * Replaces scattered @Value annotations across CapitalTracker and PositionLimitChecker.
 */
@Component
@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    private int maxConcurrentPositions = 3;
    private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(10000);

    public int getMaxConcurrentPositions() {
        return maxConcurrentPositions;
    }

    public void setMaxConcurrentPositions(int maxConcurrentPositions) {
        this.maxConcurrentPositions = maxConcurrentPositions;
    }

    public BigDecimal getMaxCapitalPerPosition() {
        return maxCapitalPerPosition;
    }

    public void setMaxCapitalPerPosition(BigDecimal maxCapitalPerPosition) {
        this.maxCapitalPerPosition = maxCapitalPerPosition;
    }
}