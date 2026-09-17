package com.swingtrade.broker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persisted open/closed shadow-variant position state (plan §7.4 gap-fill), one row per BUY that
 * a SHADOW/CHAMPION variant's {@code executeVariantBuy} simulated. Carries what
 * {@link com.swingtrade.strategy.UniformExitEvaluator} needs to evaluate an exit for that
 * position on a later run: entry price/date, suggested stop/target, and the running
 * high-water-mark used for the optional trailing stop. This runs as part of a scheduled job
 * across process restarts, so this state is persisted rather than held in memory.
 *
 * <p>At most one {@code OPEN} row is expected per {@code (portfolio_id, symbol)} - a variant's
 * paper-trading capacity guard in {@code executeVariantBuy} means a second BUY for a symbol
 * already open should not normally occur, but callers must not rely on that; the exit query
 * always narrows to the single most-relevant open row.
 */
@Entity
@Table(name = "paper_shadow_positions")
public class ShadowPositionEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "portfolio_id", length = 32, nullable = false)
    private String portfolioId;

    @Column(name = "symbol", length = 16, nullable = false)
    private String symbol;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "entry_price", precision = 15, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", precision = 15, scale = 2)
    private BigDecimal stopLoss;

    @Column(name = "target", precision = 15, scale = 2)
    private BigDecimal target;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "high_water_mark", precision = 15, scale = 2)
    private BigDecimal highWaterMark;

    @Column(name = "status", length = 16, nullable = false)
    private String status = STATUS_OPEN;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "exit_price", precision = 15, scale = 2)
    private BigDecimal exitPrice;

    @Column(name = "exit_reason", length = 32)
    private String exitReason;

    @Column(name = "pnl", precision = 15, scale = 2)
    private BigDecimal pnl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ShadowPositionEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
    public BigDecimal getStopLoss() { return stopLoss; }
    public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
    public BigDecimal getTarget() { return target; }
    public void setTarget(BigDecimal target) { this.target = target; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getHighWaterMark() { return highWaterMark; }
    public void setHighWaterMark(BigDecimal highWaterMark) { this.highWaterMark = highWaterMark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getExitDate() { return exitDate; }
    public void setExitDate(LocalDate exitDate) { this.exitDate = exitDate; }
    public BigDecimal getExitPrice() { return exitPrice; }
    public void setExitPrice(BigDecimal exitPrice) { this.exitPrice = exitPrice; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public BigDecimal getPnl() { return pnl; }
    public void setPnl(BigDecimal pnl) { this.pnl = pnl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
