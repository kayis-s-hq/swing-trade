package com.swingtrade.api.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public record AnalysisProgress(
    int stageNumber,
    String stageName,
    String status,
    String message,
    LocalDateTime timestamp,
    StageDetails details
) {
    public static AnalysisProgress running(int stage, String name) {
        return new AnalysisProgress(stage, name, "running", "Starting...", LocalDateTime.now(), null);
    }

    public static AnalysisProgress completed(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "completed",
            Objects.requireNonNullElse(message, "Completed."), LocalDateTime.now(), null);
    }

    public static AnalysisProgress completed(int stage, String name, String message, StageDetails details) {
        return new AnalysisProgress(stage, name, "completed",
            Objects.requireNonNullElse(message, "Completed."), LocalDateTime.now(), details);
    }

    public static AnalysisProgress skipped(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "skipped",
            Objects.requireNonNullElse(message, "Stage skipped."), LocalDateTime.now(), null);
    }

    public static AnalysisProgress error(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "error",
            Objects.requireNonNullElse(message, "An unexpected error occurred."), LocalDateTime.now(), null);
    }

    public record StageDetails(
        String type,
        Map<String, Object> payload
    ) {}
}
