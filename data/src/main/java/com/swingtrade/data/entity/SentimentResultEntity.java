package com.swingtrade.data.entity;

import com.swingtrade.domain.SentimentResult;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * JPA entity for the SentimentResult domain model.
 */
@Entity
@Table(name = "sentiment_results", indexes = {
    @Index(name = "idx_sentiment_symbol_date", columnList = "symbol, date", unique = true)
})
public class SentimentResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "sentiment_score", nullable = false, length = 20)
    private String sentimentScore;  // POSITIVE, NEUTRAL, NEGATIVE

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "confidence", precision = 5, scale = 4)
    private Double confidence;

    @Column(name = "analyzed_at")
    private LocalDate analyzedAt;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    /**
     * Default constructor for JPA.
     */
    public SentimentResultEntity() {
    }

    /**
     * Factory method to create entity from domain model.
     *
     * @param result the domain SentimentResult
     * @return new SentimentResultEntity
     */
    public static SentimentResultEntity fromDomain(SentimentResult result) {
        SentimentResultEntity entity = new SentimentResultEntity();
        entity.setId(result.id());
        entity.setSymbol(result.symbol());
        entity.setDate(result.date());
        entity.setSentimentScore(result.score().name());
        entity.setSummary(result.summary());
        entity.setRawContent(result.rawContent());
        entity.setConfidence(result.confidence());
        entity.setAnalyzedAt(result.analyzedAt());
        return entity;
    }

    /**
     * Convert entity to domain model.
     *
     * @return domain SentimentResult
     */
    public SentimentResult toDomain() {
        return new SentimentResult(
            id,
            symbol,
            date,
            SentimentResult.SentimentScore.valueOf(sentimentScore),
            summary,
            rawContent,
            confidence,
            analyzedAt
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

    public String getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(String sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public LocalDate getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDate analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
