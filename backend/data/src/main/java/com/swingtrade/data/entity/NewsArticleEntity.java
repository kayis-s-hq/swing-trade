package com.swingtrade.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "news_articles", indexes = {
    @Index(name = "idx_news_articles_symbol", columnList = "symbol"),
    @Index(name = "idx_news_articles_source", columnList = "source"),
    @Index(name = "idx_news_articles_published", columnList = "published_at")
})
public class NewsArticleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String link;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "published_at")
    private java.time.OffsetDateTime publishedAt;

    @Column(columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "created_at")
    private java.time.OffsetDateTime createdAt;

    public NewsArticleEntity() {
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public java.time.OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(java.time.OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public java.time.OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}