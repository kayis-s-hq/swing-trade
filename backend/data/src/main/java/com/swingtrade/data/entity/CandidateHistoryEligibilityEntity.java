package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Durable cooldown and source-quality record for symbols without usable history. */
@Entity
@Table(name = "candidate_history_eligibility")
public class CandidateHistoryEligibilityEntity {
    @Id @Column(length = 32) private String symbol;
    @Column(name = "candle_count", nullable = false) private int candleCount;
    @Column(name = "first_available_date") private LocalDate firstAvailableDate;
    @Column(name = "last_available_date") private LocalDate lastAvailableDate;
    @Column(name = "source_outcome", nullable = false, length = 32) private String sourceOutcome;
    @Column(name = "invalid_rows", nullable = false) private int invalidRows;
    @Column(name = "retry_after") private LocalDate retryAfter;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Version private Integer version = 0;
    public String getSymbol() { return symbol; }
    public void setSymbol(String v) { symbol = v; }
    public int getCandleCount() { return candleCount; }
    public void setCandleCount(int v) { candleCount = v; }
    public LocalDate getFirstAvailableDate() { return firstAvailableDate; }
    public void setFirstAvailableDate(LocalDate v) { firstAvailableDate = v; }
    public LocalDate getLastAvailableDate() { return lastAvailableDate; }
    public void setLastAvailableDate(LocalDate v) { lastAvailableDate = v; }
    public String getSourceOutcome() { return sourceOutcome; }
    public void setSourceOutcome(String v) { sourceOutcome = v; }
    public int getInvalidRows() { return invalidRows; }
    public void setInvalidRows(int v) { invalidRows = v; }
    public LocalDate getRetryAfter() { return retryAfter; }
    public void setRetryAfter(LocalDate v) { retryAfter = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
