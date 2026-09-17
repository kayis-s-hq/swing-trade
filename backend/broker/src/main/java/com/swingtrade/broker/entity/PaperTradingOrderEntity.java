package com.swingtrade.broker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_trading_orders")
public class PaperTradingOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(name = "order_id", length = 32, unique = true)
    private String orderId;

    @Column(name = "symbol", length = 16)
    private String symbol;

    @Column(name = "order_type", length = 16)
    private String type;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column
    private Integer quantity;

    @Column(precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "limit_price", precision = 15, scale = 2)
    private BigDecimal limitPrice;

    @Column(name = "stop_price", precision = 15, scale = 2)
    private BigDecimal stopPrice;

    @Column(length = 16)
    private String status;

    @Column(precision = 15, scale = 4)
    private BigDecimal commission;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "signal_id", length = 64)
    private String signalId;

    // Maps the previously-unmapped paper_trading_orders.portfolio_id column (added by
    // V47__strategy_provenance.sql as VARCHAR(40), DB default 'default'). Lets every order
    // raised for a strategy variant's own execution be tagged with that variant's portfolio
    // id, so per-portfolio order/trade history can be queried independently (plan §7 Phase
    // 5). length must stay 40, matching the migration - a mismatch here makes Hibernate's
    // ddl-auto=update (dev/local profile) silently narrow the real DB column on startup.
    @Column(name = "portfolio_id", length = 40)
    private String portfolioId;

    public PaperTradingOrderEntity() {}

    public PaperTradingOrderEntity(com.swingtrade.domain.Order order) {
        this(order, "default");
    }

    public PaperTradingOrderEntity(com.swingtrade.domain.Order order, String portfolioId) {
        this.orderId = order.getOrderId();
        this.symbol = order.getSymbol();
        this.type = order.getType() != null ? order.getType().name() : null;
        this.direction = order.getDirection() != null ? order.getDirection().name() : null;
        this.quantity = order.getQuantity() != null ? order.getQuantity().intValue() : 0;
        this.price = order.getPrice();
        this.limitPrice = order.getLimitPrice();
        this.stopPrice = order.getStopPrice();
        this.status = order.getStatus() != null ? order.getStatus().name() : null;
        this.commission = order.getCommission();
        this.executedAt = order.getExecutionTime();
        this.createdAt = order.getTimestamp();
        this.updatedAt = LocalDateTime.now();
        this.signalId = order.getAdditionalProperties() != null
            ? String.valueOf(order.getAdditionalProperties().getOrDefault("signalId", "")) : null;
        this.portfolioId = portfolioId != null ? portfolioId : "default";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
    public BigDecimal getStopPrice() { return stopPrice; }
    public void setStopPrice(BigDecimal stopPrice) { this.stopPrice = stopPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getCommission() { return commission; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getSignalId() { return signalId; }
    public void setSignalId(String signalId) { this.signalId = signalId; }
    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }
}
