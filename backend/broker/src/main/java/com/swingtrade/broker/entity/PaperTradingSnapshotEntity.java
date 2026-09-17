package com.swingtrade.broker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_trading_portfolio_snapshots")
public class PaperTradingSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(name = "snapshot_time")
    private LocalDateTime snapshotTime;

    @Column(name = "total_value", precision = 15, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "cash_balance", precision = 15, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "market_value", precision = 15, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "total_pnl", precision = 15, scale = 2)
    private BigDecimal totalPnL;

    @Column(name = "return_pct", precision = 8, scale = 4)
    private BigDecimal returnPct;

    @Column(name = "open_positions")
    private int openPositions;

    // Added by V47; nothing populated it until PaperPortfolioServiceImpl.snapshotAllPortfolios()
    // (plan §7.2 / finding F8). Defaults to "default" for pre-existing rows and any snapshot
    // taken outside the per-portfolio loop.
    @Column(name = "portfolio_id", length = 40)
    private String portfolioId = "default";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PaperTradingSnapshotEntity() {}

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @PrePersist
    private void setTimestamps() {
        if (snapshotTime == null) snapshotTime = LocalDateTime.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(LocalDateTime snapshotTime) { this.snapshotTime = snapshotTime; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    public BigDecimal getTotalPnL() { return totalPnL; }
    public void setTotalPnL(BigDecimal totalPnL) { this.totalPnL = totalPnL; }
    public BigDecimal getReturnPct() { return returnPct; }
    public void setReturnPct(BigDecimal returnPct) { this.returnPct = returnPct; }
    public int getOpenPositions() { return openPositions; }
    public void setOpenPositions(int openPositions) { this.openPositions = openPositions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }
}