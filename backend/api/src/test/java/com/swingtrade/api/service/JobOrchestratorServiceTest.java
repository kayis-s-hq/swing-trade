package com.swingtrade.api.service;

import com.swingtrade.api.service.JobOrchestratorService.JobRunProgress;
import com.swingtrade.api.service.JobOrchestratorService.JobRunSummary;
import com.swingtrade.api.service.JobOrchestratorService.StageStats;
import com.swingtrade.api.service.JobOrchestratorService.SymbolDetail;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.JobRun;
import com.swingtrade.domain.JobRunStage;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.WatchlistStore;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("JobOrchestratorService tests")
class JobOrchestratorServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private JobOrchestratorService service;

    @Mock
    private DataIngestionService dataIngestionService;

    @Mock
    private NewsIngestionService newsIngestionService;

    @Mock
    private SentimentService sentimentService;

    @Mock
    private SignalPipeline signalPipeline;

    @Mock
    private BacktestEngine backtestEngine;

    @Mock
    private TradingService tradingService;

    @Mock
    private JobOrchestratorMetrics jobOrchestratorMetrics;

    @Mock
    private JobRunRepository jobRunRepository;

    @Mock
    private JobRunStageRepository jobRunStageRepository;

    @Mock
    private SignalStore signalStore;

    @Mock
    private WatchlistStore watchlistStore;

    @Mock
    private CandleStore candleStore;

    private UUID runId;

    private JobRunEntity makeRunEntity(JobRun.Status status, int symbolsCount, int completedCount, int failedCount) {
        return makeRunEntity(status, symbolsCount, completedCount, failedCount, LocalDateTime.now());
    }

    private JobRunEntity makeRunEntity(JobRun.Status status, int symbolsCount, int completedCount,
                                        int failedCount, LocalDateTime startedAt) {
        JobRunEntity e = new JobRunEntity();
        e.setRunId(runId);
        e.setStatus(status.name());
        e.setTriggerType(JobRun.TriggerType.SCHEDULED.name());
        e.setStartedAt(startedAt);
        e.setCompletedAt(LocalDateTime.now());
        e.setSymbolsCount(symbolsCount);
        e.setCompletedCount(completedCount);
        e.setFailedCount(failedCount);
        return e;
    }

    /** Capture for stubbing save() → findByRunId() flow in startRun */
    private JobRunEntity capturedRunEntity = new JobRunEntity();

    private JobRunStageEntity makeStageEntity(UUID runId, String symbol,
                                               JobRunStage.StageName stage, String status,
                                               Long durationMs, String resultSummary) {
        JobRunStageEntity e = new JobRunStageEntity();
        e.setRunId(runId);
        e.setSymbol(symbol);
        e.setStageName(stage.name());
        e.setStatus(status);
        e.setStartedAt(LocalDateTime.now());
        e.setCompletedAt(LocalDateTime.now());
        e.setDurationMs(durationMs);
        e.setResultSummary(resultSummary);
        return e;
    }

    // ==================== startRun ====================

    @Nested
    @DisplayName("startRun")
    class StartRun {

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            service = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);
        }

        @Test
        @DisplayName("Empty watchlist completes immediately")
        void shouldCompleteImmediatelyWhenWatchlistIsEmpty() {
            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of());
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(a -> {
                JobRunEntity e = a.getArgument(0);
                capturedRunEntity = e;
                return e;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> java.util.Optional.of(capturedRunEntity));

            JobRun run = service.startRun(JobRun.TriggerType.MANUAL);

            assertThat(run.status()).isEqualTo(JobRun.Status.COMPLETED);
            assertThat(run.triggerType()).isEqualTo(JobRun.TriggerType.MANUAL);
            assertThat(run.completedAt()).isNotNull();
            assertThat(capturedRunEntity.getStatus()).isEqualTo(JobRun.Status.COMPLETED.name());
            verify(jobOrchestratorMetrics).recordRunCompleted(anyLong());
        }

        @Test
        @DisplayName("Single symbol creates run entity")
        void testStartRun_SingleSymbol_CreatesRunAndStages() {
            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("RELIANCE"));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(a -> {
                JobRunEntity e = a.getArgument(0);
                capturedRunEntity = e;
                return e;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> java.util.Optional.of(capturedRunEntity));

            JobRun run = service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(run.status()).isEqualTo(JobRun.Status.RUNNING);
            assertThat(run.triggerType()).isEqualTo(JobRun.TriggerType.SCHEDULED);
            assertThat(run.symbolsCount()).isEqualTo(1);
            verify(jobRunRepository, atLeast(1)).save(any(JobRunEntity.class));
        }

        @Test
        @DisplayName("Multiple symbols creates run for each")
        void testStartRun_MultipleSymbols_ParallelExecution() {
            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("A", "B", "C"));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(a -> {
                JobRunEntity e = a.getArgument(0);
                capturedRunEntity = e;
                return e;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenReturn(Optional.of(capturedRunEntity));

            JobRun run = service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(run.status()).isEqualTo(JobRun.Status.RUNNING);
            verify(watchlistStore, times(1)).getActiveWatchlistSymbols();
        }

        @Test
        @DisplayName("Manual trigger returns immediately when the watchlist exceeds executor capacity")
        void shouldReturnImmediatelyWhenWatchlistExceedsExecutorCapacity() throws Exception {
            List<String> symbols = IntStream.range(0, 20)
                .mapToObj(index -> "SYMBOL" + index)
                .toList();
            CountDownLatch dataFetchStarted = new CountDownLatch(1);
            CountDownLatch releaseDataFetch = new CountDownLatch(1);
            CountDownLatch runCompleted = new CountDownLatch(1);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(symbols);
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(invocation -> {
                JobRunEntity entity = invocation.getArgument(0);
                capturedRunEntity = entity;
                if (!JobRun.Status.RUNNING.name().equals(entity.getStatus())) {
                    runCompleted.countDown();
                }
                return entity;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(capturedRunEntity));
            doAnswer(invocation -> {
                dataFetchStarted.countDown();
                releaseDataFetch.await(5, TimeUnit.SECONDS);
                return null;
            }).when(dataIngestionService).processSingleStock(anyString(), any(LocalDate.class));

            ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
            try {
                Future<JobRun> response = requestExecutor.submit(
                    () -> service.startRun(JobRun.TriggerType.MANUAL));

                JobRun run = response.get(1, TimeUnit.SECONDS);

                assertThat(run.status()).isEqualTo(JobRun.Status.RUNNING);
                assertThat(run.symbolsCount()).isEqualTo(symbols.size());
                assertThat(dataFetchStarted.await(1, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseDataFetch.countDown();
                requestExecutor.shutdownNow();
                boolean requestTerminated = requestExecutor.awaitTermination(2, TimeUnit.SECONDS);
                boolean pipelineCompleted = runCompleted.await(2, TimeUnit.SECONDS);
                assertThat(requestTerminated).isTrue();
                assertThat(pipelineCompleted).isTrue();
            }
        }

        @Test
        @DisplayName("Insufficient backtest data skips paper trading without creating an error")
        void shouldSkipBacktestAndPaperTradeWhenCandleHistoryIsInsufficient() throws InterruptedException {
            AtomicReference<JobRunEntity> runState = new AtomicReference<>();
            ConcurrentHashMap<String, JobRunStageEntity> stageState = new ConcurrentHashMap<>();
            CountDownLatch runCompleted = new CountDownLatch(1);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("RELIANCE"));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(invocation -> {
                JobRunEntity entity = invocation.getArgument(0);
                runState.set(entity);
                if (!JobRun.Status.RUNNING.name().equals(entity.getStatus())) {
                    runCompleted.countDown();
                }
                return entity;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(runState.get()));
            when(jobRunRepository.incrementCompletedCount(any(UUID.class))).thenAnswer(invocation -> {
                JobRunEntity entity = runState.get();
                entity.setCompletedCount(entity.getCompletedCount() + 1);
                return 1;
            });
            when(jobRunStageRepository.save(any(JobRunStageEntity.class))).thenAnswer(invocation -> {
                JobRunStageEntity entity = invocation.getArgument(0);
                stageState.put(entity.getStageName(), entity);
                return entity;
            });
            when(jobRunStageRepository.findByRunIdAndSymbolAndStageName(
                    any(UUID.class), eq("RELIANCE"), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                    return entity == null ? List.of() : List.of(entity);
                });
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenAnswer(invocation -> List.copyOf(stageState.values()));

            when(candleStore.findTopBySymbolOrderByDateDesc("RELIANCE", 100)).thenReturn(List.of());
            when(newsIngestionService.fetchStockNews("RELIANCE")).thenReturn(List.of());
            when(sentimentService.analyzeStockSentiment(eq("RELIANCE"), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                    "RELIANCE", LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL,
                    "No news", "", 0.0));
            when(signalPipeline.generatePrimarySignal("RELIANCE")).thenReturn(Optional.empty());
            when(backtestEngine.runBacktest(eq("RELIANCE"), eq("NSE"), any(BacktestConfig.class)))
                .thenThrow(new IllegalStateException(
                    "Insufficient candle history for RELIANCE: need at least 60 candles, found 0"));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runState.get().getStatus()).isEqualTo(JobRun.Status.COMPLETED.name());
            assertThat(runState.get().getFailedCount()).isZero();
            assertThat(stageState.values())
                .noneMatch(entity -> JobRunStage.Status.ERROR.name().equals(entity.getStatus()));

            JobRunStageEntity backtestStage = stageState.get(JobRunStage.StageName.BACKTEST.name());
            assertThat(backtestStage.getStatus()).isEqualTo(JobRunStage.Status.SKIPPED.name());
            assertThat(backtestStage.getErrorMessage()).isNull();
            assertThat(backtestStage.getResultSummary()).contains("Insufficient candle history");

            JobRunStageEntity paperTradeStage = stageState.get(JobRunStage.StageName.PAPER_TRADE.name());
            assertThat(paperTradeStage.getStatus()).isEqualTo(JobRunStage.Status.SKIPPED.name());
            assertThat(paperTradeStage.getErrorMessage()).isNull();
            verifyNoInteractions(signalStore, tradingService);
        }

        @Test
        @DisplayName("Completion callback failure forces the run to FAILED instead of leaving it RUNNING")
        void shouldForceRunToFailedWhenCompletionCallbackThrows() throws InterruptedException {
            AtomicReference<JobRunEntity> runState = new AtomicReference<>();
            CountDownLatch runCompleted = new CountDownLatch(1);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("RELIANCE"));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(invocation -> {
                JobRunEntity entity = invocation.getArgument(0);
                runState.set(entity);
                if (!JobRun.Status.RUNNING.name().equals(entity.getStatus())) {
                    runCompleted.countDown();
                }
                return entity;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(runState.get()));
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenThrow(new RuntimeException("stage table unavailable"));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runState.get().getStatus()).isEqualTo(JobRun.Status.FAILED.name());
            assertThat(runState.get().getCompletedAt()).isNotNull();
            assertThat(runState.get().getErrorMessage()).contains("Run finalization failed");
        }

        @Test
        @DisplayName("Majority of symbols with an ERROR stage marks the run FAILED")
        void shouldMarkRunFailedWhenMajorityOfSymbolsHaveErrorStage() throws InterruptedException {
            AtomicReference<JobRunEntity> runState = new AtomicReference<>();
            CountDownLatch runCompleted = new CountDownLatch(1);
            UUID stageRunId = UUID.randomUUID();

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("A", "B", "C"));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(invocation -> {
                JobRunEntity entity = invocation.getArgument(0);
                runState.set(entity);
                if (!JobRun.Status.RUNNING.name().equals(entity.getStatus())) {
                    runCompleted.countDown();
                }
                return entity;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(runState.get()));
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenReturn(List.of(
                    makeStageEntity(stageRunId, "A", JobRunStage.StageName.SIGNAL,
                        JobRunStage.Status.ERROR.name(), 10L, null),
                    makeStageEntity(stageRunId, "B", JobRunStage.StageName.SIGNAL,
                        JobRunStage.Status.ERROR.name(), 10L, null),
                    makeStageEntity(stageRunId, "C", JobRunStage.StageName.SIGNAL,
                        JobRunStage.Status.COMPLETED.name(), 10L, "ok")
                ));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runState.get().getStatus()).isEqualTo(JobRun.Status.FAILED.name());
            assertThat(runState.get().getErrorMessage()).contains("2 symbols failed");
        }

        @Test
        @DisplayName("Minority of symbols with an ERROR stage keeps the run COMPLETED")
        void shouldKeepRunCompletedWhenOnlyMinorityOfSymbolsHaveErrorStage() throws InterruptedException {
            AtomicReference<JobRunEntity> runState = new AtomicReference<>();
            CountDownLatch runCompleted = new CountDownLatch(1);
            UUID stageRunId = UUID.randomUUID();

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("A", "B", "C"));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(invocation -> {
                JobRunEntity entity = invocation.getArgument(0);
                runState.set(entity);
                if (!JobRun.Status.RUNNING.name().equals(entity.getStatus())) {
                    runCompleted.countDown();
                }
                return entity;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(runState.get()));
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenReturn(List.of(
                    makeStageEntity(stageRunId, "A", JobRunStage.StageName.SIGNAL,
                        JobRunStage.Status.ERROR.name(), 10L, null),
                    makeStageEntity(stageRunId, "B", JobRunStage.StageName.SIGNAL,
                        JobRunStage.Status.COMPLETED.name(), 10L, "ok"),
                    makeStageEntity(stageRunId, "C", JobRunStage.StageName.SIGNAL,
                        JobRunStage.Status.COMPLETED.name(), 10L, "ok")
                ));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runState.get().getStatus()).isEqualTo(JobRun.Status.COMPLETED.name());
            assertThat(runState.get().getErrorMessage()).isNull();
        }
    }

    // ==================== findActiveRun ====================

    @Nested
    @DisplayName("findActiveRun")
    class FindActiveRun {

        private JobOrchestratorService svc;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            svc = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);
        }

        @Test
        @DisplayName("No RUNNING run returns empty")
        void shouldReturnEmptyWhenNoRunIsRunning() {
            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of());

            assertThat(svc.findActiveRun()).isEmpty();
        }

        @Test
        @DisplayName("RUNNING run within the staleness threshold is reported as active")
        void shouldReturnRunWhenRunningRunIsWithinStalenessThreshold() {
            // 1 symbol at maxConcurrent=3 → 1 batch → 840s * 1 * 6 = 5040s = 84 min threshold
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusMinutes(5));
            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(entity));

            assertThat(svc.findActiveRun())
                .isPresent()
                .get()
                .extracting(JobRun::runId)
                .isEqualTo(runId);
        }

        @Test
        @DisplayName("RUNNING run older than the staleness threshold is not blocking")
        void shouldReturnEmptyWhenOnlyRunningRunIsOlderThanStalenessThreshold() {
            // Threshold is 84 min (840s * 1 batch * 6); 90 min is past it.
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusMinutes(90));
            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(entity));

            assertThat(svc.findActiveRun()).isEmpty();
        }
    }

    // ==================== reapOrphanedRuns ====================

    @Nested
    @DisplayName("reapOrphanedRuns")
    class ReapOrphanedRuns {

        private JobOrchestratorService svc;

        private JobOrchestratorService newService(boolean reaperEnabled) {
            return new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, reaperEnabled);
        }

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            svc = newService(true);
        }

        @Test
        @DisplayName("Disabled reaper never touches the repositories")
        void shouldDoNothingWhenReaperIsDisabled() {
            newService(false).reapOrphanedRuns();

            verifyNoInteractions(jobRunRepository, jobRunStageRepository, jobOrchestratorMetrics);
        }

        @Test
        @DisplayName("No RUNNING runs is a no-op")
        void shouldDoNothingWhenNoRunsAreRunning() {
            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of());

            svc.reapOrphanedRuns();

            verify(jobRunRepository, never()).save(any(JobRunEntity.class));
            verify(jobRunStageRepository, never()).saveAll(any());
            verifyNoInteractions(jobOrchestratorMetrics);
        }

        @Test
        @DisplayName("Fresh RUNNING run is left untouched")
        void shouldLeaveFreshRunningRunUntouched() {
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusMinutes(5));
            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(entity));

            svc.reapOrphanedRuns();

            assertThat(entity.getStatus()).isEqualTo(JobRun.Status.RUNNING.name());
            verify(jobRunRepository, never()).save(any(JobRunEntity.class));
            verify(jobRunStageRepository, never()).saveAll(any());
            verifyNoInteractions(jobOrchestratorMetrics);
        }

        @Test
        @DisplayName("Stale RUNNING run and its RUNNING stages are force-failed")
        void shouldReapStaleRunningRunAndItsRunningStages() {
            // Threshold is 84 min (840s * 1 batch * 6); 2 h is past it.
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusHours(2));
            entity.setCompletedAt(null);
            JobRunStageEntity stage = makeStageEntity(runId, "RELIANCE",
                JobRunStage.StageName.SENTIMENT, JobRunStage.Status.RUNNING.name(), null, null);
            stage.setCompletedAt(null);

            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(entity));
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(runId))
                .thenReturn(List.of(stage));

            svc.reapOrphanedRuns();

            assertThat(entity.getStatus()).isEqualTo(JobRun.Status.FAILED.name());
            assertThat(entity.getCompletedAt()).isNotNull();
            assertThat(entity.getErrorMessage()).contains("orphaned");
            verify(jobRunRepository).save(entity);

            assertThat(stage.getStatus()).isEqualTo(JobRunStage.Status.ERROR.name());
            assertThat(stage.getCompletedAt()).isNotNull();
            assertThat(stage.getErrorMessage()).contains("orphaned");
            verify(jobRunStageRepository).saveAll(List.of(stage));

            verify(jobOrchestratorMetrics).recordRunReaped();
            verify(jobOrchestratorMetrics, never()).recordRunFailed(anyLong());
            verify(jobOrchestratorMetrics, never()).recordRunCompleted(anyLong());
        }
    }

    // ==================== cancelRun ====================

    @Nested
    @DisplayName("cancelRun")
    class CancelRun {

        private JobOrchestratorService svc;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            svc = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);
        }

        @Test
        @DisplayName("Running run marks stages cancelled")
        void testCancelRun_RunningRun_MarksStagesCancelled() {
            JobRunEntity runEntity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0);
            when(jobRunRepository.findByRunId(runId)).thenReturn(java.util.Optional.of(runEntity));

            svc.cancelRun(runId);

            verify(jobRunRepository, times(1)).save(runEntity);
        }

        @Test
        @DisplayName("Already completed run no effect")
        void testCancelRun_AlreadyCompletedRun_NoEffect() {
            JobRunEntity runEntity = makeRunEntity(JobRun.Status.COMPLETED, 1, 1, 0);
            when(jobRunRepository.findByRunId(runId)).thenReturn(java.util.Optional.of(runEntity));

            svc.cancelRun(runId);

            verify(jobRunRepository, never()).save(any());
        }
    }

    // ==================== getProgress ====================

    @Nested
    @DisplayName("getProgress")
    class GetProgress {

        private JobOrchestratorService svc;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            svc = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);
        }

        @Test
        @DisplayName("Returns structured data")
        void testGetProgress_ReturnsStructuredData() {
            JobRunEntity runEntity = makeRunEntity(JobRun.Status.COMPLETED, 3, 2, 1);
            when(jobRunRepository.findByRunId(runId)).thenReturn(java.util.Optional.of(runEntity));

            List<JobRunStageEntity> stages = List.of(
                    makeStageEntity(runId, "A", JobRunStage.StageName.DATA_FETCH, "COMPLETED", 100L, "10 candles"),
                    makeStageEntity(runId, "B", JobRunStage.StageName.DATA_FETCH, "ERROR", 50L, "error")
            );
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(runId)).thenReturn(stages);

            JobRunProgress progress = svc.getProgress(runId);

            assertThat(progress.runId()).isEqualTo(runId);
            assertThat(progress.status()).isEqualTo("COMPLETED");
            assertThat(progress.totalSymbols()).isEqualTo(3);
            assertThat(progress.completedSymbols()).isEqualTo(2);
            assertThat(progress.failedSymbols()).isEqualTo(1);
            assertThat(progress.stages()).hasSize(2);
        }
    }

    // ==================== getSummary ====================

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        private JobOrchestratorService svc;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            svc = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);
        }

        @Test
        @DisplayName("Returns aggregated stats")
        void testGetSummary_ReturnsAggregatedStats() {
            JobRunEntity runEntity = makeRunEntity(JobRun.Status.COMPLETED, 2, 2, 0);
            when(jobRunRepository.findByRunId(runId)).thenReturn(java.util.Optional.of(runEntity));

            LocalDateTime now = LocalDateTime.now();
            List<JobRunStageEntity> stages = List.of(
                    makeStageEntity(runId, "A", JobRunStage.StageName.DATA_FETCH, "COMPLETED", 100L, "ok"),
                    makeStageEntity(runId, "A", JobRunStage.StageName.SENTIMENT, "COMPLETED", 200L, "ok"),
                    makeStageEntity(runId, "B", JobRunStage.StageName.DATA_FETCH, "COMPLETED", 150L, "ok"),
                    makeStageEntity(runId, "B", JobRunStage.StageName.SENTIMENT, "COMPLETED", 250L, "ok")
            );
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(runId)).thenReturn(stages);

            JobRunSummary summary = svc.getSummary(runId);

            assertThat(summary.runId()).isEqualTo(runId);
            assertThat(summary.totalSymbols()).isEqualTo(2);
            assertThat(summary.completedSymbols()).isEqualTo(2);
            assertThat(summary.failedSymbols()).isEqualTo(0);
            assertThat(summary.totalDurationMs()).isEqualTo(700L);
            assertThat(summary.stageStats()).hasSize(2);
            assertThat(summary.symbolDetails()).hasSize(2);

            StageStats dataFetchStats = summary.stageStats().get("DATA_FETCH");
            assertThat(dataFetchStats.total()).isEqualTo(2);
            assertThat(dataFetchStats.completed()).isEqualTo(2);
            assertThat(dataFetchStats.totalDurationMs()).isEqualTo(250L);
        }
    }

    // ==================== listRuns ====================

    @Nested
    @DisplayName("listRuns")
    class ListRuns {

        private JobOrchestratorService svc;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            svc = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);
        }

        @Test
        @DisplayName("Returns sorted by startedAt desc")
        void testListRuns_ReturnsSortedByStartedAtDesc() {
            LocalDateTime t1 = LocalDateTime.of(2026, 8, 19, 10, 0);
            LocalDateTime t2 = LocalDateTime.of(2026, 8, 19, 12, 0);
            LocalDateTime t3 = LocalDateTime.of(2026, 8, 19, 14, 0);
            JobRunEntity run1 = makeRunEntity(JobRun.Status.COMPLETED, 1, 1, 0, t1);
            JobRunEntity run2 = makeRunEntity(JobRun.Status.COMPLETED, 2, 2, 0, t2);
            JobRunEntity run3 = makeRunEntity(JobRun.Status.COMPLETED, 3, 3, 0, t3);

            // Mock returns in desc order as DB would
            when(jobRunRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(run3, run2, run1));

            List<JobRun> runs = svc.listRuns();

            assertThat(runs).hasSize(3);
            assertThat(runs.get(0).startedAt()).isEqualTo(t3);
            assertThat(runs.get(1).startedAt()).isEqualTo(t2);
            assertThat(runs.get(2).startedAt()).isEqualTo(t1);
        }
    }
}