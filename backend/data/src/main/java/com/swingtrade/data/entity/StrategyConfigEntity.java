package com.swingtrade.data.entity;

import com.swingtrade.domain.PortfolioAction;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA entity for {@code strategy_config} (V46). See {@link StrategyConfig}'s Javadoc for the
 * append-only-versions contract this entity backs.
 *
 * <p>{@code lockVersion} is Hibernate's optimistic-lock column, deliberately named differently
 * from the {@code version} column (the business version number, part of the
 * {@code UNIQUE(variant_id, version)} key) to avoid the two concerns colliding on one column -
 * see V46's comment.
 */
@Entity
@Table(name = "strategy_config")
public class StrategyConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version")
    private Integer lockVersion = 0;

    @Column(name = "variant_id", nullable = false, length = 40)
    private String variantId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "strategy_type", nullable = false, length = 30)
    private String strategyType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false)
    private Map<String, Object> params = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "overlays", nullable = false)
    private Map<String, Object> overlays = new HashMap<>();

    @Column(name = "params_hash", nullable = false, length = 64)
    private String paramsHash;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode = StrategyMode.OFF.name();

    @Column(name = "paper_capital", nullable = false, precision = 15)
    private BigDecimal paperCapital = new BigDecimal("500000");

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = true;

    @Column(name = "portfolio_action", length = 16)
    private String portfolioAction;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public StrategyConfigEntity() {
    }

    public static StrategyConfigEntity fromDomain(StrategyConfig config) {
        StrategyConfigEntity entity = new StrategyConfigEntity();
        entity.id = config.id();
        entity.variantId = config.variantId();
        entity.version = config.version();
        entity.strategyType = config.strategyType();
        entity.params = new HashMap<>(config.params());
        entity.overlays = new HashMap<>(config.overlays());
        entity.paramsHash = config.paramsHash();
        entity.mode = config.mode().name();
        entity.paperCapital = config.paperCapital();
        entity.isCurrent = config.isCurrent();
        entity.portfolioAction = config.portfolioAction() == null ? null : config.portfolioAction().name();
        entity.notes = config.notes();
        entity.createdAt = config.createdAt();
        return entity;
    }

    public StrategyConfig toDomain() {
        return new StrategyConfig(
            id,
            variantId,
            version,
            strategyType,
            params,
            overlays,
            paramsHash,
            StrategyMode.valueOf(mode),
            paperCapital,
            Boolean.TRUE.equals(isCurrent),
            portfolioAction == null ? null : PortfolioAction.valueOf(portfolioAction),
            notes,
            createdAt
        );
    }

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

    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Map<String, Object> getOverlays() {
        return overlays;
    }

    public void setOverlays(Map<String, Object> overlays) {
        this.overlays = overlays;
    }

    public String getParamsHash() {
        return paramsHash;
    }

    public void setParamsHash(String paramsHash) {
        this.paramsHash = paramsHash;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public BigDecimal getPaperCapital() {
        return paperCapital;
    }

    public void setPaperCapital(BigDecimal paperCapital) {
        this.paperCapital = paperCapital;
    }

    public Boolean getIsCurrent() {
        return isCurrent;
    }

    public void setIsCurrent(Boolean isCurrent) {
        this.isCurrent = isCurrent;
    }

    public String getPortfolioAction() {
        return portfolioAction;
    }

    public void setPortfolioAction(String portfolioAction) {
        this.portfolioAction = portfolioAction;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
