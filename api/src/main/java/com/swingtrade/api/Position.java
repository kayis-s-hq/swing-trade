package com.swingtrade.api;

import java.time.LocalDateTime;

/**
 * Represents an open paper trading position
 */
public class Position {
    private String symbol;
    private String type; // LONG/SHORT
    private Double entryPrice;
    private Double quantity;
    private LocalDateTime entryTime;
    private Double currentValue;
    private Double profitLoss;
    
    /**
     * Default constructor
     */
    public Position() {}
    
    /**
     * Constructor with parameters
     * @param symbol Trading symbol
     * @param type Long or short position
     * @param entryPrice Entry price
     * @param quantity Quantity held
     * @param entryTime Entry timestamp
     * @param currentValue Current value
     * @param profitLoss Profit or loss
     */
    public Position(String symbol, String type, Double entryPrice, Double quantity, 
                   LocalDateTime entryTime, Double currentValue, Double profitLoss) {
        this.symbol = symbol;
        this.type = type;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.entryTime = entryTime;
        this.currentValue = currentValue;
        this.profitLoss = profitLoss;
    }
    
    // Getters and setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(Double entryPrice) { this.entryPrice = entryPrice; }
    
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    
    public Double getCurrentValue() { return currentValue; }
    public void setCurrentValue(Double currentValue) { this.currentValue = currentValue; }
    
    public Double getProfitLoss() { return profitLoss; }
    public void setProfitLoss(Double profitLoss) { this.profitLoss = profitLoss; }
}
