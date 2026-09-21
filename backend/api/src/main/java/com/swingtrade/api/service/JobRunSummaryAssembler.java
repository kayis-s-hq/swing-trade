package com.swingtrade.api.service;

import com.swingtrade.api.service.JobOrchestratorService.DegradedStageBreakdown;
import com.swingtrade.api.service.JobOrchestratorService.JobRunSummary;
import com.swingtrade.api.service.JobOrchestratorService.SkippedStrategyBreakdown;
import com.swingtrade.api.service.JobOrchestratorService.StageStats;
import com.swingtrade.api.service.JobOrchestratorService.SymbolDetail;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.domain.JobRunStage;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds the {@code /summary} view of a run from its persisted stage rows. */
final class JobRunSummaryAssembler {

    private JobRunSummaryAssembler() {}

    static JobRunSummary assemble(JobRunEntity run, List<JobRunStageEntity> stages) {
        Map<String, StageStats> stageStats = new HashMap<>();
        for (var entry : stages.stream().collect(Collectors.groupingBy(JobRunStageEntity::getStageName)).entrySet()) {
            List<JobRunStageEntity> list = entry.getValue();
            long completed = list.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
            long errors = list.stream().filter(e -> "ERROR".equals(e.getStatus())).count();
            long degraded = list.stream().filter(e -> "DEGRADED".equals(e.getStatus())).count();
            long duration = list.stream().mapToLong(e -> e.getDurationMs() != null ? e.getDurationMs() : 0).sum();
            stageStats.put(entry.getKey(),
                new StageStats(list.size(), (int) completed, (int) errors, duration, (int) degraded));
        }

        List<SymbolDetail> symbolDetails = stages.stream()
            .collect(Collectors.groupingBy(JobRunStageEntity::getSymbol,
                Collectors.mapping(e -> e.getStageName() + ":" + e.getStatus(), Collectors.toList())))
            .entrySet().stream()
            .map(e -> new SymbolDetail(e.getKey(), e.getValue()))
            .toList();

        List<JobRunStageEntity> degradedRows = stages.stream()
            .filter(e -> JobRunStage.Status.DEGRADED.name().equals(e.getStatus())).toList();
        List<DegradedStageBreakdown> degradedBreakdown = degradedRows.stream()
            .collect(Collectors.groupingBy(
                e -> e.getStageName() + "|" + reasonOf(e), TreeMap::new, Collectors.counting()))
            .entrySet().stream()
            .map(e -> {
                String[] key = e.getKey().split("\\|", 2);
                return new DegradedStageBreakdown(key[0], key[1], e.getValue().intValue());
            }).toList();

        List<SkippedStrategyBreakdown> skipped = skippedStrategies(stages);
        int skippedCount = skipped.stream().mapToInt(SkippedStrategyBreakdown::symbols).sum();

        return new JobRunSummary(run.getRunId(), run.getStatus(), run.getSymbolsCount(),
            run.getCompletedCount(), run.getFailedCount(),
            stages.stream().mapToLong(e -> e.getDurationMs() != null ? e.getDurationMs() : 0).sum(),
            stageStats, symbolDetails, degradedRows.size(), skippedCount, degradedBreakdown, skipped);
    }

    private static String reasonOf(JobRunStageEntity stage) {
        String reason = StageDetails.reason(stage.getDetails());
        return reason == null ? "UNKNOWN" : reason;
    }

    /** One entry per (variant, reason) skipped/errored on the SIGNAL stage, with the symbol count. */
    private static List<SkippedStrategyBreakdown> skippedStrategies(List<JobRunStageEntity> stages) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JobRunStageEntity stage : stages) {
            if (!JobRunStage.StageName.SIGNAL.name().equals(stage.getStageName())) continue;
            for (Map<String, Object> row : StageDetails.strategies(stage.getDetails())) {
                Object outcome = row.get("outcome");
                if ("SKIPPED".equals(outcome) || "ERROR".equals(outcome)) {
                    String reason = row.get("reason") instanceof String r ? r : "unknown";
                    counts.merge(row.get("variantId") + "\u0000" + outcome + "\u0000" + reason, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream().map(e -> {
            String[] key = e.getKey().split("\u0000", 3);
            return new SkippedStrategyBreakdown(key[0], key[1], key[2], e.getValue());
        }).toList();
    }
}
