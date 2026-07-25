package com.swingtrade.broker.model;

import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents the response from a broker order placement.
 */
public class OrderResponse {
    private String brokerOrderId;
    private String internalOrderId;
    private String symbol;
    private Exchange exchange;
    private OrderType type;
    private TradeDirection direction;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal limitPrice;
    private BigDecimal stopPrice;
    private OrderStatus status;
    private LocalDateTime timestamp;
    private String exchangeOrderId;
    private BigDecimal transactionCharge;
    private BigDecimal stt;
    private BigDecimal otherCharges;
    private BigDecimal brokerCharges;
    private String message;

    public OrderResponse() {
        this.status = OrderStatus.PENDING;
        this.timestamp = LocalDateTime.now();
        this.transactionCharge = BigDecimal.ZERO;
        this.stt = BigDecimal.ZERO;
        this.otherCharges = BigDecimal.ZERO;
        this.brokerCharges = BigDecimal.ZERO;
    }

    public String getBrokerOrderId() { return brokerOrderId; }
    public void setBrokerOrderId(String brokerOrderId) { this.brokerOrderId = brokerOrderId; }
    public String getInternalOrderId() { return internalOrderId; }
    public void setInternalOrderId(String internalOrderId) { this.internalOrderId = internalOrderId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Exchange getExchange() { return exchange; }
    public void setExchange(Exchange exchange) { this.exchange = exchange; }
    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
    public TradeDirection getDirection() { return direction; }
    public void setDirection(TradeDirection direction) { this.direction = direction; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
    public BigDecimal getStopPrice() { return stopPrice; }
    public void setStopPrice(BigDecimal stopPrice) { this.stopPrice = stopPrice; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }
    public BigDecimal getTransactionCharge() { return transactionCharge; }
    public void setTransactionCharge(BigDecimal transactionCharge) { this.transactionCharge = transactionCharge; }
    public BigDecimal getStt() { return stt; }
    public void setStt(BigDecimal stt) { this.stt = stt; }
    public BigDecimal getOtherCharges() { return otherCharges; }
    public void setOtherCharges(BigDecimal otherCharges) { this.otherCharges = otherCharges; }
    public BigDecimal getBrokerCharges() { return brokerCharges; }
    public void setBrokerCharges(BigDecimal brokerCharges) { this.brokerCharges = brokerCharges; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}