package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_loss_circuit_breaker_state")
public class DailyLossCircuitBreakerStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(name = "circuit_open")
    private boolean circuitOpen = false;

    @Column(name = "circuit_opened_at")
    private LocalDateTime circuitOpenedAt;

    @Column(name = "loss_at_open", precision = 15, scale = 2)
    private BigDecimal lossAtOpen;

    @Column(name = "last_reset_date")
    private LocalDate lastResetDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DailyLossCircuitBreakerStateEntity() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public boolean isCircuitOpen() { return circuitOpen; }
    public void setCircuitOpen(boolean circuitOpen) { this.circuitOpen = circuitOpen; }

    public LocalDateTime getCircuitOpenedAt() { return circuitOpenedAt; }
    public void setCircuitOpenedAt(LocalDateTime circuitOpenedAt) { this.circuitOpenedAt = circuitOpenedAt; }

    public BigDecimal getLossAtOpen() { return lossAtOpen; }
    public void setLossAtOpen(BigDecimal lossAtOpen) { this.lossAtOpen = lossAtOpen; }

    public LocalDate getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDate lastResetDate) { this.lastResetDate = lastResetDate; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
