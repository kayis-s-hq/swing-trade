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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates the 6-stage job pipeline across all watchlist symbols
 * with concurrency control and per-stage progress tracking.
 */
@Service
public class JobOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(JobOrchestratorService.class);
    private static final int MAX_CONCURRENT = 3;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Per-stage timeouts (seconds)
    private static final long TIMEOUT_DATA_FETCH = 30L;
    private static final long TIMEOUT_NEWS = 30L;
    private static final long TIMEOUT_SENTIMENT = 60L;
    private static final long TIMEOUT_SIGNAL = 30L;
    private static final long TIMEOUT_BACKTEST = 120L;
    private static final long TIMEOUT_PAPER_TRADE = 30L;

    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);
    private final ExecutorService asyncExecutor = new ThreadPoolExecutor(
        3,  // 3 cores — matches MAX_CONCURRENT
        3,  // max = 3
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(10),
        r -> {
            Thread t = new Thread(r, "job-orchestrator-%d".formatted(Thread.activeCount()));
            t.setDaemon(true);
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

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
            JobOrchestratorMetrics jobMetrics) {
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

        final JobRun finalRun = run;

        List<String> symbols = watchlistStore.getActiveWatchlistSymbols();
        if (symbols.isEmpty()) {
            logger.info("No watchlist symbols — completing run with zero symbols");
            var entity = jobRunRepository.findByRunId(run.runId()).orElseThrow();
            entity.setSymbolsCount(0);
            entity.setCompletedCount(0);
            entity.setFailedCount(0);
            entity.setCompletedAt(java.time.LocalDateTime.now(IST));
            jobRunRepository.save(entity);
            return run;
        }

        var entity2 = jobRunRepository.findByRunId(run.runId()).orElseThrow();
        entity2.setSymbolsCount(symbols.size());
        jobRunRepository.save(entity2);

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
                    logError(finalRun.runId(), symbol, "Pipeline failed", ex);
                    return null;
                })
        ).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((v, ex) -> {
                if (ex != null) {
                    completeRun(finalRun.runId(), JobRun.Status.FAILED, ex.getMessage());
                } else {
                    // Count failures
                    List<JobRunStageEntity> failedStages = jobRunStageRepository
                        .findByRunIdAndStageName(finalRun.runId(), "ERROR");
                    if (!failedStages.isEmpty()) {
                        // Check if >50% symbols had at least one error
                        List<String> symbolsWithErrors = jobRunStageRepository
                            .findByRunIdAndStageName(finalRun.runId(), "ERROR").stream()
                            .map(JobRunStageEntity::getSymbol)
                            .distinct()
                            .toList();
                        if (symbolsWithErrors.size() > symbols.size() / 2) {
                            completeRun(finalRun.runId(), JobRun.Status.FAILED,
                                symbolsWithErrors.size() + " symbols failed");
                        } else {
                            completeRun(finalRun.runId(), JobRun.Status.COMPLETED, null);
                        }
                    } else {
                        completeRun(finalRun.runId(), JobRun.Status.COMPLETED, null);
                    }
                }
            });

        return run;
    }

    private void processSymbol(UUID runId, String symbol, LocalDate today) {
        try {
            semaphore.acquire();
            try {
                executeStage(runId, symbol, JobRunStage.StageName.DATA_FETCH,
                    () -> stageDataFetch(symbol), TIMEOUT_DATA_FETCH);
                executeStage(runId, symbol, JobRunStage.StageName.NEWS,
                    () -> stageNews(symbol), TIMEOUT_NEWS);
                executeStage(runId, symbol, JobRunStage.StageName.SENTIMENT,
                    () -> stageSentiment(symbol, today), TIMEOUT_SENTIMENT);
                executeStage(runId, symbol, JobRunStage.StageName.SIGNAL,
                    () -> stageSignal(symbol), TIMEOUT_SIGNAL);
                executeStage(runId, symbol, JobRunStage.StageName.BACKTEST,
                    () -> stageBacktest(symbol), TIMEOUT_BACKTEST);
                executeStage(runId, symbol, JobRunStage.StageName.PAPER_TRADE,
                    () -> stagePaperTrade(symbol), TIMEOUT_PAPER_TRADE);

                recordCompletion(runId, symbol);
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError(runId, symbol, "Pipeline interrupted", e);
        } catch (Exception e) {
            logError(runId, symbol, "Pipeline failed", e);
        }
    }

    @FunctionalInterface
    private interface StageExecutor {
        String execute() throws Exception;
    }

    private void executeStage(UUID runId, String symbol, JobRunStage.StageName stage,
                              StageExecutor executor, long timeoutSec) {
        updateStageStatus(runId, symbol, stage, JobRunStage.Status.RUNNING, null, null, null);

        long start = System.currentTimeMillis();
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return executor.execute();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, asyncExecutor);

            String result = future.get(timeoutSec, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;
            updateStageStatus(runId, symbol, stage, JobRunStage.Status.COMPLETED,
                duration, null, result);
            logger.debug("Stage {} completed for {} in {}ms", stage, symbol, duration);
        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            String msg = "Stage timed out after " + timeoutSec + "s";
            updateStageStatus(runId, symbol, stage, JobRunStage.Status.ERROR,
                duration, msg, null);
            logger.warn("{} for {}", msg, symbol);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            updateStageStatus(runId, symbol, stage, JobRunStage.Status.ERROR,
                duration, e.getMessage(), null);
            logger.warn("Stage {} failed for {}: {}", stage, symbol, e.getMessage());
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

    private String stageBacktest(String symbol) {
        try {
            BacktestConfig config = BacktestConfig.defaults();
            BacktestResult result = backtestEngine.runBacktest(symbol, "NSE", config);
            return result.totalTrades() + " trades, " +
                String.format("%.0f", result.winRate()) + "% win, " +
                String.format("%.1f", result.totalReturn()) + "% return";
        } catch (Exception e) {
            logger.debug("Backtest skipped for {}: {}", symbol, e.getMessage());
            return "skipped (insufficient data)";
        }
    }

    private String stagePaperTrade(String symbol) {
        LocalDate today = LocalDate.now(IST);
        List<Signal> unprocessed = signalStore.findUnprocessed()
            .stream()
            .filter(s -> s.symbol().equals(symbol))
            .toList();

        int executed = 0;
        for (Signal signal : unprocessed) {
            OhlcvCandle latest = candleStore.findLatestBySymbol(symbol)
                .orElse(null);
            if (latest == null || latest.close() == null) continue;

            try {
                tradingService.executeSignal(signal, latest.close());
                signalStore.markProcessed(signal.id());
                executed++;
            } catch (Exception e) {
                logger.debug("Paper trade failed for {} signal {}: {}",
                    symbol, signal.id(), e.getMessage());
            }
        }
        return executed + " trade(s) executed";
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

    private void logError(UUID runId, String symbol, String prefix, Throwable ex) {
        String msg = prefix + " for " + symbol + ": " + ex.getMessage();
        logger.error(msg, ex);
        updateStageStatus(runId, symbol, JobRunStage.StageName.DATA_FETCH,
            JobRunStage.Status.ERROR, null, msg, null);
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
     * Cancels a running run.
     */
    public void cancelRun(UUID runId) {
        var runOpt = jobRunRepository.findByRunId(runId);
        if (runOpt.isEmpty() || !JobRun.Status.RUNNING.name().equals(runOpt.get().getStatus())) {
            return;
        }

        // Mark all RUNNING stages as CANCELLED
        for (JobRunStage.StageName stage : JobRunStage.StageName.values()) {
            List<JobRunStageEntity> runningStages = jobRunStageRepository
                .findByRunIdAndStageName(runId, stage.name()).stream()
                .filter(e -> "RUNNING".equals(e.getStatus()))
                .toList();
            for (JobRunStageEntity e : runningStages) {
                e.setStatus("CANCELLED");
                e.setCompletedAt(java.time.LocalDateTime.now(IST));
            }
            jobRunStageRepository.saveAll(runningStages);
        }

        var entity = runOpt.get();
        entity.setStatus("CANCELLED");
        entity.setCompletedAt(java.time.LocalDateTime.now(IST));
        jobRunRepository.save(entity);
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