package com.swingtrade.api.dto;

import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.PositionSummary;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for trading positions.
 * Represents an open or closed position with all relevant details.
 */
public class PositionResponse {

    private Long id;
    private String symbol;
    private BigDecimal entryPrice;
    private LocalDate entryDate;
    private Integer quantity;
    private BigDecimal stopLoss;
    private BigDecimal target;
    private PositionStatus status;
    private String entryReason;
    private BigDecimal currentPrice;
    private BigDecimal unrealizedPnL;
    private BigDecimal unrealizedPnLPercent;
    private BigDecimal averagePrice;
    private BigDecimal totalValue;

    public PositionResponse() {
    }

    public PositionResponse(Position position) {
        this.id = position.id();
        this.symbol = position.symbol();
        this.entryPrice = position.entryPrice();
        this.entryDate = position.entryDate();
        this.quantity = position.quantity();
        this.stopLoss = position.stopLoss();
        this.target = position.target();
        this.status = PositionStatus.valueOf(position.status().name());
        this.entryReason = position.entryReason();
        this.currentPrice = position.currentPrice();

        // Calculate P&L
        if (currentPrice != null && entryPrice != null) {
            this.unrealizedPnL = position.calculateUnrealizedPnL(currentPrice);
            this.unrealizedPnLPercent = position.calculatePnLPercent(currentPrice);
        }

        // Calculate total value
        if (currentPrice != null && quantity != null) {
            this.totalValue = currentPrice.multiply(BigDecimal.valueOf(quantity));
        }

        this.averagePrice = entryPrice;
    }

    /** Builds the list-item response from the lightweight projection (no aggregate load). */
    public PositionResponse(PositionSummary position) {
        this.id = position.id();
        this.symbol = position.symbol();
        this.entryPrice = position.entryPrice();
        this.entryDate = position.entryDate();
        this.quantity = position.quantity();
        this.stopLoss = position.stopLoss();
        this.target = position.target();
        this.status = position.status();
        this.entryReason = position.entryReason();
        this.currentPrice = position.currentPrice();

        if (currentPrice != null && entryPrice != null) {
            this.unrealizedPnL = position.calculateUnrealizedPnL(currentPrice);
            this.unrealizedPnLPercent = position.calculatePnLPercent(currentPrice);
        }

        if (currentPrice != null && quantity != null) {
            this.totalValue = currentPrice.multiply(BigDecimal.valueOf(quantity));
        }

        this.averagePrice = entryPrice;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
    }

    public PositionStatus getStatus() {
        return status;
    }

    public void setStatus(PositionStatus status) {
        this.status = status;
    }

    public String getEntryReason() {
        return entryReason;
    }

    public void setEntryReason(String entryReason) {
        this.entryReason = entryReason;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getUnrealizedPnL() {
        return unrealizedPnL;
    }

    public void setUnrealizedPnL(BigDecimal unrealizedPnL) {
        this.unrealizedPnL = unrealizedPnL;
    }

    public BigDecimal getUnrealizedPnLPercent() {
        return unrealizedPnLPercent;
    }

    public void setUnrealizedPnLPercent(BigDecimal unrealizedPnLPercent) {
        this.unrealizedPnLPercent = unrealizedPnLPercent;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    }
