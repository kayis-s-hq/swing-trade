package com.swingtrade.broker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "paper_trading_portfolio")
public class PaperTradingPortfolioEntity {

    // Deliberately no @GeneratedValue: this table is a fixed singleton row and every
    // caller (PaperTradingStateService.loadPortfolio()/savePortfolio()) always sets id=1L
    // explicitly rather than letting Hibernate generate it. Combining a manually-assigned
    // id with GenerationType.IDENTITY made Hibernate's unsaved-value heuristic treat a
    // brand-new instance as a "detached" (already-persisted) entity as soon as
    // entity.setId(1L) ran, so persist() on the very first insert (empty table) failed with
    // "Detached entity with generated id '1' has an uninitialized version value".
    @Id
    private Long id;

    // Must stay null (no default initializer) for a transient instance: Spring Data's
    // isNew() check for @Version entities is "version == null" to decide persist() vs
    // merge(). A "= 0" default made every brand-new instance (e.g. the
    // .orElse(new PaperTradingPortfolioEntity()) fallback in
    // PaperTradingStateService.savePortfolio()) look already-persisted, so save() routed
    // it through merge() instead of persist() on its very first write, and Hibernate threw
    // StaleObjectStateException because no row with that id/version actually existed yet.
    @Version
    private Integer version;

    // Widened from 32 to 40 by V47__strategy_provenance.sql to match strategy_config's
    // variant_id length. Must stay in sync with that migration - a mismatched length here
    // makes Hibernate's ddl-auto=update (dev/local profile) silently narrow the real DB
    // column back to varchar(32) on every startup, undoing the migration.
    @Column(name = "portfolio_id", length = 40)
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

    // Kill switch / daily loss breaker (plan §7.2). Applied per portfolio for SHADOW variants
    // and, since a single process-wide "global" breaker concept doesn't cleanly exist yet given
    // there's only one live paper engine today, applied identically to the CHAMPION's own
    // ("default") portfolio row as its interpretation of "global" - see
    // PaperPortfolioServiceImpl.isDailyLossBreached().
    @Column(name = "daily_loss_threshold_pct", precision = 5, scale = 4)
    private BigDecimal dailyLossThresholdPct;

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
    public BigDecimal getDailyLossThresholdPct() { return dailyLossThresholdPct; }
    public void setDailyLossThresholdPct(BigDecimal dailyLossThresholdPct) { this.dailyLossThresholdPct = dailyLossThresholdPct; }
}