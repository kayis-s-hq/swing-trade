package com.swingtrade.api.dto;

import com.swingtrade.data.entity.JobRunStageEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

/**
 * One stage row. {@code details} is the structured details object (or {@code null}):
 * {@code {source?, reason?, warnings?, strategies?:[{variantId, version, outcome, reason?, score?, signal?}]}}.
 */
public record JobRunStageResponse(
    String symbol,
    String stageName,
    String status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Long durationMs,
    String errorMessage,
    String resultSummary,
    JsonNode details
) {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static JobRunStageResponse from(JobRunStageEntity entity) {
        return new JobRunStageResponse(
            entity.getSymbol(),
            entity.getStageName(),
            entity.getStatus(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getDurationMs(),
            entity.getErrorMessage(),
            entity.getResultSummary(),
            parse(entity.getDetails())
        );
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
