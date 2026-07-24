package com.swingtrade.broker.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a trading portfolio in the paper trading system.
 */
public class Portfolio {
    private String portfolioId;
    private BigDecimal initialCapital;
    private BigDecimal currentCapital;
    private Map<String, Position> positions;
    private LocalDateTime lastUpdated;

    public Portfolio(String portfolioId, BigDecimal initialCapital) {
        this.portfolioId = portfolioId;
        this.initialCapital = initialCapital;
        this.currentCapital = initialCapital;
        this.positions = new ConcurrentHashMap<>();
        this.lastUpdated = LocalDateTime.now();
    }

    public Portfolio() {
        this.positions = new ConcurrentHashMap<>();
        this.currentCapital = BigDecimal.ZERO;
        this.lastUpdated = LocalDateTime.now();
    }

    public void addPosition(Position position) {
        positions.put(position.getPositionId(), position);
        lastUpdated = LocalDateTime.now();
    }

    public boolean removePosition(String positionId) {
        boolean removed = positions.remove(positionId) != null;
        if (removed) {
            lastUpdated = LocalDateTime.now();
        }
        return removed;
    }

    public Position getPosition(String positionId) {
        return positions.get(positionId);
    }

    public Map<String, Position> getPositions() {
        return positions;
    }

    public BigDecimal getTotalValue() {
        BigDecimal totalValue = initialCapital;
        for (Position position : positions.values()) {
            if (position.getStatus() == PositionStatus.OPEN) {
                totalValue = totalValue.add(position.getProfitLoss());
            }
        }
        return totalValue;
    }

    public BigDecimal getCurrentCapital() { return currentCapital; }
    public void setCurrentCapital(BigDecimal currentCapital) {
        this.currentCapital = currentCapital;
        this.lastUpdated = LocalDateTime.now();
    }
    public String getPortfolioId() { return portfolioId; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public BigDecimal getInitialCapital() { return initialCapital; }
    public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }

    public void setHoldings(java.util.List<Position> holdings) {
        for (Position position : holdings) {
            this.positions.put(position.getPositionId(), position);
        }
        this.lastUpdated = LocalDateTime.now();
    }

    public void setAvailableCash(BigDecimal availableCash) {
        this.currentCapital = availableCash;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setTotalCapital(BigDecimal totalCapital) {
        this.initialCapital = totalCapital;
        this.currentCapital = totalCapital;
        this.lastUpdated = LocalDateTime.now();
    }
}