package com.swingtrade.data.entity;

import com.swingtrade.domain.Position;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the Position domain model.
 */
@Entity
@Table(name = "positions", indexes = {
    @Index(name = "idx_positions_symbol", columnList = "symbol", unique = false),
    @Index(name = "idx_positions_status", columnList = "status", unique = false)
})
public class PositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "entry_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    private Integer quantity;

    @Column(name = "stop_loss", precision = 15, scale = 4)
    private BigDecimal stopLoss;

    @Column(precision = 15, scale = 4)
    private BigDecimal target;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String entryReason;

    @Column(name = "current_price", precision = 15, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public PositionEntity() {
    }

    public PositionEntity(Position position) {
        this.symbol = position.symbol();
        this.entryPrice = position.entryPrice();
        this.entryDate = position.entryDate();
        this.quantity = position.quantity();
        this.stopLoss = position.stopLoss();
        this.target = position.target();
        this.status = position.status().name();
        this.entryReason = position.entryReason();
        this.currentPrice = position.currentPrice();
    }

    public static PositionEntity fromDomain(Position position) {
        PositionEntity entity = new PositionEntity();
        entity.setSymbol(position.symbol());
        entity.setEntryPrice(position.entryPrice());
        entity.setEntryDate(position.entryDate());
        entity.setQuantity(position.quantity());
        entity.setStopLoss(position.stopLoss());
        entity.setTarget(position.target());
        entity.setStatus(position.status().name());
        entity.setEntryReason(position.entryReason());
        entity.setCurrentPrice(position.currentPrice());
        return entity;
    }

    public Position toDomain() {
        return new Position(
            id,
            symbol,
            entryPrice,
            entryDate,
            quantity,
            stopLoss,
            target,
            Position.PositionStatus.valueOf(status),
            entryReason,
            currentPrice
        );
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
