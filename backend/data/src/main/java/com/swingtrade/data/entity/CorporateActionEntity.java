package com.swingtrade.data.entity;

import com.swingtrade.domain.CorporateAction;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "corporate_actions", uniqueConstraints = @UniqueConstraint(
    name = "uq_corporate_action_symbol_date_type", columnNames = {"symbol", "effective_date", "action_type"}))
public class CorporateActionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 20) private String symbol;
    @Column(name = "effective_date", nullable = false) private LocalDate effectiveDate;
    @Column(name = "action_type", nullable = false, length = 40) private String actionType;
    @Column(name = "adjustment_factor", precision = 20, scale = 8) private BigDecimal adjustmentFactor;
    @Column(name = "cash_amount", precision = 20, scale = 8) private BigDecimal cashAmount;
    @Column(nullable = false, length = 80) private String source;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;

    public CorporateActionEntity() {}
    public static CorporateActionEntity fromDomain(CorporateAction value) {
        CorporateActionEntity entity = new CorporateActionEntity();
        entity.symbol = value.symbol(); entity.effectiveDate = value.effectiveDate(); entity.actionType = value.actionType();
        entity.adjustmentFactor = value.adjustmentFactor(); entity.cashAmount = value.cashAmount();
        entity.source = value.source(); entity.recordedAt = value.recordedAt(); return entity;
    }
    public CorporateAction toDomain() { return new CorporateAction(symbol, effectiveDate, actionType, adjustmentFactor, cashAmount, source, recordedAt); }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getSymbol() { return symbol; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getActionType() { return actionType; }
    public BigDecimal getAdjustmentFactor() { return adjustmentFactor; }
    public BigDecimal getCashAmount() { return cashAmount; }
    public String getSource() { return source; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setSymbol(String value) { symbol = value; }
    public void setEffectiveDate(LocalDate value) { effectiveDate = value; }
    public void setActionType(String value) { actionType = value; }
    public void setAdjustmentFactor(BigDecimal value) { adjustmentFactor = value; }
    public void setCashAmount(BigDecimal value) { cashAmount = value; }
    public void setSource(String value) { source = value; }
    public void setRecordedAt(Instant value) { recordedAt = value; }
}
