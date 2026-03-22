package com.swingtrade.data.entity;

import com.swingtrade.domain.Signal;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the Signal domain model.
 */
@Entity
@Table(name = "signals", indexes = {
    @Index(name = "idx_signals_symbol_date", columnList = "symbol, date", unique = false),
    @Index(name = "idx_signals_date", columnList = "date", unique = false)
})
public class SignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "signal_type", nullable = false, length = 10)
    private String signalType;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "entry_price", precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", precision = 15, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "target", precision = 15, scale = 4)
    private BigDecimal target;

    @Column(name = "risk_reward", precision = 5, scale = 4)
    private BigDecimal riskReward;

    @Column(name = "indicators")
    private String indicators;

    @Column(name = "warning_flag", length = 50)
    private String warningFlag;

    @Column(name = "generated_at")
    private LocalDate generatedAt;

    // Warning flag constants
    public static final String WARNING_NONE = "";
    public static final String WARNING_NEUTRAL_SENTIMENT = "NEUTRAL_SENTIMENT";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SignalEntity() {
    }

    public SignalEntity(Signal signal) {
        this(signal, WARNING_NONE);
    }

    public SignalEntity(Signal signal, String warningFlag) {
        this.symbol = signal.symbol();
        this.date = signal.date();
        this.signalType = signal.type().name();
        this.confidenceScore = signal.confidence();
        this.reasoning = signal.reasoning();
        this.entryPrice = signal.entryPrice();
        this.stopLoss = signal.stopLoss();
        this.target = signal.target();
        this.riskReward = signal.riskReward();
        this.indicators = signal.indicators();
        this.generatedAt = signal.generatedAt();
        this.warningFlag = warningFlag;
    }

    public static SignalEntity fromDomain(Signal signal) {
        return fromDomain(signal, WARNING_NONE);
    }

    public static SignalEntity fromDomain(Signal signal, String warningFlag) {
        SignalEntity entity = new SignalEntity();
        entity.setSymbol(signal.symbol());
        entity.setDate(signal.date());
        entity.setSignalType(signal.type().name());
        entity.setConfidenceScore(signal.confidence());
        entity.setReasoning(signal.reasoning());
        entity.setEntryPrice(signal.entryPrice());
        entity.setStopLoss(signal.stopLoss());
        entity.setTarget(signal.target());
        entity.setRiskReward(signal.riskReward());
        entity.setIndicators(signal.indicators());
        entity.setGeneratedAt(signal.generatedAt());
        entity.setWarningFlag(warningFlag);
        return entity;
    }

    public Signal toDomain() {
        return new Signal(
            id,
            symbol,
            date,
            Signal.SignalType.valueOf(signalType),
            confidenceScore,
            reasoning,
            entryPrice,
            stopLoss,
            target,
            riskReward,
            indicators,
            generatedAt
        );
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
    }

    public BigDecimal getRiskReward() {
        return riskReward;
    }

    public void setRiskReward(BigDecimal riskReward) {
        this.riskReward = riskReward;
    }

    public String getIndicators() {
        return indicators;
    }

    public void setIndicators(String indicators) {
        this.indicators = indicators;
    }

    public String getWarningFlag() {
        return warningFlag;
    }

    public void setWarningFlag(String warningFlag) {
        this.warningFlag = warningFlag;
    }

    public LocalDate getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDate generatedAt) {
        this.generatedAt = generatedAt;
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
