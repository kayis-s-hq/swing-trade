package com.swingtrade.api.controller;

import com.swingtrade.api.dto.JobRunResponse;
import com.swingtrade.api.dto.JobRunStageResponse;
import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.domain.JobRun;
import com.swingtrade.data.entity.JobRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/job/runs")
public class JobRunController {

    private static final Logger logger = LoggerFactory.getLogger(JobRunController.class);

    private final JobOrchestratorService orchestratorService;

    public JobRunController(JobOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /**
     * POST /api/job/runs/start — Start a new run (manual trigger).
     */
    @PostMapping("/start")
    public ResponseEntity<JobRunResponse> startRun(
            @RequestParam(defaultValue = "MANUAL") String triggerType) {
        logger.info("Manual job run triggered (triggerType={})", triggerType);
        JobRun.TriggerType type = "SCHEDULED".equalsIgnoreCase(triggerType)
            ? JobRun.TriggerType.SCHEDULED
            : JobRun.TriggerType.MANUAL;

        JobRun run = orchestratorService.startRun(type);
        return ResponseEntity.ok(JobRunResponse.from(
            new JobRunEntity() {{
                setRunId(run.runId());
                setTriggerType(run.triggerType().name());
                setStatus(run.status().name());
                setStartedAt(run.startedAt());
                setCompletedAt(run.completedAt());
                setSymbolsCount(run.symbolsCount());
                setCompletedCount(run.completedCount());
                setFailedCount(run.failedCount());
                setErrorMessage(run.errorMessage());
            }}
        ));
    }

    /**
     * GET /api/job/runs/{runId}/progress — Polling endpoint for progress.
     */
    @GetMapping("/{runId}/progress")
    public ResponseEntity<?> getProgress(@PathVariable UUID runId) {
        JobOrchestratorService.JobRunProgress progress = orchestratorService.getProgress(runId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }

        List<JobRunStageResponse> stageResponses = progress.stages().stream()
            .map(JobRunStageResponse::from)
            .toList();

        return ResponseEntity.ok(Map.of(
            "runId", progress.runId().toString(),
            "status", progress.status(),
            "totalSymbols", progress.totalSymbols(),
            "completedSymbols", progress.completedSymbols(),
            "failedSymbols", progress.failedSymbols(),
            "stages", stageResponses
        ));
    }

    /**
     * GET /api/job/runs/{runId}/summary — Summary for a completed run.
     */
    @GetMapping("/{runId}/summary")
    public ResponseEntity<?> getSummary(@PathVariable UUID runId) {
        JobOrchestratorService.JobRunSummary summary = orchestratorService.getSummary(runId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "runId", summary.runId().toString(),
            "status", summary.status(),
            "totalSymbols", summary.totalSymbols(),
            "completedSymbols", summary.completedSymbols(),
            "failedSymbols", summary.failedSymbols(),
            "totalDurationMs", summary.totalDurationMs(),
            "stageStats", summary.stageStats(),
            "symbolDetails", summary.symbolDetails()
        ));
    }

    /**
     * GET /api/job/runs — List past runs.
     */
    @GetMapping
    public ResponseEntity<List<JobRunResponse>> listRuns() {
        List<JobRun> runs = orchestratorService.listRuns();
        List<JobRunResponse> responses = runs.stream()
            .map(r -> new JobRunResponse(
                r.runId(),
                r.triggerType().name(),
                r.status().name(),
                r.startedAt(),
                r.completedAt(),
                r.symbolsCount(),
                r.completedCount(),
                r.failedCount(),
                r.errorMessage()
            ))
            .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * POST /api/job/runs/{runId}/cancel — Cancel a running run.
     */
    @PostMapping("/{runId}/cancel")
    public ResponseEntity<?> cancelRun(@PathVariable UUID runId) {
        orchestratorService.cancelRun(runId);
        return ResponseEntity.ok(Map.of("message", "Run cancelled"));
    }
}