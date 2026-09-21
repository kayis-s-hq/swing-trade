package com.swingtrade.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single orchestration run of the 6-stage job pipeline.
 */
public record JobRun(
    UUID runId,
    TriggerType triggerType,
    Status status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    int symbolsCount,
    int completedCount,
    int failedCount,
    String errorMessage
) {
    public enum TriggerType { MANUAL, SCHEDULED }
    public enum Status { RUNNING, COMPLETED, COMPLETED_WITH_WARNINGS, FAILED, CANCELLED }
}