package com.swingtrade.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents one stage execution for a single symbol within a job run.
 */
public record JobRunStage(
    UUID runId,
    String symbol,
    StageName stageName,
    Status status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Long durationMs,
    String errorMessage,
    String logDetails,
    String resultSummary
) {
    public enum StageName { DATA_FETCH, NEWS, SENTIMENT, SIGNAL, BACKTEST, PAPER_TRADE }
    public enum Status { PENDING, RUNNING, COMPLETED, SKIPPED, ERROR }
}