package com.swingtrade.data.entity;

import com.swingtrade.domain.SentimentResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.List;

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

    @Version
    private Integer version = 0;

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

    @Column(name = "confidence", precision = 5)
    private Double confidence;

    @Column(name = "analyzed_at")
    private LocalDate analyzedAt;

    @Column(name = "red_flags", columnDefinition = "TEXT[]")
    private String[] redFlags;

    @Column(name = "catalysts", columnDefinition = "TEXT[]")
    private String[] catalysts;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(name = "model_version", length = 255)
    private String modelVersion;

    @Column(name = "article_count")
    private int articleCount;

    @Column(name = "source", nullable = false, length = 16)
    private String source = "DEFAULT";

    @Column(name = "article_ids", columnDefinition = "BIGINT[]")
    private Long[] articleIds;

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
        entity.setRedFlags(result.redFlags() != null ? result.redFlags().toArray(new String[0]) : null);
        entity.setCatalysts(result.catalysts() != null ? result.catalysts().toArray(new String[0]) : null);
        entity.setPromptHash(result.promptHash());
        entity.setModelVersion(result.modelVersion());
        entity.setArticleCount(result.articleCount());
        entity.setSource(result.source());
        entity.setArticleIds(result.articleIds() == null ? null : result.articleIds().toArray(new Long[0]));
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
            analyzedAt,
            redFlags != null ? List.of(redFlags) : List.of(),
            catalysts != null ? List.of(catalysts) : List.of(),
            promptHash,
            modelVersion,
            articleCount,
            source,
            articleIds != null ? List.of(articleIds) : List.of()
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

    public String[] getRedFlags() {
        return redFlags;
    }

    public void setRedFlags(String[] redFlags) {
        this.redFlags = redFlags;
    }

    public String[] getCatalysts() {
        return catalysts;
    }

    public void setCatalysts(String[] catalysts) {
        this.catalysts = catalysts;
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

    public int getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(int articleCount) {
        this.articleCount = articleCount;
    }

    public String getSource() { return source; }

    public void setSource(String source) { this.source = source; }

    public Long[] getArticleIds() { return articleIds; }
    public void setArticleIds(Long[] articleIds) { this.articleIds = articleIds; }
}
