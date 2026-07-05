package com.swingtrade.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "news_items", indexes = {
    @Index(name = "idx_news_symbol_date", columnList = "symbol, published_at"),
    @Index(name = "idx_news_headline", columnList = "LOWER(headline)")
})
public class NewsItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String headline;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "published_at", nullable = false)
    private java.time.LocalDateTime publishedAt;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    public NewsItemEntity() {
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

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public java.time.LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(java.time.LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}