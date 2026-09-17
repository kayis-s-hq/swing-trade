package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA entity for {@code strategy_backtest_results} (V48, plan §6.8): persisted output of
 * {@code POST /api/backtest/compare}, one row per (variant, version, fold). {@code fold=0} is a
 * plain (non-walk-forward) run; {@code fold=1..N} are walk-forward folds in chronological order.
 */
@Entity
@Table(name = "strategy_backtest_results")
public class StrategyBacktestResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false, length = 40)
    private String variantId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "fold", nullable = false)
    private Integer fold;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", nullable = false)
    private Map<String, Object> metrics = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equity_curve", nullable = false)
    private List<Map<String, Object>> equityCurve = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getVariantId() {
        return variantId;
    }

    public void setVariantId(String variantId) {
        this.variantId = variantId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getFold() {
        return fold;
    }

    public void setFold(Integer fold) {
        this.fold = fold;
    }

    public LocalDate getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDate windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDate getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDate windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    public List<Map<String, Object>> getEquityCurve() {
        return equityCurve;
    }

    public void setEquityCurve(List<Map<String, Object>> equityCurve) {
        this.equityCurve = equityCurve;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
