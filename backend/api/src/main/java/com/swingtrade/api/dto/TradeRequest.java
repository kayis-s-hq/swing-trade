package com.swingtrade.api.dto;

import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.TradeDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO for creating a trade position.
 * Contains all necessary parameters for initiating a new position.
 */
public class TradeRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Direction is required")
    private TradeDirection direction;

    @NotNull(message = "Order type is required")
    private OrderType orderType;

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Limit price must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal limitPrice;

    @DecimalMin(value = "0.01", message = "Stop price must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal stopPrice;

    @DecimalMin(value = "0.01", message = "Target price must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Target price must have at most 2 decimal places")
    private BigDecimal target;

    private String entryReason;

    @DecimalMin(value = "0.0", message = "Risk tolerance must be non-negative")
    private Double riskTolerance;

    public TradeRequest() {
    }

    public TradeRequest(String symbol, Integer quantity, TradeDirection direction, OrderType orderType) {
        this.symbol = symbol.toUpperCase();
        this.quantity = quantity;
        this.direction = direction;
        this.orderType = orderType;
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol.toUpperCase();
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public TradeDirection getDirection() {
        return direction;
    }

    public void setDirection(TradeDirection direction) {
        this.direction = direction;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price != null ? price.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice != null ? limitPrice.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getStopPrice() {
        return stopPrice;
    }

    public void setStopPrice(BigDecimal stopPrice) {
        this.stopPrice = stopPrice != null ? stopPrice.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target != null ? target.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public String getEntryReason() {
        return entryReason;
    }

    public void setEntryReason(String entryReason) {
        this.entryReason = entryReason;
    }

    public Double getRiskTolerance() {
        return riskTolerance;
    }

    public void setRiskTolerance(Double riskTolerance) {
        this.riskTolerance = riskTolerance;
    }

    /**
     * Validates the trade request.
     * @return true if valid
     */
    public boolean isValid() {
        if (!isValidSymbol()) return false;
        if (quantity == null || quantity < 1) return false;
        if (direction == null) return false;
        if (orderType == null) return false;

        // For LIMIT and STOP_LIMIT orders, limitPrice is required
        if ((orderType == OrderType.LIMIT || orderType == OrderType.STOP_LIMIT) && limitPrice == null) {
            return false;
        }

        // For STOP and STOP_LIMIT orders, stopPrice is required
        if ((orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT) && stopPrice == null) {
            return false;
        }
        return true;
    }

    private boolean isValidSymbol() {
        return symbol != null && symbol.matches("[A-Za-z0-9]{1,10}");
    }
}