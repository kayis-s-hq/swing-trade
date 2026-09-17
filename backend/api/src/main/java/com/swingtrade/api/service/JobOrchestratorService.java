package com.swingtrade.api.service;

import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.JobRun;
import com.swingtrade.domain.JobRunStage;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.LlmAnalysisResult;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.WatchlistStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.BacktestResultStore;
import com.swingtrade.domain.store.LlmAnalysisResultStore;
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
 * Orchestrates the 7-stage job pipeline across all watchlist symbols
 * with concurrency control and per-stage progress tracking.
 */
@Service
public class JobOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(JobOrchestratorService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Per-stage timeouts (seconds)
    private static final long TIMEOUT_DATA_FETCH = 30L;
    private static final long TIMEOUT_NEWS = 30L;
    // Matches the CPU-only local LLM deadlines in SentimentService and
    // SynthesisService. The Pi can take up to 48 minutes for a large prompt;
    // a 10-minute stage timeout cancels the work before the inner client wait
    // can complete.
    private static final long TIMEOUT_SENTIMENT = 2880L;
    private static final long TIMEOUT_LLM_ANALYSIS = 2880L;
    private static final long TIMEOUT_SIGNAL = 30L;
    private static final long TIMEOUT_BACKTEST = 120L;
    private static final long TIMEOUT_PAPER_TRADE = 30L;

    /** Worst-case wall-clock seconds a single symbol can spend across all seven stages. */
    private static final long STAGE_TIMEOUT_SUM_SECONDS =
        TIMEOUT_DATA_FETCH + TIMEOUT_NEWS + TIMEOUT_SENTIMENT
            + TIMEOUT_SIGNAL + TIMEOUT_BACKTEST + TIMEOUT_LLM_ANALYSIS + TIMEOUT_PAPER_TRADE;

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

    /**
     * Guards the "is a run already active?" check and the creation of a new RUNNING
     * row in {@link #startRun(JobRun.TriggerType)} so the two happen atomically.
     * Without this, two near-simultaneous callers (the manual trigger racing the
     * scheduled cron, or a double-click) can both pass {@link #findActiveRun()}
     * before either has persisted its RUNNING row, and both proceed to double-run
     * the pipeline over the same watchlist. A single JVM-local lock is sufficient
     * here: this is a self-hosted, single-instance deployment, not a clustered one.
     */
    private final Object runStartLock = new Object();

    private final DataIngestionService dataIngestionService;
    private final NewsIngestionService newsIngestionService;
    private final SentimentService sentimentService;
    private final SignalPipeline signalPipeline;
    private final SentimentGate sentimentGate;
    private final BacktestEngine backtestEngine;
    private final TradingService tradingService;
    private final JobRunRepository jobRunRepository;
    private final JobRunStageRepository jobRunStageRepository;
    private final SignalStore signalStore;
    private final WatchlistStore watchlistStore;
    private final CandleStore candleStore;
    private final JobOrchestratorMetrics jobMetrics;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final FundamentalScorer fundamentalScorer;
    private final CompositeAnalysisService compositeAnalysisService;
    private final com.swingtrade.llm.service.SynthesisService synthesisService;
    private final BacktestResultStore backtestResultStore;
    private final LlmAnalysisResultStore llmAnalysisResultStore;
    private final LlmAnalysisGate llmAnalysisGate;
    private final SentimentStore sentimentStore;
    private final StrategyConfigStore strategyConfigStore;
    private final PaperPortfolioService paperPortfolioService;
    private final boolean llmAnalysisEnabled;
    private final boolean llmAnalysisAdvisoryOnly;

    @org.springframework.beans.factory.annotation.Autowired
    public JobOrchestratorService(
            DataIngestionService dataIngestionService,
            NewsIngestionService newsIngestionService,
            SentimentService sentimentService,
            SignalPipeline signalPipeline,
            SentimentGate sentimentGate,
            BacktestEngine backtestEngine,
            TradingService tradingService,
            JobRunRepository jobRunRepository,
            JobRunStageRepository jobRunStageRepository,
            SignalStore signalStore,
            WatchlistStore watchlistStore,
            CandleStore candleStore,
            JobOrchestratorMetrics jobMetrics,
            TechnicalAnalysisService technicalAnalysisService,
            FundamentalScorer fundamentalScorer,
            CompositeAnalysisService compositeAnalysisService,
            com.swingtrade.llm.service.SynthesisService synthesisService,
            BacktestResultStore backtestResultStore,
            LlmAnalysisResultStore llmAnalysisResultStore,
            LlmAnalysisGate llmAnalysisGate,
            SentimentStore sentimentStore,
            StrategyConfigStore strategyConfigStore,
            PaperPortfolioService paperPortfolioService,
            @Value("${job.orchestrator.max-concurrent:3}") int maxConcurrent,
            @Value("${job.orchestrator.poll-interval-ms:1000}") long pollIntervalMs,
            @Value("${job.orchestrator.reaper.enabled:true}") boolean reaperEnabled,
            @Value("${job.orchestrator.llm-analysis.enabled:true}") boolean llmAnalysisEnabled,
            @Value("${job.orchestrator.llm-analysis.advisory-only:true}") boolean llmAnalysisAdvisoryOnly) {
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
        this.sentimentGate = sentimentGate;
        this.backtestEngine = backtestEngine;
        this.tradingService = tradingService;
        this.jobRunRepository = jobRunRepository;
        this.jobRunStageRepository = jobRunStageRepository;
        this.signalStore = signalStore;
        this.watchlistStore = watchlistStore;
        this.candleStore = candleStore;
        this.jobMetrics = jobMetrics;
        this.technicalAnalysisService = technicalAnalysisService;
        this.fundamentalScorer = fundamentalScorer;
        this.compositeAnalysisService = compositeAnalysisService;
        this.synthesisService = synthesisService;
        this.backtestResultStore = backtestResultStore;
        this.llmAnalysisResultStore = llmAnalysisResultStore;
        this.llmAnalysisGate = llmAnalysisGate;
        this.sentimentStore = sentimentStore;
        this.strategyConfigStore = strategyConfigStore;
        this.paperPortfolioService = paperPortfolioService;
        this.llmAnalysisEnabled = llmAnalysisEnabled;
        this.llmAnalysisAdvisoryOnly = llmAnalysisAdvisoryOnly;
    }

    /** Compatibility fixture constructor for pre-LLM pipeline tests. */
    public JobOrchestratorService(DataIngestionService d, NewsIngestionService n, SentimentService s,
            SignalPipeline p, SentimentGate sg, BacktestEngine b, TradingService t,
            JobRunRepository jr, JobRunStageRepository js, SignalStore ss, WatchlistStore w,
            CandleStore c, JobOrchestratorMetrics m, int max, long poll, boolean reaper) {
        this(d,n,s,p,sg,b,t,jr,js,ss,w,c,m,null,null,null,null,null,null,null,null,null,null,max,poll,reaper,false,true);
    }

    /**
     * Starts a new pipeline run for all active watchlist symbols.
     * Returns immediately with the run record; processing is async.
     *
     * @throws ConcurrentRunException if another run is already active. The
     *     check-and-create is done under {@link #runStartLock} so two
     *     near-simultaneous callers cannot both slip past {@link #findActiveRun()}
     *     before either has persisted its RUNNING row.
     */
    public JobRun startRun(JobRun.TriggerType triggerType) {
        LocalDate today = LocalDate.now(IST);
        JobRun run;
        synchronized (runStartLock) {
            Optional<JobRun> activeRun = findActiveRun();
            if (activeRun.isPresent()) {
                throw new ConcurrentRunException(activeRun.get());
            }

            run = new JobRun(
                UUID.randomUUID(),
                triggerType,
                JobRun.Status.RUNNING,
                java.time.LocalDateTime.now(IST),
                null, 0, 0, 0, null
            );
            jobRunRepository.save(JobRunEntity.fromDomain(run));
        }
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
                    // >= half, not > half: a run where exactly half the symbols failed
                    // every stage is not a success and must not report COMPLETED.
                    if (!symbolsWithErrors.isEmpty() && symbolsWithErrors.size() * 2 >= symbols.size()) {
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

    private void processSymbol(UUID runId, String symbol, LocalDate today) {
        JobRunStage.StageName[] currentStage = {JobRunStage.StageName.DATA_FETCH};
        try {
            acquireSlot(symbol);
            try {
                // Set by the SIGNAL stage's executor for the signal context used by the
                // PAPER_TRADE stage. NEWS always runs for every symbol; SENTIMENT is gated (see
                // sentimentTrigger below) once any multi-strategy variant is active (plan §7.1).
                Signal.SignalType[] signalType = {null};
                // True if the legacy signal was a BUY, or any active variant with sentimentGate
                // enabled produced a BUY for this symbol/day (plan §7.1 item 5). Defaults to
                // true when no variants are active at all, preserving today's "always run
                // sentiment" behaviour for the pre-multi-strategy / no-variants-configured case.
                boolean[] sentimentTrigger = {false};

                List<StageDef> stageDefs = new java.util.ArrayList<>(List.of(
                    new StageDef(JobRunStage.StageName.DATA_FETCH,
                        () -> StageExecutionResult.completed(stageDataFetch(symbol)), TIMEOUT_DATA_FETCH),
                    new StageDef(JobRunStage.StageName.SIGNAL,
                        () -> stageSignal(symbol, signalType, sentimentTrigger), TIMEOUT_SIGNAL),
                    new StageDef(JobRunStage.StageName.BACKTEST, () -> stageBacktest(symbol), TIMEOUT_BACKTEST),
                    new StageDef(JobRunStage.StageName.NEWS,
                        () -> StageExecutionResult.completed(stageNews(symbol)), TIMEOUT_NEWS),
                    new StageDef(JobRunStage.StageName.SENTIMENT,
                        () -> stageSentimentGated(symbol, today, sentimentTrigger[0]), TIMEOUT_SENTIMENT),
                    new StageDef(JobRunStage.StageName.PAPER_TRADE,
                        () -> StageExecutionResult.completed(stagePaperTrade(symbol)), TIMEOUT_PAPER_TRADE)
                ));
                if (llmAnalysisEnabled) {
                    stageDefs.add(stageDefs.size() - 1, new StageDef(JobRunStage.StageName.LLM_ANALYSIS,
                        () -> stageLlmAnalysis(runId, symbol, today), TIMEOUT_LLM_ANALYSIS));
                } else {
                    updateStageStatus(runId, symbol, JobRunStage.StageName.LLM_ANALYSIS,
                        JobRunStage.Status.SKIPPED, 0L, null, "disabled by config");
                }

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
                    // A timed-out/failed LLM analysis has no persisted verdict, so PAPER_TRADE
                    // must still run and defer through LlmAnalysisGate.PENDING. Earlier stages
                    // retain the normal skip-cascade semantics.
                    if (!succeeded && stageDef.name() != JobRunStage.StageName.LLM_ANALYSIS) {
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
        String ingestionSummary = dataIngestionService.fetchLatestAndRepairGaps(symbol, yesterday);
        List<OhlcvCandle> candles = candleStore.findTopBySymbolOrderByDateDesc(symbol, 100);
        return candles.size() + " candles available; " + ingestionSummary;
    }

    private String stageNews(String symbol) {
        List<?> articles = newsIngestionService.fetchStockNews(symbol);
        return articles.size() + " articles fetched";
    }

    private String stageSentiment(String symbol, LocalDate date) {
        var result = sentimentService.analyzeStockSentiment(symbol, date);
        return result.score() + ", confidence " + result.confidence();
    }

    /**
     * Gates the SENTIMENT stage per plan §7.1 item 5: skips the (relatively expensive) sentiment
     * analysis call when neither the legacy signal nor any active sentimentGate-enabled variant
     * produced a BUY for this symbol/day.
     */
    private StageExecutionResult stageSentimentGated(String symbol, LocalDate date, boolean trigger) {
        if (!trigger) {
            return StageExecutionResult.skipped(
                "No BUY signal (legacy or sentimentGate-enabled variant) for " + symbol + " on " + date);
        }
        return StageExecutionResult.completed(stageSentiment(symbol, date));
    }

    private StageExecutionResult stageSignal(String symbol, Signal.SignalType[] signalTypeOut,
                                               boolean[] sentimentTriggerOut) {
        var signal = signalPipeline.generatePrimarySignal(symbol);
        signalTypeOut[0] = signal.map(Signal::type).orElse(null);
        boolean legacyBuy = signalTypeOut[0] == Signal.SignalType.BUY;

        // Multi-strategy variant fan-out (plan §7.1). Runs in addition to the legacy signal
        // above; failures for individual variants are isolated inside generateVariantSignals
        // and never fail this stage.
        List<SignalPipeline.VariantSignalOutcome> variantOutcomes;
        try {
            variantOutcomes = signalPipeline.generateVariantSignals(symbol);
        } catch (Exception e) {
            logger.error("Variant signal fan-out failed entirely for {}: {}", symbol, e.getMessage(), e);
            variantOutcomes = List.of();
        }
        boolean anyVariantSentimentBuy = variantOutcomes.stream()
            .anyMatch(o -> o.sentimentGateEnabled() && o.type() == Signal.SignalType.BUY);
        // Preserve today's "always run sentiment" behaviour when the multi-strategy feature is
        // unused (no active variants configured at all) - only gate once a variant actually
        // exists to gate for.
        boolean noVariantsConfigured = !signalPipeline.hasActiveVariants();
        sentimentTriggerOut[0] = legacyBuy || anyVariantSentimentBuy || noVariantsConfigured;

        String summary = signal.map(s -> "%s signal generated, confidence %.0f%%"
            .formatted(s.type(), s.confidence().doubleValue() * 100)).orElse("no signal");
        if (!variantOutcomes.isEmpty()) {
            summary += "; " + variantOutcomes.size() + " variant(s) evaluated";
        }
        return StageExecutionResult.completed(summary);
    }

    private StageExecutionResult stageBacktest(String symbol) {
        try {
            BacktestConfig config = BacktestConfig.defaults();
            BacktestResult result = backtestEngine.runBacktest(symbol, "NSE", config);
            LocalDate date = LocalDate.now(IST);
            if (backtestResultStore != null) backtestResultStore.saveOrUpdate(new com.swingtrade.domain.BacktestResult(null, symbol, date,
                result.totalTrades(), result.winningTrades(), result.losingTrades(), result.winRate(),
                result.avgGainPct(), result.avgLossPct(), result.maxDrawdownPct(), result.sharpeRatio(),
                result.totalReturn(), result.expectancy(), BacktestScorer.calculateProfitFactor(result), true));
            String summary = result.totalTrades() + " trades, "
                + String.format("%.0f", result.winRate()) + "% win, "
                + String.format("%.1f", result.totalReturn()) + "% return";
            return StageExecutionResult.completed(summary);
        } catch (IllegalStateException e) {
            logger.info("Backtest skipped for {}: {}", symbol, e.getMessage());
            if (backtestResultStore != null) backtestResultStore.saveOrUpdate(new com.swingtrade.domain.BacktestResult(null, symbol, LocalDate.now(IST),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false));
            return StageExecutionResult.skipped(e.getMessage());
        }
    }

    private StageExecutionResult stageLlmAnalysis(UUID runId, String symbol, LocalDate date) {
        CompositeAnalysis.TechnicalScore technical;
        CompositeAnalysis.FundamentalScore fundamentals;
        boolean inputFallback = false;
        try { technical = technicalAnalysisService.compute(symbol); }
        catch (Exception e) { inputFallback = true; technical = new CompositeAnalysis.TechnicalScore(0, "HOLD", 0, List.of()); }
        try { fundamentals = fundamentalScorer.compute(symbol); }
        catch (Exception e) { inputFallback = true; fundamentals = new CompositeAnalysis.FundamentalScore(0, List.of("Unavailable")); }
        CompositeAnalysis.BacktestScore backtest = backtestResultStore.findBySymbolAndDate(symbol, date)
            .map(r -> new CompositeAnalysis.BacktestScore(r.totalTrades(), r.winRate(), r.profitFactor(),
                r.maxDrawdownPct(), r.totalReturn(), r.expectancy(), r.hasEnoughData()))
            .orElse(new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false));
        var sentiment = sentimentStore.findBySymbolAndDate(symbol, date).orElse(null);
        CompositeAnalysis composite = compositeAnalysisService.analyze(symbol, technical, fundamentals, backtest, sentiment);
        var synthesis = synthesisService.synthesize(composite);
        llmAnalysisResultStore.saveOrUpdate(new LlmAnalysisResult(null, runId.toString(), symbol, date,
            synthesis.recommendation(), synthesis.confidence(), synthesis.narrative(), synthesis.keyDrivers(),
            synthesis.bullishFactors(), synthesis.bearishFactors(), composite.compositeScore(),
            composite.compositeSignal(), synthesis.success(), !synthesis.success() || inputFallback,
            inputFallback ? "One or more analysis inputs were unavailable" : null));
        String summary = "recommendation=" + synthesis.recommendation() + ", confidence=" + synthesis.confidence();
        return StageExecutionResult.completed(summary);
    }

    /**
     * PAPER_TRADE stage (plan §7.2). Fixes the cross-contamination gap flagged in the §7.1
     * follow-up: {@code SignalStore.findUnprocessed()} used to be filtered only by symbol, with
     * no awareness of which strategy variant produced a signal - so a SHADOW variant's BUY
     * (persisted per-variant since §7.1's fan-out) could be picked up here and executed through
     * the single shared paper engine/"default" portfolio just like the CHAMPION's own signal.
     *
     * <p><b>Interpretation applied here</b> (see {@link com.swingtrade.domain.service.PaperPortfolioService}'s
     * javadoc): {@code PaperTradingEngine} is still a single, process-wide in-memory engine with
     * one live portfolio ({@code "default"}) - it is not portfolio-parametrized. Making every
     * SHADOW variant's BUY execute against its own independently-simulated portfolio would
     * require that engine to become portfolio-aware, which is a larger rearchitecture out of
     * this fix's scope. Given that, this stage now:
     * <ul>
     *   <li>only ever queues BUY signals whose {@code strategy} equals the current CHAMPION
     *       variant's id (or, if no variant configs exist yet, falls back to the old
     *       unfiltered behaviour for backward compatibility with pre-multi-strategy setups);</li>
     *   <li>marks every other (SHADOW) variant's unprocessed BUY signals for this symbol as
     *       processed WITHOUT executing them - they are tracked (persisted, visible via the
     *       signals table) but never reach {@link TradingService#queueSignal}, so they can never
     *       be executed against the "default" portfolio.</li>
     * </ul>
     * This satisfies the routing gap's safety requirement (a SHADOW variant's signal is never
     * executed against "default") even though full per-variant paper execution is deferred.
     */
    private String stagePaperTrade(String symbol) {
        // strategyConfigStore/paperPortfolioService are null in the pre-multi-strategy test
        // fixture constructor (see JobOrchestratorService(DataIngestionService, ..., int, long,
        // boolean) below) - guarded the same way llmAnalysisGate etc. already are, so those
        // callers keep the old unfiltered, single-"default"-portfolio behaviour.
        String championVariantId = strategyConfigStore == null ? null : strategyConfigStore.findCurrentChampion()
            .map(StrategyConfig::variantId)
            .orElse(null);

        List<Signal> unprocessed;
        if (championVariantId != null) {
            int quarantined = signalStore.markProcessedExcludingStrategy(symbol, championVariantId);
            if (quarantined > 0) {
                logger.info("Quarantined {} non-champion (shadow) variant signal(s) for {} from paper execution",
                    quarantined, symbol);
            }
            unprocessed = signalStore.findUnprocessedByStrategy(championVariantId)
                .stream()
                .filter(s -> s.symbol().equals(symbol))
                .toList();
        } else {
            // No strategy configs exist yet (or none is CHAMPION) - preserve pre-multi-strategy
            // behaviour: a single shared signal stream feeds the single shared "default" portfolio.
            unprocessed = signalStore.findUnprocessed()
                .stream()
                .filter(s -> s.symbol().equals(symbol))
                .toList();
        }

        int executed = 0;
        int failedToQueue = 0;
        int blockedBySentiment = 0;
        int blockedByLlm = 0;
        int blockedByKillSwitch = 0;
        for (Signal signal : unprocessed) {
            OhlcvCandle latest = candleStore.findLatestBySymbol(symbol)
                .orElse(null);
            if (latest == null || latest.close() == null) continue;

            // NEWS/SENTIMENT runs for every symbol. A BUY still reads its persisted verdict
            // here rather than assuming the latest sentiment call applies, since a stale
            // unprocessed BUY from an earlier run may have no verdict for its signal date.
            if (signal.type() == Signal.SignalType.BUY) {
                var verdict = sentimentGate.evaluatePersisted(symbol, signal.date());
                if (verdict.action() == SentimentGate.SentimentVerdict.Action.PENDING) {
                    logger.debug("Deferring BUY signal {} for {}: sentiment not yet evaluated for {}",
                        signal.id(), symbol, signal.date());
                    continue; // leave unprocessed, retry once sentiment exists
                }
                if (verdict.action() == SentimentGate.SentimentVerdict.Action.SUPPRESS) {
                    try {
                        signalStore.markProcessed(signal.id());
                    } catch (Exception e) {
                        logger.warn("Failed to mark sentiment-blocked signal {} processed for {}: {}",
                            signal.id(), symbol, e.getMessage());
                        continue;
                    }
                    blockedBySentiment++;
                    logger.info("Blocked BUY signal {} for {} on sentiment: {}",
                        signal.id(), symbol, verdict.reason());
                    continue;
                }
                var llmVerdict = llmAnalysisEnabled && llmAnalysisGate != null
                    ? llmAnalysisGate.evaluatePersisted(symbol, signal.date()) : null;
                if (llmVerdict != null && llmVerdict.action() == LlmAnalysisGate.LlmVerdict.Action.PENDING) continue;
                if (llmVerdict != null && llmVerdict.action() == LlmAnalysisGate.LlmVerdict.Action.SUPPRESS && !llmAnalysisAdvisoryOnly) {
                    try { signalStore.markProcessed(signal.id()); } catch (Exception e) { continue; }
                    blockedByLlm++;
                    logger.info("Blocked BUY signal {} for {} by LLM analysis: {}", signal.id(), symbol, llmVerdict.reason());
                    continue;
                }

                // Per-portfolio daily loss breaker (plan §7.2). championVariantId is null when
                // no strategy configs exist yet (pre-multi-strategy fallback, above); in that
                // case the "default" portfolio may not even exist yet, so PaperPortfolioService
                // treats it as not-breached. RiskControlsService/DailyLossCircuitBreaker already
                // apply a global breaker on the same "default" engine downstream of queueSignal -
                // this check additionally covers the champion's own portfolio_id row explicitly
                // per §7.2, and is the mechanism that will protect shadow portfolios once they
                // gain their own execution path.
                String portfolioId = championVariantId != null ? championVariantId : "default";
                if (paperPortfolioService != null && paperPortfolioService.isDailyLossBreached(portfolioId)) {
                    try {
                        signalStore.markProcessed(signal.id());
                    } catch (Exception e) {
                        logger.warn("Failed to mark kill-switch-blocked signal {} processed for {}: {}",
                            signal.id(), symbol, e.getMessage());
                        continue;
                    }
                    blockedByKillSwitch++;
                    logger.warn("Blocked BUY signal {} for {}: daily loss breaker tripped for portfolio {}",
                        signal.id(), symbol, portfolioId);
                    continue;
                }
            }

            // Queue before marking processed. A capacity rejection returns null and must leave
            // the signal retryable. If persistence fails after a queue succeeds, queueSignal's
            // signal-id dedupe returns the existing pending order on the next run.
            try {
                var queuedOrder = tradingService.queueSignal(signal, latest.close());
                if (queuedOrder == null) {
                    failedToQueue++;
                    logger.warn("Could not queue BUY signal {} for {}; leaving it unprocessed for retry",
                        signal.id(), symbol);
                    continue;
                }
                signalStore.markProcessed(signal.id());
                executed++;
            } catch (Exception e) {
                failedToQueue++;
                logger.warn("""
                    Failed to queue or mark signal {} for {} — leaving the signal retryable: {}""",
                    signal.id(), symbol, e.getMessage());
                continue;
            }
        }
        StringBuilder summary = new StringBuilder(executed + " trade(s) executed");
        if (blockedBySentiment > 0) {
            summary.append(", ").append(blockedBySentiment).append(" blocked by sentiment");
        }
        if (blockedByLlm > 0) summary.append(", ").append(blockedByLlm).append(" blocked by LLM analysis");
        if (blockedByKillSwitch > 0) {
            summary.append(", ").append(blockedByKillSwitch).append(" blocked by daily loss breaker");
        }
        if (failedToQueue > 0) {
            summary.append(", ").append(failedToQueue).append(" failed to queue (see logs)");
        }
        return summary.toString();
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
     * Thrown when {@link #startRun(JobRun.TriggerType)} is rejected because another
     * run is already active. Callers (the manual-trigger controller, the scheduled
     * cron) should catch this and back off rather than treating it as a fatal error.
     */
    public static final class ConcurrentRunException extends IllegalStateException {
        public ConcurrentRunException(JobRun activeRun) {
            super("A job run is already in progress: " + activeRun.runId());
        }
    }

    @FunctionalInterface
    private interface StageExecutor {
        StageExecutionResult execute() throws Exception;
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

}
