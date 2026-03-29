package com.swingtrade.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an open paper trading position DTO.
 * Used for lightweight position representation in the API layer.
 */
public class Position {
    private Long id;
    private String symbol;
    private String direction; // LONG/SHORT
    private BigDecimal entryPrice;
    private Integer quantity;
    private LocalDate entryDate;
    private LocalDateTime entryTime;
    private BigDecimal stopLoss;
    private BigDecimal target;
    private BigDecimal currentPrice;
    private String status;
    private String entryReason;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;

    /**
     * Default constructor
     */
    public Position() {}

    /**
     * Constructor with core parameters
     * @param symbol Trading symbol
     * @param direction Long or short position
     * @param entryPrice Entry price
     * @param quantity Quantity held
     * @param entryDate Entry date
     * @param status Position status
     */
    public Position(String symbol, String direction, BigDecimal entryPrice, Integer quantity,
                   LocalDate entryDate, String status) {
        this.symbol = symbol;
        this.direction = direction;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.entryDate = entryDate;
        this.status = status;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public BigDecimal getStopLoss() { return stopLoss; }
    public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }

    public BigDecimal getTarget() { return target; }
    public void setTarget(BigDecimal target) { this.target = target; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEntryReason() { return entryReason; }
    public void setEntryReason(String entryReason) { this.entryReason = entryReason; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public BigDecimal getProfitLoss() { return profitLoss; }
    public void setProfitLoss(BigDecimal profitLoss) { this.profitLoss = profitLoss; }
}
