package com.swingtrade.data.entity;

import com.swingtrade.domain.SentimentAccuracy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "sentiment_accuracy", indexes = {
    @Index(name = "idx_sentiment_accuracy_symbol_date", columnList = "symbol, analysis_date"),
    @Index(name = "idx_sentiment_accuracy_label", columnList = "ground_truth_label"),
    @Index(name = "idx_sentiment_accuracy_regime", columnList = "market_regime"),
    @Index(name = "idx_sentiment_accuracy_evaluated", columnList = "evaluated_at")
})
public class SentimentAccuracyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "analysis_date", nullable = false)
    private java.time.LocalDate analysisDate;

    @Column(name = "llm_score", nullable = false, length = 20)
    private String llmScore;

    @Column(name = "llm_confidence", nullable = false)
    private Float llmConfidence;

    @Column(name = "numeric_score", nullable = false)
    private Float numericScore;

    // Ground truth
    @Column(name = "actual_return_1d", precision = 10, scale = 6)
    private java.math.BigDecimal actualReturn1d;

    @Column(name = "actual_return_5d", precision = 10, scale = 6)
    private java.math.BigDecimal actualReturn5d;

    @Column(name = "actual_return_21d", precision = 10, scale = 6)
    private java.math.BigDecimal actualReturn21d;

    @Column(name = "ground_truth_label", length = 10)
    private String groundTruthLabel;

    @Column
    private Boolean wasCorrect;

    @Column(name = "pnl_pct", precision = 10, scale = 6)
    private java.math.BigDecimal pnlPct;

    // Context
    @Column(name = "market_regime", length = 10)
    private String marketRegime;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "composite_score")
    private Integer compositeScore;

    @Column(name = "composite_signal", length = 10)
    private String compositeSignal;

    @Column(name = "composite_id")
    private Long compositeId;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "evaluated_at")
    private java.time.LocalDateTime evaluatedAt;

    public SentimentAccuracyEntity() {
    }

    public SentimentAccuracyEntity(SentimentAccuracy accuracy) {
        this.id = accuracy.id();
        this.symbol = accuracy.symbol();
        this.analysisDate = accuracy.analysisDate();
        this.llmScore = accuracy.llmScore();
        this.llmConfidence = accuracy.llmConfidence();
        this.numericScore = accuracy.numericScore();
        this.actualReturn1d = accuracy.actualReturn1d();
        this.actualReturn5d = accuracy.actualReturn5d();
        this.actualReturn21d = accuracy.actualReturn21d();
        this.groundTruthLabel = accuracy.groundTruthLabel();
        this.wasCorrect = accuracy.wasCorrect();
        this.pnlPct = accuracy.pnlPct();
        this.marketRegime = accuracy.marketRegime();
        this.promptHash = accuracy.promptHash();
        this.modelVersion = accuracy.modelVersion();
        this.createdAt = java.time.LocalDateTime.now();
        this.evaluatedAt = accuracy.evaluatedAt();
    }

    public static SentimentAccuracyEntity fromDomain(SentimentAccuracy accuracy) {
        return new SentimentAccuracyEntity(accuracy);
    }

    public SentimentAccuracy toDomain() {
        return new SentimentAccuracy(
            id,
            symbol,
            analysisDate,
            llmScore,
            llmConfidence,
            numericScore,
            actualReturn1d,
            actualReturn5d,
            actualReturn21d,
            groundTruthLabel,
            wasCorrect,
            pnlPct,
            marketRegime,
            promptHash,
            modelVersion,
            evaluatedAt
        );
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

    public java.time.LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(java.time.LocalDate analysisDate) {
        this.analysisDate = analysisDate;
    }

    public String getLlmScore() {
        return llmScore;
    }

    public void setLlmScore(String llmScore) {
        this.llmScore = llmScore;
    }

    public Float getLlmConfidence() {
        return llmConfidence;
    }

    public void setLlmConfidence(Float llmConfidence) {
        this.llmConfidence = llmConfidence;
    }

    public Float getNumericScore() {
        return numericScore;
    }

    public void setNumericScore(Float numericScore) {
        this.numericScore = numericScore;
    }

    public java.math.BigDecimal getActualReturn1d() {
        return actualReturn1d;
    }

    public void setActualReturn1d(java.math.BigDecimal actualReturn1d) {
        this.actualReturn1d = actualReturn1d;
    }

    public java.math.BigDecimal getActualReturn5d() {
        return actualReturn5d;
    }

    public void setActualReturn5d(java.math.BigDecimal actualReturn5d) {
        this.actualReturn5d = actualReturn5d;
    }

    public java.math.BigDecimal getActualReturn21d() {
        return actualReturn21d;
    }

    public void setActualReturn21d(java.math.BigDecimal actualReturn21d) {
        this.actualReturn21d = actualReturn21d;
    }

    public String getGroundTruthLabel() {
        return groundTruthLabel;
    }

    public void setGroundTruthLabel(String groundTruthLabel) {
        this.groundTruthLabel = groundTruthLabel;
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

    public String getMarketRegime() {
        return marketRegime;
    }

    public void setMarketRegime(String marketRegime) {
        this.marketRegime = marketRegime;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public void setPromptHash(String promptHash) {
        this.promptHash = promptHash;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Integer getCompositeScore() {
        return compositeScore;
    }

    public void setCompositeScore(Integer compositeScore) {
        this.compositeScore = compositeScore;
    }

    public String getCompositeSignal() {
        return compositeSignal;
    }

    public void setCompositeSignal(String compositeSignal) {
        this.compositeSignal = compositeSignal;
    }

    public Long getCompositeId() {
        return compositeId;
    }

    public void setCompositeId(Long compositeId) {
        this.compositeId = compositeId;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(java.time.LocalDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}