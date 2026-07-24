package com.swingtrade.broker.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a trading portfolio in the paper trading system.
 * Manages all positions and provides portfolio-level calculations.
 */
public class Portfolio {
    private String portfolioId;
    private BigDecimal initialCapital;
    private BigDecimal currentCapital;
    private Map<String, Position> positions;
    private LocalDateTime lastUpdated;

    /**
     * Creates a new portfolio with default values.
     *
     * @param portfolioId unique identifier for the portfolio
     * @param initialCapital initial capital amount
     */
    public Portfolio(String portfolioId, BigDecimal initialCapital) {
        this.portfolioId = portfolioId;
        this.initialCapital = initialCapital;
        this.currentCapital = initialCapital;
        this.positions = new ConcurrentHashMap<>();
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Adds a position to the portfolio.
     *
     * @param position the position to add
     */
    public void addPosition(Position position) {
        positions.put(position.getPositionId(), position);
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Removes a position from the portfolio.
     *
     * @param positionId the ID of the position to remove
     * @return true if position was removed, false otherwise
     */
    public boolean removePosition(String positionId) {
        boolean removed = positions.remove(positionId) != null;
        if (removed) {
            lastUpdated = LocalDateTime.now();
        }
        return removed;
    }

    /**
     * Gets a position by its ID.
     *
     * @param positionId the ID of the position to retrieve
     * @return the position if found, null otherwise
     */
    public Position getPosition(String positionId) {
        return positions.get(positionId);
    }

    /**
     * Gets all positions in the portfolio.
     *
     * @return map of all positions
     */
    public Map<String, Position> getPositions() {
        return positions;
    }

    /**
     * Gets the current total value of the portfolio.
     *
     * @return total portfolio value
     */
    public BigDecimal getTotalValue() {
        BigDecimal totalValue = initialCapital;
        for (Position position : positions.values()) {
            if (position.getStatus() == PositionStatus.OPEN) {
                totalValue = totalValue.add(position.getProfitLoss());
            }
        }
        return totalValue;
    }

    /**
     * Gets the current capital balance.
     *
     * @return current capital balance
     */
    public BigDecimal getCurrentCapital() {
        return currentCapital;
    }

    /**
     * Sets the current capital balance.
     *
     * @param currentCapital new capital balance
     */
    public void setCurrentCapital(BigDecimal currentCapital) {
        this.currentCapital = currentCapital;
        lastUpdated = LocalDateTime.now();
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Gets the portfolio ID.
     *
     * @return portfolio ID
     */
    public String getPortfolioId() {
        return portfolioId;
    }

    /**
     * Gets the last updated timestamp.
     *
     * @return last updated timestamp
     */
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    // Default constructor for deserialization and testing
    public Portfolio() {
        this.positions = new ConcurrentHashMap<>();
        this.currentCapital = BigDecimal.ZERO;
        this.lastUpdated = LocalDateTime.now();
    }

    // Setter for holdings
    public void setHoldings(List<Position> holdings) {
        for (Position position : holdings) {
            this.positions.put(position.getPositionId(), position);
        }
        this.lastUpdated = LocalDateTime.now();
    }

    // Setter for available cash
    public void setAvailableCash(BigDecimal availableCash) {
        this.currentCapital = availableCash;
        this.lastUpdated = LocalDateTime.now();
    }

    // Setter for total capital
    public void setTotalCapital(BigDecimal totalCapital) {
        this.initialCapital = totalCapital;
        this.currentCapital = totalCapital;
        this.lastUpdated = LocalDateTime.now();
    }
}