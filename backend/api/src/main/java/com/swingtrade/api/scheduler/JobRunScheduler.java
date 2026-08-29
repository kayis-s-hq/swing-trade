package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.domain.JobRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Triggers the job orchestrator pipeline on a schedule.
 * Rejects if another run is already in progress.
 */
@Component
public class JobRunScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JobRunScheduler.class);

    private final JobOrchestratorService orchestratorService;
    private final boolean schedulerEnabled;

    public JobRunScheduler(JobOrchestratorService orchestratorService,
                           @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.orchestratorService = orchestratorService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
    public void runScheduledPipeline() {
        if (!schedulerEnabled) {
            logger.debug("Scheduler disabled (app.features.scheduler.enabled=false) — skipping scheduled pipeline run");
            return;
        }

        Optional<JobRun> activeRun = orchestratorService.findActiveRun();
        if (activeRun.isPresent()) {
            logger.info("Skipping scheduled run: another run is in progress (runId={})",
                activeRun.get().runId());
            return;
        }

        logger.info("Starting scheduled pipeline run");
        try {
            // The pre-check above is a fast path only; startRun() itself is the
            // atomic guard against a run that started in the window between that
            // check and this call (e.g. a manual trigger firing concurrently).
            orchestratorService.startRun(JobRun.TriggerType.SCHEDULED);
        } catch (JobOrchestratorService.ConcurrentRunException e) {
            logger.info("Skipping scheduled run: {}", e.getMessage());
        }
    }
}
