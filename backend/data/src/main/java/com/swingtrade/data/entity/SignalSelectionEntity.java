package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JPA entity for {@code signal_selection} (V52): the per-symbol/day signal tournament result. */
@Entity
@Table(name = "signal_selection")
public class SignalSelectionEntity {

    public static final String PENDING = "PENDING";
    public static final String EXECUTED = "EXECUTED";
    public static final String BLOCKED = "BLOCKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String symbol;

    @Column(name = "selection_date", nullable = false)
    private LocalDate selectionDate;

    @Column(name = "winner_variant_id", nullable = false, length = 40)
    private String winnerVariantId;

    @Column(name = "winner_version", nullable = false)
    private int winnerVersion;

    @Column(name = "winner_signal_id")
    private Long winnerSignalId;

    @Column(name = "winner_confidence", nullable = false, precision = 8, scale = 4)
    private BigDecimal winnerConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> candidates = new ArrayList<>();

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, length = 16)
    private String status = PENDING;

    @Column(name = "status_detail", length = 255)
    private String statusDetail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected SignalSelectionEntity() {}

    public SignalSelectionEntity(String symbol, LocalDate selectionDate, String winnerVariantId,
                                 int winnerVersion, Long winnerSignalId, BigDecimal winnerConfidence,
                                 List<Map<String, Object>> candidates, String reason) {
        this.symbol = symbol;
        this.selectionDate = selectionDate;
        this.winnerVariantId = winnerVariantId;
        this.winnerVersion = winnerVersion;
        this.winnerSignalId = winnerSignalId;
        this.winnerConfidence = winnerConfidence;
        this.candidates = candidates;
        this.reason = reason;
    }

    public void markStatus(String newStatus, String detail) {
        this.status = newStatus;
        this.statusDetail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 255));
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getSelectionDate() { return selectionDate; }
    public String getWinnerVariantId() { return winnerVariantId; }
    public int getWinnerVersion() { return winnerVersion; }
    public Long getWinnerSignalId() { return winnerSignalId; }
    public BigDecimal getWinnerConfidence() { return winnerConfidence; }
    public List<Map<String, Object>> getCandidates() { return candidates; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getStatusDetail() { return statusDetail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
