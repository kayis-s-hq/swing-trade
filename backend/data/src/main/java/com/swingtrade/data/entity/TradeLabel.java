package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trade_labels")
public class TradeLabel {

    @Id
    private UUID id;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private PositionEntity position;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason", nullable = false)
    private ExitReason exitReason;

    @Column(name = "exit_confidence")
    private BigDecimal exitConfidence;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "labelled_by")
    private String labelledBy;

    @Column(name = "labelled_at")
    private LocalDateTime labelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ExitReason {
        STOP_LOSS,
        TARGET_HIT,
        TIME_STOP,
        TREND_BREAK,
        MANUAL
    }

    public TradeLabel() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public PositionEntity getPosition() {
        return position;
    }

    public void setPosition(PositionEntity position) {
        this.position = position;
    }

    public ExitReason getExitReason() {
        return exitReason;
    }

    public void setExitReason(ExitReason exitReason) {
        this.exitReason = exitReason;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getExitConfidence() {
        return exitConfidence;
    }

    public void setExitConfidence(BigDecimal exitConfidence) {
        this.exitConfidence = exitConfidence;
        this.updatedAt = LocalDateTime.now();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        this.updatedAt = LocalDateTime.now();
    }

    public String getLabelledBy() {
        return labelledBy;
    }

    public void setLabelledBy(String labelledBy) {
        this.labelledBy = labelledBy;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getLabelledAt() {
        return labelledAt;
    }

    public void setLabelledAt(LocalDateTime labelledAt) {
        this.labelledAt = labelledAt;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
