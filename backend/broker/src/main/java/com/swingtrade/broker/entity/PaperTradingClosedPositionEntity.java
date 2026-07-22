package com.swingtrade.broker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_trading_closed_positions")
public class PaperTradingClosedPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position_id", length = 32)
    private String positionId;

    @Column(name = "symbol", length = 16)
    private String symbol;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column
    private Integer quantity;

    @Column(name = "entry_price", precision = 15, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 15, scale = 2)
    private BigDecimal exitPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal pnl;

    @Column(name = "realized_pnl", precision = 15, scale = 2)
    private BigDecimal realizedPnL;

    @Column(length = 16)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String entryReason;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @Column(name = "exit_reason", length = 64)
    private String exitReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PaperTradingClosedPositionEntity() {}

    public PaperTradingClosedPositionEntity(PaperTradingPositionEntity open) {
        this.positionId = open.getPositionId();
        this.symbol = open.getSymbol();
        this.direction = open.getDirection();
        this.quantity = open.getQuantity();
        this.entryPrice = open.getEntryPrice();
        this.exitPrice = open.getCurrentPrice();
        this.pnl = open.getPnl();
        this.realizedPnL = open.getRealizedPnL();
        this.status = open.getStatus();
        this.entryReason = open.getEntryReason();
        this.entryTime = open.getEntryTime();
        this.exitTime = open.getExitTime();
        this.exitReason = open.getExitReason();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPositionId() { return positionId; }
    public void setPositionId(String positionId) { this.positionId = positionId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
    public BigDecimal getExitPrice() { return exitPrice; }
    public void setExitPrice(BigDecimal exitPrice) { this.exitPrice = exitPrice; }
    public BigDecimal getPnl() { return pnl; }
    public void setPnl(BigDecimal pnl) { this.pnl = pnl; }
    public BigDecimal getRealizedPnL() { return realizedPnL; }
    public void setRealizedPnL(BigDecimal realizedPnL) { this.realizedPnL = realizedPnL; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEntryReason() { return entryReason; }
    public void setEntryReason(String entryReason) { this.entryReason = entryReason; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}