package com.swingtrade.api;

import java.time.LocalDateTime;

/**
 * Represents a trading signal for swing trading
 */
public class Signal {
    private String symbol;
    private String type; // BUY/SELL
    private Double price;
    private LocalDateTime timestamp;
    private String reason;
    
    /**
     * Default constructor
     */
    public Signal() {}
    
    /**
     * Constructor with parameters
     * @param symbol Trading symbol
     * @param type Buy or sell signal
     * @param price Target price
     * @param timestamp Signal timestamp
     * @param reason Reason for the signal
     */
    public Signal(String symbol, String type, Double price, LocalDateTime timestamp, String reason) {
        this.symbol = symbol;
        this.type = type;
        this.price = price;
        this.timestamp = timestamp;
        this.reason = reason;
    }
    
    // Getters and setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
