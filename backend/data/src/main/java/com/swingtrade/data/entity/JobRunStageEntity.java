package com.swingtrade.data.entity;

import com.swingtrade.domain.JobRunStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for job run stage tracking.
 */
@Entity
@Table(name = "job_run_stages", indexes = {
    @Index(name = "idx_job_run_stages_run", columnList = "run_id"),
    @Index(name = "idx_job_run_stages_symbol_stage", columnList = "symbol, stage_name"),
    @Index(name = "idx_job_run_stages_status", columnList = "status")
})
public class JobRunStageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    protected Integer version = 0;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "symbol", nullable = false, length = 16)
    private String symbol;

    @Column(name = "stage_name", nullable = false, length = 32)
    private String stageName;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "log_details", columnDefinition = "TEXT")
    private String logDetails;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    public JobRunStageEntity() {}

    public static JobRunStageEntity fromDomain(JobRunStage jrs) {
        JobRunStageEntity e = new JobRunStageEntity();
        e.setRunId(jrs.runId());
        e.setSymbol(jrs.symbol());
        e.setStageName(jrs.stageName().name());
        e.setStatus(jrs.status().name());
        e.setStartedAt(jrs.startedAt());
        e.setCompletedAt(jrs.completedAt());
        e.setDurationMs(jrs.durationMs());
        e.setErrorMessage(jrs.errorMessage());
        e.setLogDetails(jrs.logDetails());
        e.setResultSummary(jrs.resultSummary());
        return e;
    }

    public JobRunStage toDomain() {
        return new JobRunStage(
            runId,
            symbol,
            JobRunStage.StageName.valueOf(stageName),
            JobRunStage.Status.valueOf(status),
            startedAt,
            completedAt,
            durationMs,
            errorMessage,
            logDetails,
            resultSummary
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getLogDetails() { return logDetails; }
    public void setLogDetails(String logDetails) { this.logDetails = logDetails; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
}