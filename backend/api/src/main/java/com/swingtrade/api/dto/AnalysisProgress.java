package com.swingtrade.api.dto;

import java.time.LocalDateTime;

public record AnalysisProgress(
    int stageNumber,
    String stageName,
    String status,    // "running" | "completed" | "skipped" | "error"
    String message,
    LocalDateTime timestamp
) {
    public static AnalysisProgress running(int stage, String name) {
        return new AnalysisProgress(stage, name, "running", "Starting...", LocalDateTime.now());
    }

    public static AnalysisProgress completed(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "completed", message, LocalDateTime.now());
    }

    public static AnalysisProgress skipped(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "skipped", message, LocalDateTime.now());
    }

    public static AnalysisProgress error(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "error", message, LocalDateTime.now());
    }
}