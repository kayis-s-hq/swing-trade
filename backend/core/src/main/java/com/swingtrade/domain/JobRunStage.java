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
    // Declaration order drives the PENDING rows startRun() inserts up front (see
    // JobOrchestratorService), which the dashboard displays in insertion order - keep it in
    // sync with processSymbol()'s actual execution order. NEWS/SENTIMENT run after SIGNAL,
    // and only for a BUY signal, not every symbol every day: sentiment is a trade-execution
    // gate (checked by PAPER_TRADE), not a signal-generation input.
    public enum StageName { DATA_FETCH, SIGNAL, BACKTEST, NEWS, SENTIMENT, PAPER_TRADE }
    public enum Status { PENDING, RUNNING, COMPLETED, SKIPPED, ERROR, CANCELLED }
}