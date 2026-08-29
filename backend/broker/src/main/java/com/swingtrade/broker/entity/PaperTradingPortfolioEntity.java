package com.swingtrade.broker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "paper_trading_portfolio")
public class PaperTradingPortfolioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(name = "portfolio_id", length = 32)
    private String portfolioId;

    @Column(name = "initial_capital", precision = 15, scale = 2)
    private BigDecimal initialCapital;

    @Column(name = "current_capital", precision = 15, scale = 2)
    private BigDecimal currentCapital;

    @Column(name = "total_realized_pnl", precision = 15, scale = 2)
    private BigDecimal totalRealizedPnl;

    @Column(name = "total_unrealized_pnl", precision = 15, scale = 2)
    private BigDecimal totalUnrealizedPnL;

    @Column(name = "open_position_count")
    private int openPositionCount;

    public PaperTradingPortfolioEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }
    public BigDecimal getCurrentCapital() { return currentCapital; }
    public void setCurrentCapital(BigDecimal currentCapital) { this.currentCapital = currentCapital; }
    public BigDecimal getInitialCapital() { return initialCapital; }
    public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }
    public BigDecimal getTotalRealizedPnl() { return totalRealizedPnl; }
    public void setTotalRealizedPnl(BigDecimal totalRealizedPnl) { this.totalRealizedPnl = totalRealizedPnl; }
    public BigDecimal getTotalUnrealizedPnL() { return totalUnrealizedPnL; }
    public void setTotalUnrealizedPnL(BigDecimal totalUnrealizedPnL) { this.totalUnrealizedPnL = totalUnrealizedPnL; }
    public int getOpenPositionCount() { return openPositionCount; }
    public void setOpenPositionCount(int openPositionCount) { this.openPositionCount = openPositionCount; }
}