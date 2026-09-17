package com.swingtrade.llm.service;

import com.swingtrade.domain.store.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/** Measures persisted synthesis decisions after their configured forward window closes. */
@Component
public class SynthesisEvaluationJob {
    private static final Logger log = LoggerFactory.getLogger(SynthesisEvaluationJob.class);

    private final SynthesisEvaluationService evaluationService;
    private final CandleStore candleStore;
    private final int horizonDays;
    private final boolean schedulerEnabled;
    private final AtomicReference<LocalDateTime> lastRun = new AtomicReference<>();
    private final AtomicReference<String> lastStatus = new AtomicReference<>();
    private final AtomicReference<Integer> lastCount = new AtomicReference<>();

    public SynthesisEvaluationJob(SynthesisEvaluationService evaluationService, CandleStore candleStore,
                                  @Value("${app.synthesis.evaluation-horizon-days:5}") int horizonDays,
                                  @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.evaluationService = evaluationService;
        this.candleStore = candleStore;
        this.horizonDays = horizonDays;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 15 2 * * *", zone = "Asia/Kolkata")
    public void evaluatePendingSynthesis() {
        if (!schedulerEnabled) {
            log.debug("Scheduler disabled (app.features.scheduler.enabled=false) — skipping synthesis evaluation");
            return;
        }
        lastRun.set(LocalDateTime.now());
        try {
            int count = evaluationService.evaluatePending(candleStore, LocalDate.now(), horizonDays);
            lastCount.set(count);
            lastStatus.set("OK");
            log.info("Synthesis evaluation job complete: processed {} records", count);
        } catch (Exception e) {
            lastCount.set(0);
            lastStatus.set("FAILED: " + e.getMessage());
            log.error("Synthesis evaluation job failed", e);
        }
    }

    public LocalDateTime getLastRun() { return lastRun.get(); }
    public String getLastStatus() { return lastStatus.get(); }
    public Integer getLastCount() { return lastCount.get(); }
}
