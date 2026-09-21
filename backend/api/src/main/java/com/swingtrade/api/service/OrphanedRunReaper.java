package com.swingtrade.api.service;

import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.domain.JobRun;
import com.swingtrade.domain.JobRunStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Force-fails an orphaned RUNNING run and its RUNNING stages; extracted from the orchestrator. */
final class OrphanedRunReaper {

    private static final Logger logger = LoggerFactory.getLogger(OrphanedRunReaper.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JobRunRepository jobRunRepository;
    private final JobRunStageRepository jobRunStageRepository;
    private final JobOrchestratorMetrics jobMetrics;

    OrphanedRunReaper(JobRunRepository jobRunRepository, JobRunStageRepository jobRunStageRepository,
                      JobOrchestratorMetrics jobMetrics) {
        this.jobRunRepository = jobRunRepository;
        this.jobRunStageRepository = jobRunStageRepository;
        this.jobMetrics = jobMetrics;
    }

    void reap(JobRunEntity run, String reason) {
        logger.warn("Reaping orphaned run {}: startedAt={}, symbolsCount={}, reason={}",
            run.getRunId(), run.getStartedAt(), run.getSymbolsCount(), reason);

        run.setStatus(JobRun.Status.FAILED.name());
        run.setCompletedAt(LocalDateTime.now(IST));
        run.setErrorMessage(reason);
        jobRunRepository.save(run);
        jobMetrics.recordRunReaped();

        List<JobRunStageEntity> runningStages = jobRunStageRepository
            .findByRunIdOrderBySymbolAscStageNameAsc(run.getRunId()).stream()
            .filter(e -> JobRunStage.Status.RUNNING.name().equals(e.getStatus()))
            .toList();
        for (JobRunStageEntity stage : runningStages) {
            stage.setStatus(JobRunStage.Status.ERROR.name());
            stage.setCompletedAt(LocalDateTime.now(IST));
            stage.setErrorMessage("Reaped as orphaned — run was force-failed while this stage was RUNNING");
        }
        jobRunStageRepository.saveAll(runningStages);
    }
}
