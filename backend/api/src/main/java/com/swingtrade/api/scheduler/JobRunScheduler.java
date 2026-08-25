package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.domain.JobRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Triggers the job orchestrator pipeline on a schedule.
 * Rejects if another run is already in progress.
 */
@Component
public class JobRunScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JobRunScheduler.class);

    private final JobOrchestratorService orchestratorService;
    private final JobRunRepository jobRunRepository;
    private final boolean schedulerEnabled;

    public JobRunScheduler(JobOrchestratorService orchestratorService,
                           JobRunRepository jobRunRepository,
                           @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.orchestratorService = orchestratorService;
        this.jobRunRepository = jobRunRepository;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
    public void runScheduledPipeline() {
        if (!schedulerEnabled) {
            logger.debug("Scheduler disabled (app.features.scheduler.enabled=false) — skipping scheduled pipeline run");
            return;
        }

        List<JobRunEntity> running = jobRunRepository
            .findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name());
        if (!running.isEmpty()) {
            logger.info("Skipping scheduled run: another run is in progress (runId={})",
                running.get(0).getRunId());
            return;
        }

        logger.info("Starting scheduled pipeline run");
        orchestratorService.startRun(JobRun.TriggerType.SCHEDULED);
    }
}