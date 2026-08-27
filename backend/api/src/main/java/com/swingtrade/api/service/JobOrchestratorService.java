package com.swingtrade.api.service;

import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.JobRun;
import com.swingtrade.domain.JobRunStage;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.WatchlistStore;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates the 6-stage job pipeline across all watchlist symbols
 * with concurrency control and per-stage progress tracking.
 */
@Service
public class JobOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(JobOrchestratorService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Per-stage timeouts (seconds)
    private static final long TIMEOUT_DATA_FETCH = 30L;
    private static final long TIMEOUT_NEWS = 30L;
    // Matches SentimentService.ANALYSIS_TIMEOUT_SECONDS: CPU-only local LLM generation
    // can legitimately run for minutes, so the outer stage must not cut the inner wait short.
    private static final long TIMEOUT_SENTIMENT = 600L;
    private static final long TIMEOUT_SIGNAL = 30L;
    private static final long TIMEOUT_BACKTEST = 120L;
    private static final long TIMEOUT_PAPER_TRADE = 30L;

    /** Worst-case wall-clock seconds a single symbol can spend across all six stages. */
    private static final long STAGE_TIMEOUT_SUM_SECONDS =
        TIMEOUT_DATA_FETCH + TIMEOUT_NEWS + TIMEOUT_SENTIMENT
            + TIMEOUT_SIGNAL + TIMEOUT_BACKTEST + TIMEOUT_PAPER_TRADE;

    /** Generous margin over the worst case before a RUNNING row is considered orphaned. */
    private static final long STALE_RUN_SAFETY_MULTIPLIER = 6L;

    private final long pollIntervalMs;
    private final int maxConcurrent;
    private final boolean reaperEnabled;
    private final Semaphore semaphore;
    private final ExecutorService asyncExecutor;

    /**
     * Currently in-flight stage executions, keyed by "{runId}::{symbol}" — lets cancelRun()
     * interrupt the actual blocking work (e.g. the SENTIMENT stage's Ollama/vLLM HTTP call),
     * not just flip DB status. Only one stage runs at a time per symbol since stages execute
     * sequentially within processSymbol().
     */
    private final ConcurrentHashMap<String, Future<StageExecutionResult>> inFlightStageFutures =
        new ConcurrentHashMap<>();

    /**
     * Run IDs cancelled via cancelRun() whose background symbol-processing threads may still be
     * unwinding. Consulted by processSymbol()'s stage loop (stop starting new stages once set)
     * and by startRun()'s completion callback (don't overwrite CANCELLED back to
     * COMPLETED/FAILED once those threads finish). Entries are removed once the run's futures
     * have all completed.
     */
    private final Set<UUID> cancelledRunIds = ConcurrentHashMap.newKeySet();

    private final DataIngestionService dataIngestionService;
    private final NewsIngestionService newsIngestionService;
    private final SentimentService sentimentService;
    private final SignalPipeline signalPipeline;
    private final BacktestEngine backtestEngine;
    private final TradingService tradingService;
    private final JobRunRepository jobRunRepository;
    private final JobRunStageRepository jobRunStageRepository;
    private final SignalStore signalStore;
    private final WatchlistStore watchlistStore;
    private final CandleStore candleStore;
    private final JobOrchestratorMetrics jobMetrics;

    public JobOrchestratorService(
            DataIngestionService dataIngestionService,
            NewsIngestionService newsIngestionService,
            SentimentService sentimentService,
            SignalPipeline signalPipeline,
            BacktestEngine backtestEngine,
            TradingService tradingService,
            JobRunRepository jobRunRepository,
            JobRunStageRepository jobRunStageRepository,
            SignalStore signalStore,
            WatchlistStore watchlistStore,
            CandleStore candleStore,
            JobOrchestratorMetrics jobMetrics,
            @Value("${job.orchestrator.max-concurrent:3}") int maxConcurrent,
            @Value("${job.orchestrator.poll-interval-ms:1000}") long pollIntervalMs,
            @Value("${job.orchestrator.reaper.enabled:true}") boolean reaperEnabled) {
        this.pollIntervalMs = pollIntervalMs;
        this.maxConcurrent = maxConcurrent;
        this.reaperEnabled = reaperEnabled;
        this.semaphore = new Semaphore(maxConcurrent);
        this.asyncExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("job-orchestrator-", 0).factory());
        this.dataIngestionService = dataIngestionService;
        this.newsIngestionService = newsIngestionService;
        this.sentimentService = sentimentService;
        this.signalPipeline = signalPipeline;
        this.backtestEngine = backtestEngine;
        this.tradingService = tradingService;
        this.jobRunRepository = jobRunRepository;
        this.jobRunStageRepository = jobRunStageRepository;
        this.signalStore = signalStore;
        this.watchlistStore = watchlistStore;
        this.candleStore = candleStore;
        this.jobMetrics = jobMetrics;
    }

    // ---- DTOs ----

    public record JobRunProgress(
        UUID runId,
        String status,
        int totalSymbols,
        int completedSymbols,
        int failedSymbols,
        List<JobRunStageEntity> stages
    ) {}

    public record JobRunSummary(
        UUID runId,
        String status,
        int totalSymbols,
        int completedSymbols,
        int failedSymbols,
        long totalDurationMs,
        java.util.Map<String, StageStats> stageStats,
        List<SymbolDetail> symbolDetails
    ) {}

    public record StageStats(
        int total,
        int completed,
        int errors,
        long totalDurationMs
    ) {}

    public record SymbolDetail(
        String symbol,
        List<String> stageStatuses
    ) {}

    /**
     * Starts a new pipeline run for all active watchlist symbols.
     * Returns immediately with the run record; processing is async.
     */
    public JobRun startRun(JobRun.TriggerType triggerType) {
        LocalDate today = LocalDate.now(IST);

        JobRun run = new JobRun(
            UUID.randomUUID(),
            triggerType,
            JobRun.Status.RUNNING,
            java.time.LocalDateTime.now(IST),
            null, 0, 0, 0, null
        );
        jobRunRepository.save(JobRunEntity.fromDomain(run));
        jobMetrics.recordRunStarted();

        List<String> symbols = watchlistStore.getActiveWatchlistSymbols();
        if (symbols.isEmpty()) {
            logger.info("No watchlist symbols — completing run with zero symbols");
            completeRun(run.runId(), JobRun.Status.COMPLETED, null);
            return jobRunRepository.findByRunId(run.runId())
                .orElseThrow()
                .toDomain();
        }

        var entity2 = jobRunRepository.findByRunId(run.runId()).orElseThrow();
        entity2.setSymbolsCount(symbols.size());
        jobRunRepository.save(entity2);
        run = new JobRun(
            run.runId(), run.triggerType(), run.status(), run.startedAt(), run.completedAt(),
            symbols.size(), run.completedCount(), run.failedCount(), run.errorMessage()
        );
        final JobRun finalRun = run;

        // Initialize stage rows for all symbols
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (String symbol : symbols) {
            for (JobRunStage.StageName stage : JobRunStage.StageName.values()) {
                JobRunStage stageRow = new JobRunStage(
                    run.runId(), symbol, stage,
                    JobRunStage.Status.PENDING, now, null, null, null, null, null
                );
                jobRunStageRepository.save(JobRunStageEntity.fromDomain(stageRow));
            }
        }

        // Process symbols in parallel with semaphore throttle
        List<CompletableFuture<Void>> futures = symbols.stream().map(symbol ->
            CompletableFuture.runAsync(() -> processSymbol(finalRun.runId(), symbol, today), asyncExecutor)
                .exceptionally(ex -> {
                    logUnattributedPipelineError(symbol, ex);
                    return null;
                })
        ).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((v, ex) -> {
                try {
                    if (cancelledRunIds.remove(finalRun.runId())) {
                        logger.info("Run {} finished after cancellation — leaving status as CANCELLED",
                            finalRun.runId());
                        return;
                    }
                    if (ex != null) {
                        completeRun(finalRun.runId(), JobRun.Status.FAILED, ex.getMessage());
                        return;
                    }
                    // Count distinct symbols that recorded at least one ERROR stage.
                    // Filter on stage STATUS, not stageName — "ERROR" is a JobRunStage.Status
                    // value and never a valid StageName, so the old
                    // findByRunIdAndStageName(runId, "ERROR") lookup could never match.
                    List<String> symbolsWithErrors = jobRunStageRepository
                        .findByRunIdOrderBySymbolAscStageNameAsc(finalRun.runId()).stream()
                        .filter(e -> JobRunStage.Status.ERROR.name().equals(e.getStatus()))
                        .map(JobRunStageEntity::getSymbol)
                        .distinct()
                        .toList();
                    if (!symbolsWithErrors.isEmpty() && symbolsWithErrors.size() > symbols.size() / 2) {
                        completeRun(finalRun.runId(), JobRun.Status.FAILED,
                            symbolsWithErrors.size() + " symbols failed");
                    } else {
                        completeRun(finalRun.runId(), JobRun.Status.COMPLETED, null);
                    }
                } catch (Exception completionEx) {
                    // Never let a failure here vanish silently and leave the JobRun row
                    // stuck at RUNNING forever.
                    logger.error("Run {} completion callback failed — forcing run to FAILED "
                        + "to avoid a stuck RUNNING row: {}",
                        finalRun.runId(), completionEx.getMessage(), completionEx);
                    forceRunFailedSafely(finalRun.runId(),
                        "Run finalization failed: " + completionEx.getMessage());
                }
            });

        return run;
    }

    /** Ordered stage definition used to drive processSymbol and its failure-gating. */
    private record StageDef(JobRunStage.StageName name, StageExecutor executor, long timeoutSec) {}

    /** A stage can complete normally or skip without being treated as an operational error. */
    private record StageExecutionResult(JobRunStage.Status status, String summary) {
        private static StageExecutionResult completed(String summary) {
            return new StageExecutionResult(JobRunStage.Status.COMPLETED, summary);
        }

        private static StageExecutionResult skipped(String summary) {
            return new StageExecutionResult(JobRunStage.Status.SKIPPED, summary);
        }
    }

    private void processSymbol(UUID runId, String symbol, LocalDate today) {
        JobRunStage.StageName[] currentStage = {JobRunStage.StageName.DATA_FETCH};
        try {
            acquireSlot(symbol);
            try {
                List<StageDef> stageDefs = List.of(
                    new StageDef(JobRunStage.StageName.DATA_FETCH,
                        () -> StageExecutionResult.completed(stageDataFetch(symbol)), TIMEOUT_DATA_FETCH),
                    new StageDef(JobRunStage.StageName.NEWS,
                        () -> StageExecutionResult.completed(stageNews(symbol)), TIMEOUT_NEWS),
                    new StageDef(JobRunStage.StageName.SENTIMENT,
                        () -> StageExecutionResult.completed(stageSentiment(symbol, today)), TIMEOUT_SENTIMENT),
                    new StageDef(JobRunStage.StageName.SIGNAL,
                        () -> StageExecutionResult.completed(stageSignal(symbol)), TIMEOUT_SIGNAL),
                    new StageDef(JobRunStage.StageName.BACKTEST, () -> stageBacktest(symbol), TIMEOUT_BACKTEST),
                    new StageDef(JobRunStage.StageName.PAPER_TRADE,
                        () -> StageExecutionResult.completed(stagePaperTrade(symbol)), TIMEOUT_PAPER_TRADE)
                );

                boolean priorStageBlocked = false;
                for (StageDef stageDef : stageDefs) {
                    currentStage[0] = stageDef.name();
                    if (cancelledRunIds.contains(runId)) {
                        updateStageStatus(runId, symbol, stageDef.name(), JobRunStage.Status.CANCELLED,
                            null, "Run cancelled by user request", null);
                        logger.debug("Skipping stage {} for {}: run was cancelled", stageDef.name(), symbol);
                        continue;
                    }
                    if (priorStageBlocked) {
                        String reason = "Skipped — an earlier stage did not complete";
                        updateStageStatus(runId, symbol, stageDef.name(), JobRunStage.Status.SKIPPED,
                            null, null, reason);
                        logger.debug("Skipping stage {} for {}: earlier stage did not complete",
                            stageDef.name(), symbol);
                        continue;
                    }
                    boolean succeeded = executeStage(runId, symbol, stageDef.name(),
                        stageDef.executor(), stageDef.timeoutSec());
                    if (!succeeded) {
                        priorStageBlocked = true;
                    }
                }

                recordCompletion(runId, symbol);
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError(runId, symbol, currentStage[0], "Pipeline interrupted", e);
        } catch (Exception e) {
            logError(runId, symbol, currentStage[0], "Pipeline failed", e);
        }
    }

    /**
     * Acquires a processing slot, polling every {@code pollIntervalMs} instead of
     * blocking indefinitely so waits are observable and the poll cadence is configurable
     * via {@code job.orchestrator.poll-interval-ms}.
     */
    private void acquireSlot(String symbol) throws InterruptedException {
        while (!semaphore.tryAcquire(pollIntervalMs, TimeUnit.MILLISECONDS)) {
            logger.debug("Waiting for a processing slot for {} (poll interval {}ms)", symbol, pollIntervalMs);
        }
    }

    private static String inFlightKey(UUID runId, String symbol) {
        return runId + "::" + symbol;
    }

    @FunctionalInterface
    private interface StageExecutor {
        StageExecutionResult execute() throws Exception;
    }

    /**
     * Executes a single stage and records its outcome.
     *
     * @return true if the stage completed successfully, false if it errored, timed out, or skipped
     */
    private boolean executeStage(UUID runId, String symbol, JobRunStage.StageName stage,
                              StageExecutor executor, long timeoutSec) {
        String key = inFlightKey(runId, symbol);
        long start = System.currentTimeMillis();

        Future<StageExecutionResult> future = asyncExecutor.submit(() -> {
            try {
                return executor.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        // Register BEFORE marking RUNNING in the DB, so that by the time cancelRun() (or any
        // external observer polling /progress) can see this stage as RUNNING, the future is
        // already reachable to interrupt — otherwise there is a narrow window where a cancel
        // request could see "RUNNING" in the DB but find nothing to interrupt.
        inFlightStageFutures.put(key, future);
        updateStageStatus(runId, symbol, stage, JobRunStage.Status.RUNNING, null, null, null);

        try {
            StageExecutionResult result = future.get(timeoutSec, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;
            updateStageStatusTolerantly(runId, symbol, stage, result.status(),
                duration, null, result.summary());
            logger.debug("Stage {} finished with status {} for {} in {}ms",
                stage, result.status(), symbol, duration);
            return result.status() == JobRunStage.Status.COMPLETED;
        } catch (CancellationException e) {
            long duration = System.currentTimeMillis() - start;
            String msg = "Cancelled by user request";
            updateStageStatusTolerantly(runId, symbol, stage, JobRunStage.Status.CANCELLED,
                duration, msg, null);
            logger.info("Stage {} cancelled for {}", stage, symbol);
            return false;
        } catch (TimeoutException e) {
            future.cancel(true);
            long duration = System.currentTimeMillis() - start;
            String msg = "Stage timed out after " + timeoutSec + "s";
            updateStageStatusTolerantly(runId, symbol, stage, JobRunStage.Status.ERROR,
                duration, msg, null);
            logger.warn("{} for {}", msg, symbol);
            return false;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            updateStageStatusTolerantly(runId, symbol, stage, JobRunStage.Status.ERROR,
                duration, e.getMessage(), null);
            logger.warn("Stage {} failed for {}: {}", stage, symbol, e.getMessage());
            return false;
        } finally {
            inFlightStageFutures.remove(key, future);
        }
    }

    /**
     * Same as {@link #updateStageStatus} but swallows (logs, does not rethrow) a failure to
     * write the stage's own terminal status. This write can lose a benign race with
     * {@code cancelRun()}'s "belt-and-suspenders" loop, which independently writes a terminal
     * CANCELLED status directly to any RUNNING row for the run being cancelled — both writers
     * can target the exact same row concurrently (one via {@code saveAll()} on the cancelling
     * thread, this one via {@code save()} on the just-interrupted stage's own thread as it
     * unwinds). Optimistic locking (the entity's {@code @Version} column) correctly rejects
     * whichever write loses that race — but before this method existed, a lost race propagated
     * an uncaught exception out of {@code executeStage()}, which (a) let {@code processSymbol()}'s
     * outer catch overwrite the row's already-correct CANCELLED status with ERROR, and (b) aborted
     * the rest of that symbol's stage loop entirely, leaving its remaining stages stuck at PENDING
     * forever. Confirmed live against the real Postgres/Hibernate stack (not just a theoretical
     * race): {@code ObjectOptimisticLockingFailureException} / "Row was already updated or deleted
     * by another transaction" / "Unexpected row count (expected row count 1 but was 0)".
     */
    private void updateStageStatusTolerantly(UUID runId, String symbol, JobRunStage.StageName stage,
                                    JobRunStage.Status status, Long durationMs,
                                    String errorMessage, String resultSummary) {
        try {
            updateStageStatus(runId, symbol, stage, status, durationMs, errorMessage, resultSummary);
        } catch (Exception e) {
            logger.debug("Stage {} status write for {} (-> {}) lost a race — most likely "
                + "cancelRun() already wrote an equivalent terminal status for this row "
                + "concurrently, which is safe to ignore here: {}",
                stage, symbol, status, e.getMessage());
        }
    }

    private String stageDataFetch(String symbol) {
        LocalDate today = LocalDate.now(IST);
        LocalDate yesterday = today.minusDays(1);
        dataIngestionService.processSingleStock(symbol, yesterday);
        List<OhlcvCandle> candles = candleStore.findTopBySymbolOrderByDateDesc(symbol, 100);
        return candles.size() + " candles available";
    }

    private String stageNews(String symbol) {
        List<?> articles = newsIngestionService.fetchStockNews(symbol);
        return articles.size() + " articles fetched";
    }

    private String stageSentiment(String symbol, LocalDate date) {
        var result = sentimentService.analyzeStockSentiment(symbol, date);
        return result.score() + ", confidence " + result.confidence();
    }

    private String stageSignal(String symbol) {
        var signal = signalPipeline.generatePrimarySignal(symbol);
        return signal.map(s -> s.type() + " signal generated, confidence " +
            String.format("%.0f", s.confidence().doubleValue() * 100) + "%").orElse("no signal");
    }

    private StageExecutionResult stageBacktest(String symbol) {
        try {
            BacktestConfig config = BacktestConfig.defaults();
            BacktestResult result = backtestEngine.runBacktest(symbol, "NSE", config);
            String summary = result.totalTrades() + " trades, "
                + String.format("%.0f", result.winRate()) + "% win, "
                + String.format("%.1f", result.totalReturn()) + "% return";
            return StageExecutionResult.completed(summary);
        } catch (IllegalStateException e) {
            logger.info("Backtest skipped for {}: {}", symbol, e.getMessage());
            return StageExecutionResult.skipped(e.getMessage());
        }
    }

    private String stagePaperTrade(String symbol) {
        List<Signal> unprocessed = signalStore.findUnprocessed()
            .stream()
            .filter(s -> s.symbol().equals(symbol))
            .toList();

        int executed = 0;
        int failedAfterMark = 0;
        for (Signal signal : unprocessed) {
            OhlcvCandle latest = candleStore.findLatestBySymbol(symbol)
                .orElse(null);
            if (latest == null || latest.close() == null) continue;

            // Mark the signal processed BEFORE executing the trade, not after. If we executed
            // first and markProcessed() then threw (e.g. an optimistic-lock failure on a row
            // with a stale/null @Version), the trade would already be live but the signal
            // would still show up in findUnprocessed() on the next run/retry — risking a
            // second real position being opened for the same signal. Marking processed first
            // makes "already handled" durable before any capital is committed: a failure here
            // simply skips the trade this run (retried next run), which is the safe failure
            // mode versus a silent duplicate execution.
            try {
                signalStore.markProcessed(signal.id());
            } catch (Exception e) {
                logger.warn("""
                    Failed to mark signal {} processed for {} — skipping trade execution \
                    this run to avoid a possible duplicate; will retry next run: {}""",
                    signal.id(), symbol, e.getMessage());
                continue;
            }

            try {
                tradingService.executeSignal(signal, latest.close());
                executed++;
            } catch (Exception e) {
                // The signal is already marked processed at this point, so it will NOT be
                // retried automatically. Log at WARN (not debug) and surface it in the stage
                // summary so a failed trade attempt is visible for manual follow-up instead of
                // silently vanishing.
                failedAfterMark++;
                logger.warn("""
                    Paper trade execution failed for {} signal {} AFTER marking it processed \
                    — this signal will not be retried automatically: {}""",
                    symbol, signal.id(), e.getMessage());
            }
        }
        String summary = executed + " trade(s) executed";
        if (failedAfterMark > 0) {
            summary += ", " + failedAfterMark + " failed after marking processed (see logs)";
        }
        return summary;
    }

    private void updateStageStatus(UUID runId, String symbol, JobRunStage.StageName stage,
                                    JobRunStage.Status status, Long durationMs,
                                    String errorMessage, String resultSummary) {
        List<JobRunStageEntity> existing = jobRunStageRepository
            .findByRunIdAndSymbolAndStageName(runId, symbol, stage.name());

        if (!existing.isEmpty()) {
            JobRunStageEntity entity = existing.get(0);
            entity.setStatus(status.name());
            entity.setStartedAt(entity.getStartedAt() != null ? entity.getStartedAt()
                : java.time.LocalDateTime.now(IST));
            if (status == JobRunStage.Status.COMPLETED || status == JobRunStage.Status.ERROR
                || status == JobRunStage.Status.SKIPPED) {
                entity.setCompletedAt(java.time.LocalDateTime.now(IST));
                entity.setDurationMs(durationMs);
            }
            entity.setErrorMessage(errorMessage);
            entity.setResultSummary(resultSummary);
            jobRunStageRepository.save(entity);
        } else {
            JobRunStage stageRow = new JobRunStage(
                runId, symbol, stage, status,
                java.time.LocalDateTime.now(IST),
                (status == JobRunStage.Status.COMPLETED || status == JobRunStage.Status.ERROR)
                    ? java.time.LocalDateTime.now(IST) : null,
                durationMs, errorMessage, null, resultSummary
            );
            jobRunStageRepository.save(JobRunStageEntity.fromDomain(stageRow));
        }
    }

    private void recordCompletion(UUID runId, String symbol) {
        // Atomic SQL UPDATE — avoids read-modify-write race between parallel symbols
        int updated = jobRunRepository.incrementCompletedCount(runId);
        if (updated == 0) {
            logger.warn("recordCompletion: no JobRun found for runId {}", runId);
        }
    }

    private void logError(UUID runId, String symbol, JobRunStage.StageName stage, String prefix, Throwable ex) {
        String msg = prefix + " for " + symbol + ": " + ex.getMessage();
        logger.error(msg, ex);
        updateStageStatus(runId, symbol, stage,
            JobRunStage.Status.ERROR, null, msg, null);
    }

    /**
     * Logs a pipeline-level failure that escaped {@link #processSymbol} entirely (e.g. an
     * uncaught {@link Error}) — no specific stage row is touched since we cannot know which
     * stage, if any, was executing when the failure happened.
     */
    private void logUnattributedPipelineError(String symbol, Throwable ex) {
        logger.error("Pipeline failed for {}: {}", symbol, ex.getMessage(), ex);
    }

    private void completeRun(UUID runId, JobRun.Status status, String errorMessage) {
        var runOpt = jobRunRepository.findByRunId(runId);
        runOpt.ifPresent(entity -> {
            entity.setStatus(status.name());
            entity.setCompletedAt(java.time.LocalDateTime.now(IST));
            entity.setErrorMessage(errorMessage);

            // Count failures
            long errorCount = jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(runId).stream()
                .filter(e -> "ERROR".equals(e.getStatus()))
                .map(JobRunStageEntity::getSymbol)
                .distinct()
                .count();
            entity.setFailedCount((int) errorCount);

            jobRunRepository.save(entity);
        });
        long durationMs = runOpt.filter(e -> e.getStartedAt() != null)
            .map(e -> java.time.Duration.between(e.getStartedAt(), e.getCompletedAt()).toMillis())
            .orElse(0L);
        if (status == JobRun.Status.COMPLETED) {
            jobMetrics.recordRunCompleted(durationMs);
        } else {
            jobMetrics.recordRunFailed(durationMs);
        }
        logger.info("Run {} completed with status {}", runId, status);
    }

    /**
     * Last-resort path when the normal completion logic in {@code whenComplete} throws.
     * Writes only the JobRun row itself (no stage aggregation) so it has the best chance
     * of forcing the run out of RUNNING even if the failure was in stage-table access.
     */
    private void forceRunFailedSafely(UUID runId, String reason) {
        try {
            jobRunRepository.findByRunId(runId).ifPresent(entity -> {
                entity.setStatus(JobRun.Status.FAILED.name());
                entity.setCompletedAt(java.time.LocalDateTime.now(IST));
                entity.setErrorMessage(reason);
                jobRunRepository.save(entity);
            });
            jobMetrics.recordRunFailed(0L);
        } catch (Exception fatal) {
            logger.error("CRITICAL: failed to force run {} to FAILED after a completion "
                + "error — row may be left stuck at RUNNING: {}", runId, fatal.getMessage(), fatal);
        }
    }

    /**
     * Generous, watchlist-size-aware staleness threshold: worst-case per-symbol stage time,
     * times the number of concurrency batches needed to process {@code symbolsCount} symbols
     * at {@code maxConcurrent} parallelism, times a large safety multiplier. A run older than
     * this either survived a JVM restart or is otherwise abandoned.
     */
    private Duration staleThreshold(int symbolsCount) {
        int batches = Math.ceilDiv(Math.max(1, symbolsCount), Math.max(1, maxConcurrent));
        return Duration.ofSeconds(STAGE_TIMEOUT_SUM_SECONDS * batches * STALE_RUN_SAFETY_MULTIPLIER);
    }

    private boolean isStale(JobRunEntity run) {
        if (run.getStartedAt() == null) {
            return false;
        }
        Duration age = Duration.between(run.getStartedAt(), java.time.LocalDateTime.now(IST));
        return age.compareTo(staleThreshold(run.getSymbolsCount())) > 0;
    }

    /**
     * Returns the currently-blocking run, if any. A RUNNING row older than its staleness
     * threshold is treated as orphaned (not blocking) rather than skipping forever — the
     * watchdog reaper (see {@link #reapOrphanedRuns()}) cleans up the DB row separately,
     * but callers should not wait for that before allowing a new run.
     */
    @Transactional(readOnly = true)
    public Optional<JobRun> findActiveRun() {
        return jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name())
            .stream()
            .filter(e -> !isStale(e))
            .findFirst()
            .map(JobRunEntity::toDomain);
    }

    /**
     * Self-healing watchdog: finds job_runs rows stuck RUNNING past their staleness
     * threshold (e.g. abandoned by a JVM restart mid-run) and force-fails them, along with
     * any of their stages still marked RUNNING. Runs shortly after startup and periodically
     * thereafter so an orphaned run recovers automatically instead of requiring a manual
     * DB fix.
     *
     * <p>This heuristic-based check exists for a run that is stuck WITHIN a still-live JVM
     * (e.g. a hung LLM call) — it deliberately waits out a large safety margin so it never
     * kills a legitimately slow-but-progressing run. It is complementary to, and does not
     * replace, {@link #reapAllRunningRunsOnStartup()} below.</p>
     */
    @Scheduled(
        initialDelayString = "${job.orchestrator.reaper.initial-delay-ms:15000}",
        fixedDelayString = "${job.orchestrator.reaper.interval-ms:300000}")
    public void reapOrphanedRuns() {
        if (!reaperEnabled) {
            return;
        }
        List<JobRunEntity> running = jobRunRepository
            .findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name());
        for (JobRunEntity run : running) {
            if (isStale(run)) {
                String reason = """
                    Reaped as orphaned: RUNNING for longer than the staleness threshold \
                    (%d min) — likely abandoned by a JVM restart"""
                    .formatted(staleThreshold(run.getSymbolsCount()).toMinutes());
                reapRun(run, reason);
            }
        }
    }

    /**
     * Startup-time correction for the restart case: any {@code job_runs} row still marked
     * RUNNING when this JVM boots is <em>definitely</em> orphaned — the thread that was
     * executing it belonged to the previous process and no longer exists, so there is no
     * "wait and see" case here unlike {@link #reapOrphanedRuns()}. Reaping immediately
     * (rather than waiting out the multi-hour staleness heuristic) avoids forcing a manual
     * {@code /cancel} call after every routine restart before a new run can start.
     *
     * <p>Purely additive: the periodic in-process staleness check above is unchanged and
     * still runs — it catches a different failure mode (a run stuck while this same JVM is
     * still alive).</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reapAllRunningRunsOnStartup() {
        if (!reaperEnabled) {
            return;
        }
        List<JobRunEntity> running = jobRunRepository
            .findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name());
        for (JobRunEntity run : running) {
            reapRun(run, "Orphaned by application restart — executing thread no longer exists");
        }
    }

    private void reapRun(JobRunEntity run, String reason) {
        logger.warn("Reaping orphaned run {}: startedAt={}, symbolsCount={}, reason={}",
            run.getRunId(), run.getStartedAt(), run.getSymbolsCount(), reason);

        run.setStatus(JobRun.Status.FAILED.name());
        run.setCompletedAt(java.time.LocalDateTime.now(IST));
        run.setErrorMessage(reason);
        jobRunRepository.save(run);
        jobMetrics.recordRunReaped();

        List<JobRunStageEntity> runningStages = jobRunStageRepository
            .findByRunIdOrderBySymbolAscStageNameAsc(run.getRunId()).stream()
            .filter(e -> JobRunStage.Status.RUNNING.name().equals(e.getStatus()))
            .toList();
        for (JobRunStageEntity stage : runningStages) {
            stage.setStatus(JobRunStage.Status.ERROR.name());
            stage.setCompletedAt(java.time.LocalDateTime.now(IST));
            stage.setErrorMessage(
                "Reaped as orphaned — run was force-failed while this stage was RUNNING");
        }
        jobRunStageRepository.saveAll(runningStages);
    }

    /**
     * Cancels a running run.
     */
    public void cancelRun(UUID runId) {
        var runOpt = jobRunRepository.findByRunId(runId);
        if (runOpt.isEmpty() || !JobRun.Status.RUNNING.name().equals(runOpt.get().getStatus())) {
            return;
        }

        // Flag first so any symbol not yet past acquireSlot(), or about to start its next
        // stage, sees the signal and stops advancing without doing further work.
        cancelledRunIds.add(runId);

        // Interrupt whichever stage-level thread(s) are actually in flight for this run —
        // without this, the DB status flip below has no effect on the already-running blocking
        // call (e.g. the SENTIMENT stage's Ollama/vLLM HTTP request), which would otherwise
        // keep burning CPU until it finishes naturally (up to TIMEOUT_SENTIMENT).
        String prefix = runId + "::";
        int interruptedCount = 0;
        for (var entry : inFlightStageFutures.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().cancel(true)) {
                interruptedCount++;
            }
        }
        if (interruptedCount > 0) {
            logger.info("Run {} cancel: interrupted {} in-flight stage thread(s)", runId, interruptedCount);
        }

        // Mark all RUNNING stages as CANCELLED (belt-and-suspenders alongside the interrupt
        // above — executeStage()'s own CancellationException handler will also write this same
        // terminal state once the interrupted thread unwinds).
        for (JobRunStage.StageName stage : JobRunStage.StageName.values()) {
            List<JobRunStageEntity> runningStages = jobRunStageRepository
                .findByRunIdAndStageName(runId, stage.name()).stream()
                .filter(e -> "RUNNING".equals(e.getStatus()))
                .toList();
            for (JobRunStageEntity e : runningStages) {
                e.setStatus(JobRunStage.Status.CANCELLED.name());
                e.setCompletedAt(java.time.LocalDateTime.now(IST));
                e.setErrorMessage("Cancelled by user request");
            }
            jobRunStageRepository.saveAll(runningStages);
        }

        var entity = runOpt.get();
        entity.setStatus(JobRun.Status.CANCELLED.name());
        entity.setCompletedAt(java.time.LocalDateTime.now(IST));
        jobRunRepository.save(entity);
        jobMetrics.recordRunCancelled();
        logger.info("Run {} cancelled", runId);
    }

    /**
     * Gets progress for a run — all stage rows grouped by symbol.
     */
    @Transactional(readOnly = true)
    public JobRunProgress getProgress(UUID runId) {
        var runOpt = jobRunRepository.findByRunId(runId);
        if (runOpt.isEmpty()) return null;

        JobRunEntity runEntity = runOpt.get();
        List<JobRunStageEntity> stages = jobRunStageRepository
            .findByRunIdOrderBySymbolAscStageNameAsc(runId);

        return new JobRunProgress(
            runEntity.getRunId(),
            runEntity.getStatus(),
            runEntity.getSymbolsCount(),
            runEntity.getCompletedCount(),
            runEntity.getFailedCount(),
            stages
        );
    }

    /**
     * Gets a summary for a completed run.
     */
    @Transactional(readOnly = true)
    public JobRunSummary getSummary(UUID runId) {
        var runOpt = jobRunRepository.findByRunId(runId);
        if (runOpt.isEmpty()) return null;

        JobRunEntity runEntity = runOpt.get();
        List<JobRunStageEntity> stages = jobRunStageRepository
            .findByRunIdOrderBySymbolAscStageNameAsc(runId);

        // Per-stage aggregates
        java.util.Map<String, StageStats> stageStats = new java.util.HashMap<>();
        for (var entry : stages.stream().collect(java.util.stream.Collectors.groupingBy(JobRunStageEntity::getStageName)).entrySet()) {
            List<JobRunStageEntity> list = entry.getValue();
            int total = list.size();
            long completed = list.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
            long errors = list.stream().filter(e -> "ERROR".equals(e.getStatus())).count();
            long duration = list.stream().mapToLong(e ->
                e.getDurationMs() != null ? e.getDurationMs() : 0).sum();
            stageStats.put(entry.getKey(), new StageStats(total, (int) completed, (int) errors, duration));
        }

        // Per-symbol detail
        List<SymbolDetail> symbolDetails = stages.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                JobRunStageEntity::getSymbol,
                java.util.stream.Collectors.mapping(e -> e.getStageName() + ":" + e.getStatus(),
                    java.util.stream.Collectors.toList())
            ))
            .entrySet().stream()
            .map(e -> new SymbolDetail(e.getKey(), e.getValue()))
            .toList();

        return new JobRunSummary(
            runEntity.getRunId(),
            runEntity.getStatus(),
            runEntity.getSymbolsCount(),
            runEntity.getCompletedCount(),
            runEntity.getFailedCount(),
            stages.stream().mapToLong(e -> e.getDurationMs() != null ? e.getDurationMs() : 0).sum(),
            stageStats,
            symbolDetails
        );
    }

    /**
     * Lists past runs.
     */
    @Transactional(readOnly = true)
    public List<JobRun> listRuns() {
        return jobRunRepository.findAllByOrderByStartedAtDesc().stream()
            .map(JobRunEntity::toDomain)
            .toList();
    }
}