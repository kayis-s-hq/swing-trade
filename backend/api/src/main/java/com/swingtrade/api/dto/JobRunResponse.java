package com.swingtrade.api.dto;

import com.swingtrade.data.entity.JobRunEntity;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobRunResponse(
    UUID runId,
    String triggerType,
    String status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    int symbolsCount,
    int completedCount,
    int failedCount,
    String errorMessage
) {
    public static JobRunResponse from(JobRunEntity entity) {
        return new JobRunResponse(
            entity.getRunId(),
            entity.getTriggerType(),
            entity.getStatus(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getSymbolsCount(),
            entity.getCompletedCount(),
            entity.getFailedCount(),
            entity.getErrorMessage()
        );
    }
}