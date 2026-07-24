package com.swingtrade.broker.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents the response from a broker order placement.
 * Contains order confirmation details and broker-specific information.
 */
public class OrderResponse {

    /**
     * Unique order ID assigned by the broker (e.g., Kite order ID)
     */
    private String brokerOrderId;

    /**
     * Internal order reference ID
     */
    private String internalOrderId;

    /**
     * Trading symbol (e.g., RELIANCE-EQ)
     */
    private String symbol;

    /**
     * Exchange where order is placed
     */
    private Exchange exchange;

    /**
     * Order type
     */
    private OrderType type;

    /**
     * Trade direction
     */
    private TradeDirection direction;

    /**
     * Order quantity
     */
    private BigDecimal quantity;

    /**
     * Order price (for MARKET orders, this is the estimated price)
     */
    private BigDecimal price;

    /**
     * Limit price (for LIMIT orders)
     */
    private BigDecimal limitPrice;

    /**
     * Stop loss price (for STOP_LOSS orders)
     */
    private BigDecimal stopPrice;

    /**
     * Current order status
     */
    private OrderStatus status;

    /**
     * Order placement timestamp
     */
    private LocalDateTime timestamp;

    /**
     * Broker-specific exchange order ID (when available)
     */
    private String exchangeOrderId;

    /**
     * Transaction charge
     */
    private BigDecimal transactionCharge;

    /**
     * Securities Transaction Tax (STT)
     */
    private BigDecimal stt;

    /**
     * Other charges (stamp duty, etc.)
     */
    private BigDecimal otherCharges;

    /**
     * Total broker charges
     */
    private BigDecimal brokerCharges;

    /**
     * Order placement message/log
     */
    private String message;

    public OrderResponse() {
        this.status = OrderStatus.PENDING;
        this.timestamp = LocalDateTime.now();
        this.transactionCharge = BigDecimal.ZERO;
        this.stt = BigDecimal.ZERO;
        this.otherCharges = BigDecimal.ZERO;
        this.brokerCharges = BigDecimal.ZERO;
    }

    // Getters and Setters

    public String getBrokerOrderId() {
        return brokerOrderId;
    }

    public void setBrokerOrderId(String brokerOrderId) {
        this.brokerOrderId = brokerOrderId;
    }

    public String getInternalOrderId() {
        return internalOrderId;
    }

    public void setInternalOrderId(String internalOrderId) {
        this.internalOrderId = internalOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
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

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }

    public BigDecimal getTransactionCharge() {
        return transactionCharge;
    }

    public void setTransactionCharge(BigDecimal transactionCharge) {
        this.transactionCharge = transactionCharge;
    }

    public BigDecimal getStt() {
        return stt;
    }

    public void setStt(BigDecimal stt) {
        this.stt = stt;
    }

    public BigDecimal getOtherCharges() {
        return otherCharges;
    }

    public void setOtherCharges(BigDecimal otherCharges) {
        this.otherCharges = otherCharges;
    }

    public BigDecimal getBrokerCharges() {
        return brokerCharges;
    }

    public void setBrokerCharges(BigDecimal brokerCharges) {
        this.brokerCharges = brokerCharges;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "OrderResponse{" +
                "brokerOrderId='" + brokerOrderId + '\'' +
                ", internalOrderId='" + internalOrderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", exchange=" + exchange +
                ", type=" + type +
                ", direction=" + direction +
                ", quantity=" + quantity +
                ", price=" + price +
                ", limitPrice=" + limitPrice +
                ", status=" + status +
                ", timestamp=" + timestamp +
                ", message='" + message + '\'' +
                '}';
    }
}