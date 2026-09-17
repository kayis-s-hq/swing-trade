package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.CandidateScanService;
import com.swingtrade.api.service.JobOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * Triggers the Candidate Explorer scan on a schedule, separate from the job
 * orchestrator's own scheduler. Runs shortly after market close, with a
 * 1-hour graceful gap before the orchestrator's 18:00 IST run.
 * Skips if another scan is already in progress.
 */
@Component
public class CandidateScanScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CandidateScanScheduler.class);

    private final CandidateScanService candidateScanService;
    private final boolean schedulerEnabled;
    private final boolean candidateScanSchedulerEnabled;

    @Autowired
    public CandidateScanScheduler(CandidateScanService candidateScanService,
                                  JobOrchestratorService orchestratorService,
                                  @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled,
                                  @Value("${app.features.candidate-scan.scheduler.enabled:true}") boolean candidateScanSchedulerEnabled) {
        this.candidateScanService = candidateScanService;
        this.schedulerEnabled = schedulerEnabled;
        this.candidateScanSchedulerEnabled = candidateScanSchedulerEnabled;
    }

    public CandidateScanScheduler(CandidateScanService candidateScanService,
                                  boolean schedulerEnabled,
                                  boolean candidateScanSchedulerEnabled) {
        this(candidateScanService, null, schedulerEnabled, candidateScanSchedulerEnabled);
    }

    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Kolkata")
    public void runScheduledScan() {
        if (!schedulerEnabled) {
            logger.debug("Scheduler disabled (app.features.scheduler.enabled=false) — skipping scheduled candidate scan");
            return;
        }
        if (!candidateScanSchedulerEnabled) {
            logger.debug("Candidate scan scheduler disabled (app.features.candidate-scan.scheduler.enabled=false) — skipping scheduled candidate scan");
            return;
        }

        logger.info("Starting scheduled candidate scan");
        try {
            candidateScanService.startScheduled();
        } catch (IllegalStateException e) {
            logger.info("Skipping scheduled candidate scan: a scan is already in progress ({})", e.getMessage());
        }
    }

}
