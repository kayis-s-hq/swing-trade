package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** JPA entity for {@code strategy_config_audit} (V46): records every mode change. */
@Entity
@Table(name = "strategy_config_audit")
public class StrategyConfigAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false, length = 40)
    private String variantId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "old_mode", length = 16)
    private String oldMode;

    @Column(name = "new_mode", nullable = false, length = 16)
    private String newMode;

    @Column(name = "changed_at")
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public StrategyConfigAuditEntity() {
    }

    public StrategyConfigAuditEntity(String variantId, int version, String oldMode, String newMode, String notes) {
        this.variantId = variantId;
        this.version = version;
        this.oldMode = oldMode;
        this.newMode = newMode;
        this.notes = notes;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getVariantId() {
        return variantId;
    }

    public Integer getVersion() {
        return version;
    }

    public String getOldMode() {
        return oldMode;
    }

    public String getNewMode() {
        return newMode;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getNotes() {
        return notes;
    }
}
