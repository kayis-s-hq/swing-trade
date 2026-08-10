package com.swingtrade.api.dto;

import com.swingtrade.data.entity.JobRunStageEntity;

import java.time.LocalDateTime;

public record JobRunStageResponse(
    String symbol,
    String stageName,
    String status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Long durationMs,
    String errorMessage,
    String resultSummary
) {
    public static JobRunStageResponse from(JobRunStageEntity entity) {
        return new JobRunStageResponse(
            entity.getSymbol(),
            entity.getStageName(),
            entity.getStatus(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getDurationMs(),
            entity.getErrorMessage(),
            entity.getResultSummary()
        );
    }
}