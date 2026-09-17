package com.swingtrade.data.entity;

import com.swingtrade.domain.JobRun;
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
 * JPA entity for job orchestration runs.
 */
@Entity
@Table(name = "job_runs", indexes = {
    @Index(name = "idx_job_runs_status", columnList = "status"),
    @Index(name = "idx_job_runs_started_at", columnList = "started_at DESC")
})
public class JobRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(name = "run_id", nullable = false, unique = true)
    private UUID runId;

    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "symbols_count")
    private int symbolsCount;

    @Column(name = "completed_count")
    private int completedCount;

    @Column(name = "failed_count")
    private int failedCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "candidate_scan_run_id") private UUID candidateScanRunId;

    public JobRunEntity() {}

    public static JobRunEntity fromDomain(JobRun jr) {
        JobRunEntity e = new JobRunEntity();
        e.setRunId(jr.runId());
        e.setTriggerType(jr.triggerType().name());
        e.setStatus(jr.status().name());
        e.setStartedAt(jr.startedAt());
        e.setCompletedAt(jr.completedAt());
        e.setSymbolsCount(jr.symbolsCount());
        e.setCompletedCount(jr.completedCount());
        e.setFailedCount(jr.failedCount());
        e.setErrorMessage(jr.errorMessage());
        return e;
    }

    public JobRun toDomain() {
        return new JobRun(
            runId,
            JobRun.TriggerType.valueOf(triggerType),
            JobRun.Status.valueOf(status),
            startedAt,
            completedAt,
            symbolsCount,
            completedCount,
            failedCount,
            errorMessage
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public int getSymbolsCount() { return symbolsCount; }
    public void setSymbolsCount(int symbolsCount) { this.symbolsCount = symbolsCount; }
    public int getCompletedCount() { return completedCount; }
    public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public UUID getCandidateScanRunId() { return candidateScanRunId; }
    public void setCandidateScanRunId(UUID candidateScanRunId) { this.candidateScanRunId = candidateScanRunId; }
}
