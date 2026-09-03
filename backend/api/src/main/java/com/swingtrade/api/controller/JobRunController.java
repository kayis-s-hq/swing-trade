package com.swingtrade.api.controller;

import com.swingtrade.api.dto.ErrorResponse;
import com.swingtrade.api.dto.JobRunResponse;
import com.swingtrade.api.dto.JobRunStageResponse;
import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.domain.JobRun;
import com.swingtrade.data.entity.JobRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * Returns 409 CONFLICT if a run is already in progress.
     */
    @PostMapping("/start")
    public ResponseEntity<?> startRun(
            @RequestParam(defaultValue = "MANUAL") String triggerType) {
        Optional<JobRun> activeRun = orchestratorService.findActiveRun();
        if (activeRun.isPresent()) {
            logger.info("Rejecting {} job run trigger — run {} is already in progress",
                triggerType, activeRun.get().runId());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.conflict(
                    "A job run is already in progress: " + activeRun.get().runId()));
        }

        logger.info("Manual job run triggered (triggerType={})", triggerType);
        JobRun.TriggerType type = "SCHEDULED".equalsIgnoreCase(triggerType)
            ? JobRun.TriggerType.SCHEDULED
            : JobRun.TriggerType.MANUAL;

        JobRun run;
        try {
            // The pre-check above is a fast path only; startRun() itself is the
            // atomic guard against a run that started in the window between that
            // check and this call (e.g. the scheduled cron firing concurrently).
            run = orchestratorService.startRun(type);
        } catch (JobOrchestratorService.ConcurrentRunException e) {
            logger.info("Rejecting {} job run trigger — {}", triggerType, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.conflict(e.getMessage()));
        }
        JobRunEntity entity = new JobRunEntity();
        entity.setRunId(run.runId());
        entity.setTriggerType(run.triggerType().name());
        entity.setStatus(run.status().name());
        entity.setStartedAt(run.startedAt());
        entity.setCompletedAt(run.completedAt());
        entity.setSymbolsCount(run.symbolsCount());
        entity.setCompletedCount(run.completedCount());
        entity.setFailedCount(run.failedCount());
        entity.setErrorMessage(run.errorMessage());
        return ResponseEntity.ok(JobRunResponse.from(entity));
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