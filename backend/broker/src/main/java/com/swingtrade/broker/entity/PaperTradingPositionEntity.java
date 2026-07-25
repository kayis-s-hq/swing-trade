package com.swingtrade.broker.entity;

import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_trading_positions")
public class PaperTradingPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position_id", length = 32, unique = true, nullable = false)
    private String positionId;

    @Column(name = "symbol", length = 16, nullable = false)
    private String symbol;

    @Column(name = "direction", length = 10)
    private String direction = "LONG";

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "entry_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "average_price", precision = 15, scale = 2)
    private BigDecimal averagePrice;

    @Column(name = "current_price", precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "stop_loss", precision = 15, scale = 2)
    private BigDecimal stopLoss;

    @Column(name = "target_price", precision = 15, scale = 2)
    private BigDecimal targetPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal pnl = BigDecimal.ZERO;

    @Column(name = "unrealized_pnl", precision = 15, scale = 2)
    private BigDecimal unrealizedPnL = BigDecimal.ZERO;

    @Column(name = "realized_pnl", precision = 15, scale = 2)
    private BigDecimal realizedPnL = BigDecimal.ZERO;

    @Column(length = 16)
    private String status = "OPEN";

    @Column(columnDefinition = "TEXT")
    private String entryReason;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @Column(name = "exit_reason", length = 64)
    private String exitReason;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public PaperTradingPositionEntity() {}

    public PaperTradingPositionEntity(Position pos) {
        this.positionId = pos.positionId();
        this.symbol = pos.symbol();
        this.direction = pos.direction() != null ? pos.direction().name() : "LONG";
        this.quantity = pos.quantity() != null ? pos.quantity() : 0;
        this.entryPrice = pos.entryPrice();
        this.averagePrice = pos.averagePrice();
        this.currentPrice = pos.currentPrice();
        this.stopLoss = pos.stopLoss();
        this.targetPrice = pos.target();
        this.pnl = pos.unrealizedPnL();
        this.unrealizedPnL = pos.unrealizedPnL();
        this.realizedPnL = pos.realizedPnL();
        this.status = pos.status() != null ? pos.status().name() : "OPEN";
        this.entryTime = pos.entryTime();
        this.exitTime = pos.exitTime();
        this.entryReason = pos.entryReason();
        this.exitReason = pos.exitReason();
        this.lastUpdated = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    private void setTimestamps() {
        this.lastUpdated = LocalDateTime.now();
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
    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public BigDecimal getStopLoss() { return stopLoss; }
    public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public void setTargetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; }
    public BigDecimal getPnl() { return pnl; }
    public void setPnl(BigDecimal pnl) { this.pnl = pnl; }
    public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
    public void setUnrealizedPnL(BigDecimal unrealizedPnL) { this.unrealizedPnL = unrealizedPnL; }
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
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public Position toDomain() {
        return Position.of(
            null,
            symbol,
            entryPrice,
            null, // entryDate not stored in paper trading table
            quantity,
            stopLoss,
            targetPrice,
            status != null ? PositionStatus.valueOf(status) : PositionStatus.OPEN,
            entryReason,
            currentPrice,
            positionId,
            null, // brokerPositionId not stored here
            null, // exchange not stored here
            direction != null ? TradeDirection.valueOf(direction) : TradeDirection.LONG,
            averagePrice,
            unrealizedPnL,
            realizedPnL,
            null, // marginUtilized not stored here
            entryTime,
            exitTime,
            exitReason,
            null  // orders not stored here
        );
    }
}