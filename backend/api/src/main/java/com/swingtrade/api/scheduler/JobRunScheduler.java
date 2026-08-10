package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.domain.JobRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public JobRunScheduler(JobOrchestratorService orchestratorService,
                           JobRunRepository jobRunRepository) {
        this.orchestratorService = orchestratorService;
        this.jobRunRepository = jobRunRepository;
    }

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
    public void runScheduledPipeline() {
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