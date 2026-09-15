package com.swingtrade.data.entity;

import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionEntry;
import com.swingtrade.domain.PositionExit;
import com.swingtrade.domain.PositionRisk;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.PositionValuation;
import com.swingtrade.domain.TradeDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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

    @Version
    private Integer version = 0;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "broker_type", length = 10, nullable = false, columnDefinition = "VARCHAR(10) DEFAULT 'PAPER'")
    private String brokerType = "PAPER";

    // scale = 4, see the note on currentPrice below - same truncation bug applies here.
    @Column(name = "entry_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    private Integer quantity;

    @Column(name = "stop_loss", precision = 15)
    private BigDecimal stopLoss;

    @Column(precision = 15)
    private BigDecimal target;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String entryReason;

    // scale = 4 matches the actual DB column (NUMERIC(15,4) in
    // V1__swing_trade_schema.sql). Without an explicit scale, JPA's @Column
    // default (scale = 0) makes Hibernate round every write to this column to
    // a whole number - silently truncating a real exit price like 105.1572 to
    // 105 on save, regardless of the DB schema's actual precision.
    @Column(name = "current_price", precision = 15, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Broker-enriched fields
    @Column(name = "position_id", length = 32)
    private String positionId;

    @Column(name = "broker_position_id", length = 64)
    private String brokerPositionId;

    @Column(name = "exchange", length = 10)
    private String exchange;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "average_price", precision = 15, scale = 2)
    private BigDecimal averagePrice;

    @Column(name = "unrealized_pnl", precision = 15, scale = 2)
    private BigDecimal unrealizedPnL;

    @Column(name = "realized_pnl", precision = 15, scale = 2)
    private BigDecimal realizedPnL;

    @Column(name = "margin_utilized", precision = 15, scale = 2)
    private BigDecimal marginUtilized;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @Column(name = "exit_reason", length = 64)
    private String exitReason;

    public PositionEntity() {
    }

    public PositionEntity(Position position) {
        this.symbol = position.symbol();
        this.brokerType = position.brokerType() != null ? position.brokerType() : "PAPER";
        this.entryPrice = position.entryPrice();
        this.entryDate = position.entryDate();
        this.quantity = position.quantity();
        this.stopLoss = position.stopLoss();
        this.target = position.target();
        this.status = position.status().name();
        this.entryReason = position.entryReason();
        this.currentPrice = position.currentPrice();
        this.positionId = position.positionId();
        this.brokerPositionId = position.brokerPositionId();
        this.exchange = position.exchange() != null ? position.exchange().name() : null;
        this.direction = position.direction() != null ? position.direction().name() : null;
        this.averagePrice = position.averagePrice();
        this.unrealizedPnL = position.unrealizedPnL();
        this.realizedPnL = position.realizedPnL();
        this.marginUtilized = position.marginUtilized();
        this.entryTime = position.entryTime();
        this.exitTime = position.exitTime();
        this.exitReason = position.exitReason();
    }

    public static PositionEntity fromDomain(Position position) {
        PositionEntity entity = new PositionEntity();
        entity.setId(position.id());
        entity.setSymbol(position.symbol());
        entity.setBrokerType(position.brokerType() != null ? position.brokerType() : "PAPER");
        entity.setEntryPrice(position.entryPrice());
        entity.setEntryDate(position.entryDate());
        entity.setQuantity(position.quantity());
        entity.setStopLoss(position.stopLoss());
        entity.setTarget(position.target());
        entity.setStatus(position.status().name());
        entity.setEntryReason(position.entryReason());
        entity.setCurrentPrice(position.currentPrice());
        entity.setPositionId(position.positionId());
        entity.setBrokerPositionId(position.brokerPositionId());
        entity.setExchange(position.exchange() != null ? position.exchange().name() : null);
        entity.setDirection(position.direction() != null ? position.direction().name() : null);
        entity.setAveragePrice(position.averagePrice());
        entity.setUnrealizedPnL(position.unrealizedPnL());
        entity.setRealizedPnL(position.realizedPnL());
        entity.setMarginUtilized(position.marginUtilized());
        entity.setEntryTime(position.entryTime());
        entity.setExitTime(position.exitTime());
        entity.setExitReason(position.exitReason());
        return entity;
    }

    public Position toDomain() {
        return new Position(
            id,
            brokerType,
            PositionEntry.of(
                symbol,
                entryPrice,
                entryDate,
                quantity,
                entryTime,
                entryReason,
                positionId,
                brokerPositionId,
                exchange != null ? Exchange.fromCode(exchange) : null,
                direction != null ? TradeDirection.valueOf(direction) : TradeDirection.LONG,
                averagePrice
            ),
            new PositionRisk(stopLoss, target, marginUtilized),
            new PositionValuation(currentPrice, unrealizedPnL, realizedPnL),
            status != null ? PositionStatus.valueOf(status) : PositionStatus.OPEN,
            exitTime == null && exitReason == null ? null : new PositionExit(exitTime, exitReason),
            null
        );
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getBrokerType() { return brokerType; }
    public void setBrokerType(String brokerType) { this.brokerType = brokerType; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getStopLoss() { return stopLoss; }
    public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
    public BigDecimal getTarget() { return target; }
    public void setTarget(BigDecimal target) { this.target = target; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEntryReason() { return entryReason; }
    public void setEntryReason(String entryReason) { this.entryReason = entryReason; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getPositionId() { return positionId; }
    public void setPositionId(String positionId) { this.positionId = positionId; }
    public String getBrokerPositionId() { return brokerPositionId; }
    public void setBrokerPositionId(String brokerPositionId) { this.brokerPositionId = brokerPositionId; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
    public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
    public void setUnrealizedPnL(BigDecimal unrealizedPnL) { this.unrealizedPnL = unrealizedPnL; }
    public BigDecimal getRealizedPnL() { return realizedPnL; }
    public void setRealizedPnL(BigDecimal realizedPnL) { this.realizedPnL = realizedPnL; }
    public BigDecimal getMarginUtilized() { return marginUtilized; }
    public void setMarginUtilized(BigDecimal marginUtilized) { this.marginUtilized = marginUtilized; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
}
