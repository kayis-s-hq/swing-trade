package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_audits")
public class ReconciliationAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20) private String symbol;
    @Column(name = "from_date", nullable = false) private LocalDate fromDate;
    @Column(name = "to_date", nullable = false) private LocalDate toDate;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "missing_count", nullable = false) private int missingCount;
    @Column(name = "inserted_count", nullable = false) private int insertedCount;
    @Column(name = "invalid_stored_removed_count", nullable = false) private int invalidStoredRemovedCount;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    protected ReconciliationAuditEntity() {}
    public ReconciliationAuditEntity(String symbol, LocalDate fromDate, LocalDate toDate, String status,
                                     int missingCount, int insertedCount, int invalidStoredRemovedCount) {
        this.symbol = symbol;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.status = status;
        this.missingCount = missingCount;
        this.insertedCount = insertedCount;
        this.invalidStoredRemovedCount = invalidStoredRemovedCount;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
