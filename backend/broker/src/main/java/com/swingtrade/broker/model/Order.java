package com.swingtrade.broker.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a trading order in the paper trading system.
 * This class encapsulates all the information needed to execute an order.
 */
public class Order {
    private String orderId;
    private String symbol;
    private OrderType type;
    private TradeDirection direction;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal limitPrice;
    private BigDecimal stopPrice;
    private OrderStatus status;
    private LocalDateTime timestamp;
    private LocalDateTime executionTime;
    private BigDecimal commission;
    private Map<String, Object> additionalProperties;
    
    /**
     * Creates a new order with default values.
     */
    public Order() {
        this.status = OrderStatus.PENDING;
        this.timestamp = LocalDateTime.now();
        this.commission = BigDecimal.ZERO;
    }
    
    /**
     * Creates a new order with specified parameters.
     *
     * @param orderId unique identifier for the order
     * @param symbol trading symbol (e.g., "AAPL")
     * @param type order type (MARKET, LIMIT, etc.)
     * @param direction trade direction (LONG or SHORT)
     * @param quantity number of shares/contracts
     * @param price execution price (for MARKET orders)
     * @param limitPrice limit price (for LIMIT orders)
     * @param stopPrice stop price (for STOP orders)
     */
    public Order(String orderId, String symbol, OrderType type, TradeDirection direction, 
                 BigDecimal quantity, BigDecimal price, BigDecimal limitPrice, BigDecimal stopPrice) {
        this();
        this.orderId = orderId;
        this.symbol = symbol;
        this.type = type;
        this.direction = direction;
        this.quantity = quantity;
        this.price = price;
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
    }
    
    // Getters and setters
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public OrderType getType() {
        return type;
    }
    
    public void setType(OrderType type) {
        this.type = type;
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
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public BigDecimal getLimitPrice() {
        return limitPrice;
    }
    
    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }
    
    public BigDecimal getStopPrice() {
        return stopPrice;
    }
    
    public void setStopPrice(BigDecimal stopPrice) {
        this.stopPrice = stopPrice;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public LocalDateTime getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(LocalDateTime executionTime) {
        this.executionTime = executionTime;
    }
    
    public BigDecimal getCommission() {
        return commission;
    }
    
    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}
