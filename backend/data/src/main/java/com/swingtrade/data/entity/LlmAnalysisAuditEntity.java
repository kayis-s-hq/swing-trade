package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Immutable audit record for one LLM request/response attempt. */
@Entity
@Table(name = "llm_analysis_audit")
public class LlmAnalysisAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;
    @Column(nullable = false, length = 20)
    private String symbol;
    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;
    @Column(nullable = false, length = 32)
    private String provider;
    @Column(name = "model_version", length = 255)
    private String modelVersion;
    @Column(name = "prompt_hash", length = 64)
    private String promptHash;
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;
    @Column(name = "user_prompt", columnDefinition = "TEXT")
    private String userPrompt;
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;
    @Column(name = "parsed_score", length = 20)
    private String parsedScore;
    @Column(name = "parsed_confidence")
    private Double parsedConfidence;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;
    @Column(name = "max_tokens")
    private Integer maxTokens;
    private Double temperature;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    @Column(name = "latency_ms")
    private Long latencyMs;

    protected LlmAnalysisAuditEntity() { }

    public LlmAnalysisAuditEntity(String requestId, String symbol, LocalDate analysisDate,
                                  String provider, String modelVersion, String promptHash,
                                  String systemPrompt, String userPrompt, String rawResponse,
                                  String parsedScore, Double parsedConfidence, String status,
                                  String errorMessage, boolean fallbackUsed, Integer maxTokens,
                                  Double temperature, OffsetDateTime startedAt,
                                  OffsetDateTime completedAt, Long latencyMs) {
        this.requestId = requestId;
        this.symbol = symbol;
        this.analysisDate = analysisDate;
        this.provider = provider;
        this.modelVersion = modelVersion;
        this.promptHash = promptHash;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.rawResponse = rawResponse;
        this.parsedScore = parsedScore;
        this.parsedConfidence = parsedConfidence;
        this.status = status;
        this.errorMessage = errorMessage;
        this.fallbackUsed = fallbackUsed;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.latencyMs = latencyMs;
    }
}
