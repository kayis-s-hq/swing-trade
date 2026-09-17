package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.data.entity.CandidateScanRunEntity;
import com.swingtrade.data.repository.CandidateScanRunRepository;
import com.swingtrade.domain.JobRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Recovers scheduled scan handoffs after a process restart; no in-memory waiter is required. */
@Component
public class CandidateScanHandoffDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(CandidateScanHandoffDispatcher.class);
    private final CandidateScanRunRepository runs;
    private final JobOrchestratorService orchestrator;
    private final Object dispatchLock = new Object();
    public CandidateScanHandoffDispatcher(CandidateScanRunRepository runs, JobOrchestratorService orchestrator) {
        this.runs = runs; this.orchestrator = orchestrator;
    }
    @Scheduled(fixedDelayString = "${candidate-scan.handoff-dispatch-ms:60000}")
    public void dispatchPendingHandoffs() {
        synchronized (dispatchLock) {
            for (CandidateScanRunEntity scan : runs.findByStatusAndOrchestrationStatus("COMPLETED", "PENDING")) {
                if (scan.getQualifiedSymbols() == 0) { markNotRequired(scan); continue; }
                if (orchestrator.findActiveRun().isPresent()) return;
                try {
                    JobRun job = orchestrator.startRun(JobRun.TriggerType.SCHEDULED, scan.getRunId());
                    scan.setOrchestrationJobRunId(job.runId()); scan.setOrchestrationStatus("STARTED");
                    scan.setOrchestrationError(null); runs.save(scan);
                    logger.info("Started orchestration {} for candidate scan {}", job.runId(), scan.getRunId());
                } catch (JobOrchestratorService.ConcurrentRunException ignored) {
                    return;
                } catch (Exception e) {
                    scan.setOrchestrationStatus("FAILED"); scan.setOrchestrationError(e.getMessage()); runs.save(scan);
                    logger.warn("Candidate scan handoff {} failed: {}", scan.getRunId(), e.getMessage());
                }
            }
        }
    }
    @Transactional void markNotRequired(CandidateScanRunEntity scan) { scan.setOrchestrationStatus("NOT_REQUIRED"); runs.save(scan); }
}
