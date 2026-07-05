package com.swingtrade.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sentiment_accuracy", indexes = {
    @Index(name = "idx_sentiment_accuracy_symbol_date", columnList = "symbol, signal_date")
})
public class SentimentAccuracyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "signal_date", nullable = false)
    private java.time.LocalDate signalDate;

    @Column(name = "sentiment_score", nullable = false, length = 20)
    private String sentimentScore;

    @Column(name = "actual_outcome", nullable = false, length = 20)
    private String actualOutcome;

    @Column
    private Boolean wasCorrect;

    @Column(name = "pnl_pct", precision = 10, scale = 2)
    private java.math.BigDecimal pnlPct;

    @Column(name = "recorded_at")
    private java.time.LocalDateTime recordedAt;

    public SentimentAccuracyEntity() {
    }

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

    public java.time.LocalDate getSignalDate() {
        return signalDate;
    }

    public void setSignalDate(java.time.LocalDate signalDate) {
        this.signalDate = signalDate;
    }

    public String getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(String sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public String getActualOutcome() {
        return actualOutcome;
    }

    public void setActualOutcome(String actualOutcome) {
        this.actualOutcome = actualOutcome;
    }

    public Boolean getWasCorrect() {
        return wasCorrect;
    }

    public void setWasCorrect(Boolean wasCorrect) {
        this.wasCorrect = wasCorrect;
    }

    public java.math.BigDecimal getPnlPct() {
        return pnlPct;
    }

    public void setPnlPct(java.math.BigDecimal pnlPct) {
        this.pnlPct = pnlPct;
    }

    public java.time.LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(java.time.LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}