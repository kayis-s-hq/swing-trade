package com.swingtrade.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pdf_extractions", indexes = {
    @Index(name = "idx_pdf_symbol_date", columnList = "symbol, extraction_date")
})
public class PdfExtractionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "extracted_json", nullable = false, columnDefinition = "JSONB")
    private String extractedJson;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "extraction_date", nullable = false)
    private java.time.LocalDate extractionDate;

    @Column(name = "model_used", length = 50)
    private String modelUsed;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    public PdfExtractionEntity() {
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

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getExtractedJson() {
        return extractedJson;
    }

    public void setExtractedJson(String extractedJson) {
        this.extractedJson = extractedJson;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public java.time.LocalDate getExtractionDate() {
        return extractionDate;
    }

    public void setExtractionDate(java.time.LocalDate extractionDate) {
        this.extractionDate = extractionDate;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}