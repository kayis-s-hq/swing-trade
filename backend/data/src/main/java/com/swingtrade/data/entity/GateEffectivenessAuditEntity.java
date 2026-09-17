package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Persisted verdict used to measure whether a signal gate adds value. */
@Entity
@Table(name = "gate_effectiveness_audit", uniqueConstraints = @UniqueConstraint(
    name = "uq_gate_effectiveness_symbol_date_gate",
    columnNames = {"symbol", "signal_date", "gate_name", "strategy"}))
public class GateEffectivenessAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    @Column(name = "gate_name", nullable = false, length = 40)
    private String gateName;

    @Column(nullable = false, length = 30)
    private String strategy = "DEFAULT";

    @Column(nullable = false, length = 24)
    private String verdict;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    protected GateEffectivenessAuditEntity() { }

    public GateEffectivenessAuditEntity(String symbol, LocalDate signalDate, String gateName,
                                        String verdict, String reason, OffsetDateTime recordedAt) {
        this(symbol, signalDate, gateName, verdict, reason, recordedAt, "DEFAULT");
    }

    public GateEffectivenessAuditEntity(String symbol, LocalDate signalDate, String gateName,
                                        String verdict, String reason, OffsetDateTime recordedAt,
                                        String strategy) {
        this.symbol = symbol;
        this.signalDate = signalDate;
        this.gateName = gateName;
        this.verdict = verdict;
        this.reason = reason;
        this.recordedAt = recordedAt;
        this.strategy = strategy == null || strategy.isBlank() ? "DEFAULT" : strategy;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getSignalDate() { return signalDate; }
    public String getGateName() { return gateName; }
    public String getStrategy() { return strategy; }
    public String getVerdict() { return verdict; }
    public String getReason() { return reason; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
}
