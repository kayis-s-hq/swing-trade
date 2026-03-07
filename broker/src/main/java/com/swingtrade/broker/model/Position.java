package com.swingtrade.broker.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a trading position in the paper trading system.
 * A position tracks holdings of a particular asset with associated risk metrics.
 */
public class Position {
    private String positionId;
    private String symbol;
    private TradeDirection direction;
    private BigDecimal quantity;
    private BigDecimal entryPrice;
    private BigDecimal currentPrice;
    private BigDecimal profitLoss;
    private BigDecimal slPrice;
    private BigDecimal targetPrice;
    private PositionStatus status;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private List<Order> orders;
    
    /**
     * Creates a new position with default values.
     */
    public Position() {
        this.status = PositionStatus.OPEN;
        this.entryTime = LocalDateTime.now();
        this.profitLoss = BigDecimal.ZERO;
    }
    
    /**
     * Creates a new position with specified parameters.
     *
     * @param positionId unique identifier for the position
     * @param symbol trading symbol (e.g., "AAPL")
     * @param direction trade direction (LONG or SHORT)
     * @param quantity number of shares/contracts
     * @param entryPrice entry price of the position
     * @param slPrice stop loss price
     * @param targetPrice take profit price
     */
    public Position(String positionId, String symbol, TradeDirection direction, 
                   BigDecimal quantity, BigDecimal entryPrice, BigDecimal slPrice, BigDecimal targetPrice) {
        this();
        this.positionId = positionId;
        this.symbol = symbol;
        this.direction = direction;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.slPrice = slPrice;
        this.targetPrice = targetPrice;
        this.currentPrice = entryPrice;
    }
    
    // Getters and setters
    public String getPositionId() {
        return positionId;
    }
    
    public void setPositionId(String positionId) {
        this.positionId = positionId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public TradeDirection getDirection() {
        return direction;
    }
    
    public void setDirection(TradeDirection direction) {
        this.direction = direction;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getEntryPrice() {
        return entryPrice;
    }
    
    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }
    
    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
        calculateProfitLoss();
    }
    
    public BigDecimal getProfitLoss() {
        return profitLoss;
    }
    
    private void calculateProfitLoss() {
        if (entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            profitLoss = BigDecimal.ZERO;
            return;
        }
        
        BigDecimal priceChange = (direction == TradeDirection.LONG) 
            ? currentPrice.subtract(entryPrice) 
            : entryPrice.subtract(currentPrice);
            
        profitLoss = priceChange.multiply(quantity);
    }
    
    public BigDecimal getSlPrice() {
        return slPrice;
    }
    
    public void setSlPrice(BigDecimal slPrice) {
        this.slPrice = slPrice;
    }
    
    public BigDecimal getTargetPrice() {
        return targetPrice;
    }
    
    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }
    
    public PositionStatus getStatus() {
        return status;
    }
    
    public void setStatus(PositionStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getEntryTime() {
        return entryTime;
    }
    
    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }
    
    public LocalDateTime getExitTime() {
        return exitTime;
    }
    
    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }
    
    public List<Order> getOrders() {
        return orders;
    }
    
    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }
}
