package com.swingtrade.data.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for the Trade domain model.
 */
@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trades_position_id", columnList = "position_id", unique = false),
    @Index(name = "idx_trades_symbol", columnList = "symbol", unique = false),
    @Index(name = "idx_trades_entry_date", columnList = "entry_date", unique = false)
})
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "entry_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 15, scale = 4)
    private BigDecimal exitPrice;

    private Integer quantity;

    @Column(name = "total_pnl", precision = 15, scale = 2)
    private BigDecimal totalPnL;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "trade_status", nullable = false, length = 20)
    private String tradeStatus;

    @Column(columnDefinition = "TEXT")
    private String entryReason;

    @Column(name = "exit_reason", length = 50)
    private String exitReason;

    @Column(precision = 15, scale = 4)
    private BigDecimal fees;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Trade() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
        this.updatedAt = LocalDateTime.now();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDate getExitDate() {
        return exitDate;
    }

    public void setExitDate(LocalDate exitDate) {
        this.exitDate = exitDate;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getTotalPnL() {
        return totalPnL;
    }

    public void setTotalPnL(BigDecimal totalPnL) {
        this.totalPnL = totalPnL;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTradeStatus() {
        return tradeStatus;
    }

    public void setTradeStatus(String tradeStatus) {
        this.tradeStatus = tradeStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public String getStatus() {
        return tradeStatus;
    }

    public void setStatus(String status) {
        this.tradeStatus = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getEntryReason() {
        return entryReason;
    }

    public void setEntryReason(String entryReason) {
        this.entryReason = entryReason;
        this.updatedAt = LocalDateTime.now();
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getFees() {
        return fees;
    }

    public void setFees(BigDecimal fees) {
        this.fees = fees;
        this.updatedAt = LocalDateTime.now();
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
