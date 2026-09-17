package com.swingtrade.api.service;

import com.swingtrade.api.service.JobOrchestratorService.JobRunProgress;
import com.swingtrade.api.service.JobOrchestratorService.JobRunSummary;
import com.swingtrade.api.service.JobOrchestratorService.StageStats;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.JobRun;
import com.swingtrade.domain.JobRunStage;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.SynthesisResult;
import com.swingtrade.domain.store.BacktestResultStore;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.LlmAnalysisResultStore;
import com.swingtrade.domain.store.SentimentStore;
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
import java.util.Set;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("JobOrchestratorService tests")
class JobOrchestratorServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String SYMBOL = "RELIANCE";
    private static final String EXCHANGE = "NSE";
    private static final String STATUS_RUNNING = "RUNNING";

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
    private SentimentGate sentimentGate;

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

    @Mock
    private TechnicalAnalysisService technicalAnalysisService;

    @Mock
    private FundamentalScorer fundamentalScorer;

    @Mock
    private CompositeAnalysisService compositeAnalysisService;

    @Mock
    private com.swingtrade.llm.service.SynthesisService synthesisService;

    @Mock
    private BacktestResultStore backtestResultStore;

    @Mock
    private LlmAnalysisResultStore llmAnalysisResultStore;

    @Mock
    private LlmAnalysisGate llmAnalysisGate;

    @Mock
    private SentimentStore sentimentStore;
    @Mock
    private com.swingtrade.domain.store.StrategyConfigStore strategyConfigStore;
    @Mock
    private com.swingtrade.domain.service.PaperPortfolioService paperPortfolioService;

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
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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
            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
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
            }).when(dataIngestionService).fetchLatestAndRepairGaps(anyString(), any(LocalDate.class));

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

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
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
                    any(UUID.class), eq(SYMBOL), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                    return entity == null ? List.of() : List.of(entity);
                });
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenAnswer(invocation -> List.copyOf(stageState.values()));

            when(candleStore.findTopBySymbolOrderByDateDesc(SYMBOL, 100)).thenReturn(List.of());
            // The backtest is deliberately insufficient, so later stages are blocked by the
            // normal failure gate in this scenario.
            lenient().when(newsIngestionService.fetchStockNews(SYMBOL)).thenReturn(List.of());
            lenient().when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                    SYMBOL, LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL,
                    "No news", "", 0.0));
            when(signalPipeline.generatePrimarySignal(SYMBOL)).thenReturn(Optional.empty());
            when(backtestEngine.runBacktest(eq(SYMBOL), eq(EXCHANGE), any(BacktestConfig.class)))
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
        @DisplayName("Kill switch: LLM_ANALYSIS is skipped entirely, not attempted, when disabled")
        void shouldSkipLlmAnalysisStageWhenKillSwitchDisabled() throws InterruptedException {
            // `service` here comes from this nested class's setUp(), which uses the
            // compatibility fixture constructor — llmAnalysisEnabled=false.
            AtomicReference<JobRunEntity> runState = new AtomicReference<>();
            ConcurrentHashMap<String, JobRunStageEntity> stageState = new ConcurrentHashMap<>();
            CountDownLatch runCompleted = new CountDownLatch(1);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
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
                    any(UUID.class), eq(SYMBOL), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                    return entity == null ? List.of() : List.of(entity);
                });
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenAnswer(invocation -> List.copyOf(stageState.values()));

            when(candleStore.findTopBySymbolOrderByDateDesc(SYMBOL, 100)).thenReturn(List.of());
            lenient().when(newsIngestionService.fetchStockNews(SYMBOL)).thenReturn(List.of());
            lenient().when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                    SYMBOL, LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL,
                    "No news", "", 0.0));
            when(signalPipeline.generatePrimarySignal(SYMBOL)).thenReturn(Optional.empty());
            when(backtestEngine.runBacktest(eq(SYMBOL), eq(EXCHANGE), any(BacktestConfig.class)))
                .thenReturn(new BacktestResult(SYMBOL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            JobRunStageEntity llmStage = stageState.get(JobRunStage.StageName.LLM_ANALYSIS.name());
            assertThat(llmStage.getStatus()).isEqualTo(JobRunStage.Status.SKIPPED.name());
            assertThat(llmStage.getResultSummary()).contains("disabled by config");
            verifyNoInteractions(compositeAnalysisService, synthesisService, llmAnalysisResultStore);
        }

        @Test
        @DisplayName("Completion callback failure forces the run to FAILED instead of leaving it RUNNING")
        void shouldForceRunToFailedWhenCompletionCallbackThrows() throws InterruptedException {
            AtomicReference<JobRunEntity> runState = new AtomicReference<>();
            CountDownLatch runCompleted = new CountDownLatch(1);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
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

        @Test
        @DisplayName("Exactly half of symbols with an ERROR stage still marks the run FAILED")
        void shouldMarkRunFailedWhenExactlyHalfOfSymbolsHaveErrorStage() throws InterruptedException {
            // Regression: the threshold used to be "> half", so a run where exactly
            // half the symbols failed every stage was reported COMPLETED.
            AtomicReference<JobRunEntity> runState = new AtomicReference<>();
            CountDownLatch runCompleted = new CountDownLatch(1);
            UUID stageRunId = UUID.randomUUID();

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("A", "B"));
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
                        JobRunStage.Status.COMPLETED.name(), 10L, "ok")
                ));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runState.get().getStatus()).isEqualTo(JobRun.Status.FAILED.name());
            assertThat(runState.get().getErrorMessage()).contains("1 symbols failed");
        }

        @Test
        @DisplayName("startRun rejects a second run while one is already active")
        void shouldRejectConcurrentStartWhenARunIsAlreadyActive() {
            JobRunEntity activeRun = new JobRunEntity();
            activeRun.setRunId(UUID.randomUUID());
            activeRun.setStatus(JobRun.Status.RUNNING.name());
            activeRun.setTriggerType(JobRun.TriggerType.MANUAL.name());
            activeRun.setStartedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            activeRun.setSymbolsCount(1);
            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(activeRun));

            assertThatThrownBy(() -> service.startRun(JobRun.TriggerType.MANUAL))
                .isInstanceOf(JobOrchestratorService.ConcurrentRunException.class)
                .hasMessageContaining(activeRun.getRunId().toString());

            verify(watchlistStore, never()).getActiveWatchlistSymbols();
        }
    }

    // ==================== LLM_ANALYSIS stage: enabled end-to-end ====================

    @Nested
    @DisplayName("LLM_ANALYSIS stage — enabled")
    class LlmAnalysisStageIntegration {

        private ConcurrentHashMap<String, JobRunStageEntity> stageState;
        private AtomicReference<JobRunEntity> runState;
        private CountDownLatch runCompleted;
        // save() mutates and re-saves the SAME entity instance per stage, so by the time
        // assertions run every captured reference reflects only its FINAL status. Recording
        // "stage::status" strings at the moment of each save() call is the only way to observe
        // the actual transition sequence (e.g. RUNNING before COMPLETED) after the fact.
        private java.util.List<String> transitions;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            service = new JobOrchestratorService(
                dataIngestionService, newsIngestionService, sentimentService,
                signalPipeline, sentimentGate, backtestEngine, tradingService,
                jobRunRepository, jobRunStageRepository, signalStore, watchlistStore,
                candleStore, jobOrchestratorMetrics, technicalAnalysisService, fundamentalScorer,
                compositeAnalysisService, synthesisService, backtestResultStore, llmAnalysisResultStore,
                llmAnalysisGate, sentimentStore, strategyConfigStore, paperPortfolioService, 3, 1000L, true, true, true);

            stageState = new ConcurrentHashMap<>();
            runState = new AtomicReference<>();
            runCompleted = new CountDownLatch(1);
            transitions = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
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
            lenient().when(jobRunRepository.incrementCompletedCount(any(UUID.class))).thenAnswer(invocation -> {
                JobRunEntity entity = runState.get();
                entity.setCompletedCount(entity.getCompletedCount() + 1);
                return 1;
            });
            when(jobRunStageRepository.save(any(JobRunStageEntity.class))).thenAnswer(invocation -> {
                JobRunStageEntity entity = invocation.getArgument(0);
                stageState.put(entity.getStageName(), entity);
                transitions.add(entity.getStageName() + "::" + entity.getStatus());
                return entity;
            });
            when(jobRunStageRepository.findByRunIdAndSymbolAndStageName(
                    any(UUID.class), eq(SYMBOL), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                    return entity == null ? List.of() : List.of(entity);
                });
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenAnswer(invocation -> List.copyOf(stageState.values()));

            when(candleStore.findTopBySymbolOrderByDateDesc(SYMBOL, 100)).thenReturn(List.of());
            lenient().when(newsIngestionService.fetchStockNews(SYMBOL)).thenReturn(List.of());
            lenient().when(signalPipeline.generatePrimarySignal(SYMBOL)).thenReturn(Optional.empty());
            lenient().when(signalStore.findUnprocessed()).thenReturn(List.of());
        }

        @Test
        @DisplayName("Stage order: LLM_ANALYSIS runs strictly between SENTIMENT and PAPER_TRADE")
        void shouldRunLlmAnalysisBetweenSentimentAndPaperTrade() throws InterruptedException {
            when(backtestEngine.runBacktest(eq(SYMBOL), eq(EXCHANGE), any(BacktestConfig.class)))
                .thenReturn(new BacktestResult(SYMBOL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
            when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                    SYMBOL, LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL, "No news", "", 0.0));
            when(technicalAnalysisService.compute(SYMBOL))
                .thenReturn(new CompositeAnalysis.TechnicalScore(0, "HOLD", 0, List.of()));
            when(fundamentalScorer.compute(SYMBOL))
                .thenReturn(new CompositeAnalysis.FundamentalScore(0, List.of()));
            when(backtestResultStore.findBySymbolAndDate(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(Optional.empty());
            when(sentimentStore.findBySymbolAndDate(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(Optional.empty());
            var composite = new CompositeAnalysis(SYMBOL, LocalDate.now(), 0, "HOLD", java.math.BigDecimal.ZERO,
                List.of(), new CompositeAnalysis.NewsScore(0, "none", List.of(), List.of(), 0),
                new CompositeAnalysis.TechnicalScore(0, "HOLD", 0, List.of()),
                new CompositeAnalysis.FundamentalScore(0, List.of()),
                new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false), "reason", null);
            when(compositeAnalysisService.analyze(eq(SYMBOL), any(), any(), any(), any())).thenReturn(composite);
            when(synthesisService.synthesize(composite)).thenReturn(new SynthesisResult(
                "n", "HOLD", 0.5, List.of(), List.of(), List.of(), true));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(stageState.get(JobRunStage.StageName.LLM_ANALYSIS.name()).getStatus())
                .isEqualTo(JobRunStage.Status.COMPLETED.name());

            String sentimentCompleted = JobRunStage.StageName.SENTIMENT.name() + "::"
                + JobRunStage.Status.COMPLETED.name();
            String llmRunning = JobRunStage.StageName.LLM_ANALYSIS.name() + "::"
                + JobRunStage.Status.RUNNING.name();
            String paperTradeRunning = JobRunStage.StageName.PAPER_TRADE.name() + "::"
                + JobRunStage.Status.RUNNING.name();

            int sentimentIndex = transitions.indexOf(sentimentCompleted);
            int llmIndex = transitions.indexOf(llmRunning);
            int paperTradeIndex = transitions.indexOf(paperTradeRunning);

            assertThat(sentimentIndex).as("SENTIMENT completed transition recorded").isNotEqualTo(-1);
            assertThat(llmIndex).as("LLM_ANALYSIS running transition recorded").isNotEqualTo(-1);
            assertThat(paperTradeIndex).as("PAPER_TRADE running transition recorded").isNotEqualTo(-1);
            assertThat(sentimentIndex).isLessThan(llmIndex);
            assertThat(llmIndex).isLessThan(paperTradeIndex);
        }

        @Test
        @DisplayName("Skip-cascade: SENTIMENT error skips both LLM_ANALYSIS and PAPER_TRADE")
        void shouldSkipLlmAnalysisAndPaperTradeWhenSentimentErrors() throws InterruptedException {
            when(backtestEngine.runBacktest(eq(SYMBOL), eq(EXCHANGE), any(BacktestConfig.class)))
                .thenReturn(new BacktestResult(SYMBOL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
            when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenThrow(new RuntimeException("sentiment provider unavailable"));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(stageState.get(JobRunStage.StageName.SENTIMENT.name()).getStatus())
                .isEqualTo(JobRunStage.Status.ERROR.name());
            assertThat(stageState.get(JobRunStage.StageName.LLM_ANALYSIS.name()).getStatus())
                .isEqualTo(JobRunStage.Status.SKIPPED.name());
            assertThat(stageState.get(JobRunStage.StageName.PAPER_TRADE.name()).getStatus())
                .isEqualTo(JobRunStage.Status.SKIPPED.name());
            verifyNoInteractions(compositeAnalysisService, synthesisService, llmAnalysisResultStore);
        }
    }

    // ==================== stagePaperTrade ordering ====================

    @Nested
    @DisplayName("stagePaperTrade — queue-before-mark ordering")
    class StagePaperTrade {

        private AtomicReference<JobRunEntity> runState;
        private ConcurrentHashMap<String, JobRunStageEntity> stageState;
        private CountDownLatch runCompleted;

        private final com.swingtrade.domain.Signal buySignal = new com.swingtrade.domain.Signal(
                42L, SYMBOL, LocalDate.now(IST), com.swingtrade.domain.Signal.SignalType.BUY,
                java.math.BigDecimal.valueOf(0.8), "strong setup",
                java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(95),
                java.math.BigDecimal.valueOf(110), java.math.BigDecimal.valueOf(2),
                "{}", LocalDate.now(IST), null, null);

        private final com.swingtrade.domain.OhlcvCandle latestCandle = com.swingtrade.domain.OhlcvCandle.of(
                SYMBOL, LocalDate.now(IST), java.math.BigDecimal.valueOf(99),
                java.math.BigDecimal.valueOf(101), java.math.BigDecimal.valueOf(98),
                java.math.BigDecimal.valueOf(100), 1000L);

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            runState = new AtomicReference<>();
            stageState = new ConcurrentHashMap<>();
            runCompleted = new CountDownLatch(1);

            service = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
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
                    any(UUID.class), eq(SYMBOL), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                    return entity == null ? List.of() : List.of(entity);
                });
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenAnswer(invocation -> List.copyOf(stageState.values()));

            when(candleStore.findTopBySymbolOrderByDateDesc(SYMBOL, 100)).thenReturn(List.of(latestCandle));
            // generatePrimarySignal() returns empty below (this run produces no signal of its
            // own - buySignal below simulates a stale unprocessed one from an earlier run).
            lenient().when(newsIngestionService.fetchStockNews(SYMBOL)).thenReturn(List.of());
            lenient().when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                    SYMBOL, LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL,
                    "No news", "", 0.0));
            when(signalPipeline.generatePrimarySignal(SYMBOL)).thenReturn(Optional.empty());
            when(backtestEngine.runBacktest(eq(SYMBOL), eq(EXCHANGE), any(BacktestConfig.class)))
                .thenReturn(new BacktestResult(SYMBOL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
            // buySignal simulates a stale unprocessed BUY left over from an earlier run; its
            // paper-trade sentiment lookup must use whatever verdict was persisted for its date.
            when(sentimentGate.evaluatePersisted(eq(SYMBOL), eq(buySignal.date())))
                .thenReturn(SentimentGate.SentimentVerdict.allow());

            when(signalStore.findUnprocessed()).thenReturn(List.of(buySignal));
            when(candleStore.findLatestBySymbol(SYMBOL)).thenReturn(Optional.of(latestCandle));
        }

        @Test
        @DisplayName("Signal is queued before it is marked processed")
        void shouldQueueSignalBeforeMarkingProcessed() throws InterruptedException {
            when(tradingService.queueSignal(eq(buySignal), eq(latestCandle.close())))
                .thenReturn(new com.swingtrade.domain.Order());

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            var inOrder = inOrder(signalStore, tradingService);
            inOrder.verify(tradingService).queueSignal(eq(buySignal), eq(latestCandle.close()));
            inOrder.verify(signalStore).markProcessed(42L);

            JobRunStageEntity paperTradeStage = stageState.get(JobRunStage.StageName.PAPER_TRADE.name());
            assertThat(paperTradeStage.getStatus()).isEqualTo(JobRunStage.Status.COMPLETED.name());
            assertThat(paperTradeStage.getResultSummary()).contains("1 trade(s) executed");
        }

        @Test
        @DisplayName("When marking processed fails, the queued trade is retried idempotently")
        void shouldLeaveQueuedTradeRetryableWhenMarkProcessedFails() throws InterruptedException {
            when(tradingService.queueSignal(eq(buySignal), eq(latestCandle.close())))
                .thenReturn(new com.swingtrade.domain.Order());
            doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    com.swingtrade.data.entity.SignalEntity.class, 42L))
                .when(signalStore).markProcessed(42L);

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            verify(signalStore).markProcessed(42L);
            verify(tradingService).queueSignal(eq(buySignal), eq(latestCandle.close()));

            JobRunStageEntity paperTradeStage = stageState.get(JobRunStage.StageName.PAPER_TRADE.name());
            assertThat(paperTradeStage.getStatus()).isEqualTo(JobRunStage.Status.COMPLETED.name());
            assertThat(paperTradeStage.getResultSummary()).contains("0 trade(s) executed");
        }

        @Test
        @DisplayName("When queueing fails, the signal remains retryable")
        void shouldLeaveSignalRetryableWhenQueueFails() throws InterruptedException {
            when(tradingService.queueSignal(eq(buySignal), eq(latestCandle.close())))
                .thenThrow(new IllegalStateException("broker rejected order"));

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            verify(signalStore, never()).markProcessed(42L);
            verify(tradingService).queueSignal(eq(buySignal), eq(latestCandle.close()));

            JobRunStageEntity paperTradeStage = stageState.get(JobRunStage.StageName.PAPER_TRADE.name());
            assertThat(paperTradeStage.getStatus()).isEqualTo(JobRunStage.Status.COMPLETED.name());
            assertThat(paperTradeStage.getResultSummary())
                .contains("0 trade(s) executed")
                .contains("1 failed to queue");
        }
    }

    // ==================== stagePaperTrade — per-variant routing (plan §7.2) ====================

    @Nested
    @DisplayName("stagePaperTrade — variant routing gap fix")
    class StagePaperTradeVariantRouting {

        private AtomicReference<JobRunEntity> runState;
        private ConcurrentHashMap<String, JobRunStageEntity> stageState;
        private CountDownLatch runCompleted;

        private static final String CHAMPION_VARIANT = "BREAKOUT_STRICT";
        private static final String SHADOW_VARIANT = "PULLBACK_B";

        private final com.swingtrade.domain.Signal championSignal = new com.swingtrade.domain.Signal(
                42L, SYMBOL, LocalDate.now(IST), com.swingtrade.domain.Signal.SignalType.BUY,
                java.math.BigDecimal.valueOf(0.8), "strong setup",
                java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(95),
                java.math.BigDecimal.valueOf(110), java.math.BigDecimal.valueOf(2),
                "{}", LocalDate.now(IST), null, null);

        private final com.swingtrade.domain.Signal shadowSignal = new com.swingtrade.domain.Signal(
                43L, SYMBOL, LocalDate.now(IST), com.swingtrade.domain.Signal.SignalType.BUY,
                java.math.BigDecimal.valueOf(0.7), "shadow setup",
                java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(95),
                java.math.BigDecimal.valueOf(110), java.math.BigDecimal.valueOf(2),
                "{}", LocalDate.now(IST), null, null);

        private final com.swingtrade.domain.OhlcvCandle latestCandle = com.swingtrade.domain.OhlcvCandle.of(
                SYMBOL, LocalDate.now(IST), java.math.BigDecimal.valueOf(99),
                java.math.BigDecimal.valueOf(101), java.math.BigDecimal.valueOf(98),
                java.math.BigDecimal.valueOf(100), 1000L);

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            runState = new AtomicReference<>();
            stageState = new ConcurrentHashMap<>();
            runCompleted = new CountDownLatch(1);

            service = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore, watchlistStore,
                    candleStore, jobOrchestratorMetrics, technicalAnalysisService, fundamentalScorer,
                    compositeAnalysisService, synthesisService, backtestResultStore, llmAnalysisResultStore,
                    llmAnalysisGate, sentimentStore, strategyConfigStore, paperPortfolioService,
                    3, 1000L, true, false, true);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
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
                    any(UUID.class), eq(SYMBOL), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                    return entity == null ? List.of() : List.of(entity);
                });
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenAnswer(invocation -> List.copyOf(stageState.values()));

            when(candleStore.findTopBySymbolOrderByDateDesc(SYMBOL, 100)).thenReturn(List.of(latestCandle));
            lenient().when(newsIngestionService.fetchStockNews(SYMBOL)).thenReturn(List.of());
            lenient().when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                    SYMBOL, LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL,
                    "No news", "", 0.0));
            when(signalPipeline.generatePrimarySignal(SYMBOL)).thenReturn(Optional.empty());
            when(backtestEngine.runBacktest(eq(SYMBOL), eq(EXCHANGE), any(BacktestConfig.class)))
                .thenReturn(new BacktestResult(SYMBOL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
            lenient().when(sentimentGate.evaluatePersisted(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(SentimentGate.SentimentVerdict.allow());
            when(candleStore.findLatestBySymbol(SYMBOL)).thenReturn(Optional.of(latestCandle));

            com.swingtrade.domain.StrategyConfig champion = new com.swingtrade.domain.StrategyConfig(
                1L, CHAMPION_VARIANT, 1, "BREAKOUT", java.util.Map.of(), java.util.Map.of(), "hash",
                com.swingtrade.domain.StrategyMode.CHAMPION, new java.math.BigDecimal("500000"),
                true, null, null, LocalDateTime.now());
            when(strategyConfigStore.findCurrentChampion()).thenReturn(Optional.of(champion));
        }

        @Test
        @DisplayName("A SHADOW variant's BUY signal is never executed against the champion/default portfolio")
        void shadowVariantSignalIsQuarantinedNotExecuted() throws InterruptedException {
            // Only the CHAMPION variant's own signal is returned by the strategy-scoped query;
            // the shadow's signal exists in the DB (per §7.1's fan-out) but must never reach
            // tradingService.queueSignal.
            when(signalStore.findUnprocessedByStrategy(CHAMPION_VARIANT)).thenReturn(List.of(championSignal));
            when(signalStore.markProcessedExcludingStrategy(SYMBOL, CHAMPION_VARIANT)).thenReturn(1);
            when(tradingService.queueSignal(eq(championSignal), eq(latestCandle.close())))
                .thenReturn(new com.swingtrade.domain.Order());

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            // The champion signal is the only one ever passed to the shared paper engine.
            verify(tradingService).queueSignal(eq(championSignal), eq(latestCandle.close()));
            verify(tradingService, never()).queueSignal(eq(shadowSignal), any());
            // The shadow variant's own unprocessed signal was quarantined (marked processed)
            // rather than executed.
            verify(signalStore).markProcessedExcludingStrategy(SYMBOL, CHAMPION_VARIANT);
            // findUnprocessed() (the old symbol-only, strategy-blind query) must never be used
            // once a CHAMPION variant is configured - that was the routing gap.
            verify(signalStore, never()).findUnprocessed();
        }

        @Test
        @DisplayName("Daily loss breach on the champion portfolio blocks new BUY entries")
        void killSwitchBlocksNewEntriesOnBreach() throws InterruptedException {
            when(signalStore.findUnprocessedByStrategy(CHAMPION_VARIANT)).thenReturn(List.of(championSignal));
            when(signalStore.markProcessedExcludingStrategy(SYMBOL, CHAMPION_VARIANT)).thenReturn(0);
            when(paperPortfolioService.isDailyLossBreached(CHAMPION_VARIANT)).thenReturn(true);

            service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(runCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            verify(tradingService, never()).queueSignal(any(), any());
            verify(signalStore).markProcessed(42L);

            JobRunStageEntity paperTradeStage = stageState.get(JobRunStage.StageName.PAPER_TRADE.name());
            assertThat(paperTradeStage.getResultSummary()).contains("blocked by daily loss breaker");
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
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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
            // Threshold is 10 hours (6000s * 1 batch * 6); 12 hours is past it.
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusHours(12));
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
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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
            // Threshold is 10 hours (6000s * 1 batch * 6); 12 h is past it.
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusHours(12));
            entity.setCompletedAt(null);
            JobRunStageEntity stage = makeStageEntity(runId, SYMBOL,
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

    // ==================== reapAllRunningRunsOnStartup ====================

    @Nested
    @DisplayName("reapAllRunningRunsOnStartup")
    class ReapAllRunningRunsOnStartup {

        private JobOrchestratorService svc;

        private JobOrchestratorService newService(boolean reaperEnabled) {
            return new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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
            newService(false).reapAllRunningRunsOnStartup();

            verifyNoInteractions(jobRunRepository, jobRunStageRepository, jobOrchestratorMetrics);
        }

        @Test
        @DisplayName("No RUNNING runs is a no-op")
        void shouldDoNothingWhenNoRunsAreRunning() {
            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of());

            svc.reapAllRunningRunsOnStartup();

            verify(jobRunRepository, never()).save(any(JobRunEntity.class));
            verify(jobRunStageRepository, never()).saveAll(any());
            verifyNoInteractions(jobOrchestratorMetrics);
        }

        @Test
        @DisplayName("A RUNNING run that is well within the staleness threshold is still reaped immediately")
        void shouldReapFreshRunningRunImmediatelyOnStartup() {
            // Only 5 minutes old — far short of the multi-hour staleness threshold used by
            // reapOrphanedRuns(). The startup hook must not wait for that: a RUNNING row at
            // boot time is always orphaned since its executing thread belonged to the
            // previous JVM.
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusMinutes(5));
            entity.setCompletedAt(null);

            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(entity));
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(runId))
                .thenReturn(List.of());

            svc.reapAllRunningRunsOnStartup();

            assertThat(entity.getStatus()).isEqualTo(JobRun.Status.FAILED.name());
            assertThat(entity.getCompletedAt()).isNotNull();
            assertThat(entity.getErrorMessage()).contains("Orphaned by application restart");
            verify(jobRunRepository).save(entity);
            verify(jobOrchestratorMetrics).recordRunReaped();
        }

        @Test
        @DisplayName("RUNNING stages belonging to the orphaned run are force-failed too")
        void shouldForceFailRunningStagesOfOrphanedRun() {
            JobRunEntity entity = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusMinutes(1));
            entity.setCompletedAt(null);
            JobRunStageEntity stage = makeStageEntity(runId, SYMBOL,
                JobRunStage.StageName.SENTIMENT, JobRunStage.Status.RUNNING.name(), null, null);
            stage.setCompletedAt(null);

            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(entity));
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(runId))
                .thenReturn(List.of(stage));

            svc.reapAllRunningRunsOnStartup();

            assertThat(stage.getStatus()).isEqualTo(JobRunStage.Status.ERROR.name());
            assertThat(stage.getCompletedAt()).isNotNull();
            assertThat(stage.getErrorMessage()).contains("orphaned");
            verify(jobRunStageRepository).saveAll(List.of(stage));
        }

        @Test
        @DisplayName("Multiple RUNNING runs are all reaped")
        void shouldReapMultipleRunningRuns() {
            JobRunEntity first = makeRunEntity(JobRun.Status.RUNNING, 1, 0, 0,
                LocalDateTime.now(IST).minusMinutes(1));
            first.setCompletedAt(null);
            JobRunEntity second = makeRunEntity(JobRun.Status.RUNNING, 2, 0, 0,
                LocalDateTime.now(IST).minusMinutes(2));
            second.setCompletedAt(null);

            when(jobRunRepository.findByStatusOrderByStartedAtDesc(JobRun.Status.RUNNING.name()))
                .thenReturn(List.of(first, second));
            when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
                .thenReturn(List.of());

            svc.reapAllRunningRunsOnStartup();

            assertThat(first.getStatus()).isEqualTo(JobRun.Status.FAILED.name());
            assertThat(second.getStatus()).isEqualTo(JobRun.Status.FAILED.name());
            verify(jobRunRepository).save(first);
            verify(jobRunRepository).save(second);
            verify(jobOrchestratorMetrics, times(2)).recordRunReaped();
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
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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

    // ==================== cancelRun — interrupts in-flight work ====================

    @Nested
    @DisplayName("cancelRun — interrupts in-flight work")
    class CancelRunInterruptsInFlightWork {

        private AtomicReference<JobRunEntity> runState;
        private ConcurrentHashMap<String, JobRunStageEntity> stageState;
        private CountDownLatch sentimentStarted;
        private CountDownLatch sentimentInterrupted;
        private CountDownLatch sentimentStageFinalized;
        private CountDownLatch symbolProcessingFinished;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            runState = new AtomicReference<>();
            stageState = new ConcurrentHashMap<>();
            sentimentStarted = new CountDownLatch(1);
            sentimentInterrupted = new CountDownLatch(1);
            sentimentStageFinalized = new CountDownLatch(1);
            symbolProcessingFinished = new CountDownLatch(1);

            service = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(invocation -> {
                JobRunEntity entity = invocation.getArgument(0);
                runState.set(entity);
                return entity;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(runState.get()));
            when(jobRunRepository.incrementCompletedCount(any(UUID.class))).thenAnswer(invocation -> {
                JobRunEntity entity = runState.get();
                entity.setCompletedCount(entity.getCompletedCount() + 1);
                symbolProcessingFinished.countDown();
                return 1;
            });
            // lenient: shouldTolerateRaceBetweenCancelRunWriteAndExecuteStageWrite() overrides
            // this with a more specific stub (adds a simulated optimistic-lock failure), which
            // makes this default stub unused for that one test.
            lenient().when(jobRunStageRepository.save(any(JobRunStageEntity.class))).thenAnswer(invocation -> {
                JobRunStageEntity entity = invocation.getArgument(0);
                stageState.put(entity.getStageName(), entity);
                if (JobRunStage.StageName.SENTIMENT.name().equals(entity.getStageName())
                        && !STATUS_RUNNING.equals(entity.getStatus())) {
                    sentimentStageFinalized.countDown();
                }
                return entity;
            });
            lenient().when(jobRunStageRepository.saveAll(any())).thenAnswer(invocation -> {
                List<JobRunStageEntity> entities = invocation.getArgument(0);
                for (JobRunStageEntity entity : entities) {
                    stageState.put(entity.getStageName(), entity);
                    if (JobRunStage.StageName.SENTIMENT.name().equals(entity.getStageName())
                            && !STATUS_RUNNING.equals(entity.getStatus())) {
                        sentimentStageFinalized.countDown();
                    }
                }
                return entities;
            });
            when(jobRunStageRepository.findByRunIdAndSymbolAndStageName(
                    any(UUID.class), eq(SYMBOL), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                    return entity == null ? List.of() : List.of(entity);
                });
            lenient().when(jobRunStageRepository.findByRunIdAndStageName(any(UUID.class), anyString()))
                .thenAnswer(invocation -> {
                    JobRunStageEntity entity = stageState.get(invocation.getArgument(1));
                    return entity == null ? List.of() : List.of(entity);
                });

            when(candleStore.findTopBySymbolOrderByDateDesc(SYMBOL, 100)).thenReturn(List.of());
            when(newsIngestionService.fetchStockNews(SYMBOL)).thenReturn(List.of());
            // BACKTEST now runs before NEWS/SENTIMENT - must not error, or priorStageBlocked
            // would skip the rest of the stage loop before SENTIMENT ever starts.
            when(backtestEngine.runBacktest(eq(SYMBOL), eq(EXCHANGE), any(BacktestConfig.class)))
                .thenReturn(new BacktestResult(SYMBOL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
            // The cancellation tests use a BUY so SENTIMENT actually blocks and gives
            // cancelRun() something in-flight to interrupt.
            when(signalPipeline.generatePrimarySignal(SYMBOL)).thenReturn(Optional.of(
                new com.swingtrade.domain.Signal(
                    42L, SYMBOL, LocalDate.now(IST), com.swingtrade.domain.Signal.SignalType.BUY,
                    java.math.BigDecimal.valueOf(0.8), "strong setup",
                    java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(95),
                    java.math.BigDecimal.valueOf(110), java.math.BigDecimal.valueOf(2),
                    "{}", LocalDate.now(IST), null, null)));
        }

        @Test
        @DisplayName("News and sentiment run for a non-BUY signal")
        void shouldRunNewsAndSentimentForHoldSignal() throws InterruptedException {
            when(signalPipeline.generatePrimarySignal(SYMBOL)).thenReturn(Optional.of(
                new com.swingtrade.domain.Signal(
                    42L, SYMBOL, LocalDate.now(IST), com.swingtrade.domain.Signal.SignalType.HOLD,
                    java.math.BigDecimal.ONE, "waiting for confirmation",
                    java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(95),
                    java.math.BigDecimal.valueOf(110), java.math.BigDecimal.valueOf(2),
                    "{}", LocalDate.now(IST), null, null)));
            when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                    SYMBOL, LocalDate.now(IST), SentimentResult.SentimentScore.NEUTRAL,
                    "No news", "", 0.0));

            JobRun startedRun = service.startRun(JobRun.TriggerType.MANUAL);

            assertThat(symbolProcessingFinished.await(5, TimeUnit.SECONDS)).isTrue();
            verify(newsIngestionService).fetchStockNews(SYMBOL);
            verify(sentimentService).analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class));
            assertThat(stageState.get(JobRunStage.StageName.NEWS.name()).getStatus())
                .isEqualTo(JobRunStage.Status.COMPLETED.name());
            assertThat(stageState.get(JobRunStage.StageName.SENTIMENT.name()).getStatus())
                .isEqualTo(JobRunStage.Status.COMPLETED.name());
            assertThat(startedRun.status()).isEqualTo(JobRun.Status.RUNNING);
        }

        @Test
        @DisplayName("Cancelling a run interrupts the blocking SENTIMENT-stage thread instead of only flipping DB status")
        void shouldInterruptInFlightSentimentStageOnCancel() throws InterruptedException {
            when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    sentimentStarted.countDown();
                    try {
                        Thread.sleep(30_000);
                    } catch (InterruptedException e) {
                        sentimentInterrupted.countDown();
                        throw new RuntimeException("sentiment call interrupted", e);
                    }
                    throw new IllegalStateException("should have been interrupted before reaching here");
                });

            JobRun startedRun = service.startRun(JobRun.TriggerType.MANUAL);

            assertThat(sentimentStarted.await(2, TimeUnit.SECONDS))
                .as("SENTIMENT stage should have started before we cancel")
                .isTrue();

            service.cancelRun(startedRun.runId());

            // 15s, not 5s: this awaits a REAL Thread.interrupt() delivered through a REAL
            // ExecutorService (Future#cancel(true)) unwinding a REAL Thread.sleep(30_000) — actual
            // OS thread scheduling, not a mocked/virtual clock. 5s was measured flaky under
            // real (non-CI-isolated) system load on this Pi even with the interrupt logic
            // working correctly; this only widens the test's patience, not what it asserts.
            assertThat(sentimentInterrupted.await(15, TimeUnit.SECONDS))
                .as("cancelRun() must interrupt the thread blocked in the SENTIMENT stage call, "
                    + "not just update DB status")
                .isTrue();
            assertThat(sentimentStageFinalized.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(symbolProcessingFinished.await(15, TimeUnit.SECONDS)).isTrue();

            JobRunStageEntity sentimentStage = stageState.get(JobRunStage.StageName.SENTIMENT.name());
            assertThat(sentimentStage.getStatus())
                .as("interrupted stage must land in a clean terminal state, not stay RUNNING")
                .isEqualTo(JobRunStage.Status.CANCELLED.name());
            assertThat(sentimentStage.getErrorMessage()).containsIgnoringCase("cancel");

            assertThat(runState.get().getStatus())
                .as("the run must stay CANCELLED — the async completion callback must not "
                    + "overwrite it back to COMPLETED/FAILED once the interrupted stage unwinds")
                .isEqualTo(JobRun.Status.CANCELLED.name());

            verify(jobOrchestratorMetrics).recordRunCancelled();
        }

        @Test
        @DisplayName("A race between cancelRun()'s belt-and-suspenders write and executeStage()'s own "
            + "cancellation write does not corrupt the stage's terminal status or abort the remaining stage loop")
        void shouldTolerateRaceBetweenCancelRunWriteAndExecuteStageWrite() throws InterruptedException {
            when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    sentimentStarted.countDown();
                    try {
                        Thread.sleep(30_000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("sentiment call interrupted", e);
                    }
                    throw new IllegalStateException("should have been interrupted before reaching here");
                });
            // Simulate real Hibernate/Spring Data optimistic-locking behavior discovered during live
            // verification against the real Postgres/Hibernate stack: cancelRun()'s belt-and-suspenders
            // saveAll() (a different repository method, unaffected by this stub) wins the race and writes
            // SENTIMENT -> CANCELLED first. executeStage()'s own save() call for the exact same terminal
            // write then loses the race, exactly as production logged:
            // "Row was already updated or deleted by another transaction".
            when(jobRunStageRepository.save(any(JobRunStageEntity.class))).thenAnswer(invocation -> {
                JobRunStageEntity entity = invocation.getArgument(0);
                if (JobRunStage.StageName.SENTIMENT.name().equals(entity.getStageName())
                        && JobRunStage.Status.CANCELLED.name().equals(entity.getStatus())) {
                    throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        JobRunStageEntity.class, 1L);
                }
                stageState.put(entity.getStageName(), entity);
                if (JobRunStage.StageName.SENTIMENT.name().equals(entity.getStageName())
                        && !STATUS_RUNNING.equals(entity.getStatus())) {
                    sentimentStageFinalized.countDown();
                }
                return entity;
            });

            JobRun startedRun = service.startRun(JobRun.TriggerType.MANUAL);

            assertThat(sentimentStarted.await(2, TimeUnit.SECONDS)).isTrue();

            service.cancelRun(startedRun.runId());

            // 15s, not 5s — same reasoning as shouldInterruptInFlightSentimentStageOnCancel: this
            // depends on a REAL Thread.interrupt() unwinding a REAL Thread.sleep(30_000), which is
            // sensitive to actual OS thread scheduling and was measured flaky at 5s under real
            // system load even when the underlying race-tolerance logic was correct.
            assertThat(symbolProcessingFinished.await(15, TimeUnit.SECONDS))
                .as("processSymbol() must still finish (recordCompletion() must still be reached) even "
                    + "when executeStage()'s own status write loses a race with cancelRun()'s — the lost "
                    + "race must not silently abort the rest of the stage loop for this symbol")
                .isTrue();

            JobRunStageEntity sentimentStage = stageState.get(JobRunStage.StageName.SENTIMENT.name());
            assertThat(sentimentStage.getStatus())
                .as("cancelRun()'s belt-and-suspenders write already achieved the correct terminal "
                    + "CANCELLED state for this row — a losing race on executeStage()'s own (redundant) "
                    + "write must not revert it to ERROR")
                .isEqualTo(JobRunStage.Status.CANCELLED.name());

            // SIGNAL now runs before SENTIMENT in the pipeline order (it must complete with a
            // BUY before NEWS/SENTIMENT are even scheduled), so it is no longer a "later stage"
            // relative to SENTIMENT — only PAPER_TRADE still follows it.
            JobRunStageEntity paperTradeStage = stageState.get(JobRunStage.StageName.PAPER_TRADE.name());
            assertThat(paperTradeStage.getStatus())
                .as("later stages must still be visited and reach a terminal CANCELLED state, not be "
                    + "left dangling at PENDING forever")
                .isEqualTo(JobRunStage.Status.CANCELLED.name());

            assertThat(runState.get().getStatus()).isEqualTo(JobRun.Status.CANCELLED.name());
        }
    }

    // ==================== cancelRun — stops queued symbols from starting new work ====================

    @Nested
    @DisplayName("cancelRun — stops queued symbols from starting new work")
    class CancelRunStopsQueuedSymbols {

        private AtomicReference<JobRunEntity> runState;
        private ConcurrentHashMap<String, JobRunStageEntity> stageState; // key: symbol + "::" + stageName
        private CountDownLatch anySentimentStarted;
        private final Set<String> symbolsThatStartedSentiment = ConcurrentHashMap.newKeySet();
        private CountDownLatch bothSymbolsFinished;

        @BeforeEach
        void setUp() {
            runId = UUID.randomUUID();
            runState = new AtomicReference<>();
            stageState = new ConcurrentHashMap<>();
            anySentimentStarted = new CountDownLatch(1);
            bothSymbolsFinished = new CountDownLatch(2);

            // maxConcurrent = 1: symbol B cannot start until symbol A releases its permit.
            // pollIntervalMs = 50 so B's acquireSlot() polling doesn't slow the test down.
            service = new JobOrchestratorService(
                    dataIngestionService, newsIngestionService, sentimentService,
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore, jobOrchestratorMetrics, 1, 50L, true);

            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("A", "B"));
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(invocation -> {
                JobRunEntity entity = invocation.getArgument(0);
                runState.set(entity);
                return entity;
            });
            when(jobRunRepository.findByRunId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(runState.get()));
            when(jobRunRepository.incrementCompletedCount(any(UUID.class))).thenAnswer(invocation -> {
                JobRunEntity entity = runState.get();
                entity.setCompletedCount(entity.getCompletedCount() + 1);
                bothSymbolsFinished.countDown();
                return 1;
            });
            when(jobRunStageRepository.save(any(JobRunStageEntity.class))).thenAnswer(invocation -> {
                JobRunStageEntity entity = invocation.getArgument(0);
                stageState.put(entity.getSymbol() + "::" + entity.getStageName(), entity);
                return entity;
            });
            when(jobRunStageRepository.saveAll(any())).thenAnswer(invocation -> {
                List<JobRunStageEntity> entities = invocation.getArgument(0);
                for (JobRunStageEntity entity : entities) {
                    stageState.put(entity.getSymbol() + "::" + entity.getStageName(), entity);
                }
                return entities;
            });
            when(jobRunStageRepository.findByRunIdAndSymbolAndStageName(
                    any(UUID.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(1) + "::" + invocation.getArgument(2);
                    JobRunStageEntity entity = stageState.get(key);
                    return entity == null ? List.of() : List.of(entity);
                });
            when(jobRunStageRepository.findByRunIdAndStageName(any(UUID.class), anyString()))
                .thenAnswer(invocation -> {
                    String stageName = invocation.getArgument(1);
                    return stageState.values().stream()
                        .filter(e -> stageName.equals(e.getStageName()))
                        .toList();
                });

            when(candleStore.findTopBySymbolOrderByDateDesc(anyString(), eq(100))).thenReturn(List.of());
            when(newsIngestionService.fetchStockNews(anyString())).thenReturn(List.of());
            // BACKTEST now runs before NEWS/SENTIMENT - must not error, or priorStageBlocked
            // would skip the rest of the stage loop before SENTIMENT ever starts.
            when(backtestEngine.runBacktest(anyString(), eq(EXCHANGE), any(BacktestConfig.class)))
                .thenReturn(new BacktestResult("X", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
            // Both symbols use BUY here so SENTIMENT actually starts for the active symbol and
            // B is left genuinely queued behind it.
            when(signalPipeline.generatePrimarySignal(anyString())).thenAnswer(invocation -> {
                String symbol = invocation.getArgument(0);
                return Optional.of(new com.swingtrade.domain.Signal(
                    42L, symbol, LocalDate.now(IST), com.swingtrade.domain.Signal.SignalType.BUY,
                    java.math.BigDecimal.valueOf(0.8), "strong setup",
                    java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(95),
                    java.math.BigDecimal.valueOf(110), java.math.BigDecimal.valueOf(2),
                    "{}", LocalDate.now(IST), null, null));
            });
            when(sentimentService.analyzeStockSentiment(anyString(), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    String symbol = invocation.getArgument(0);
                    symbolsThatStartedSentiment.add(symbol);
                    anySentimentStarted.countDown();
                    try {
                        Thread.sleep(30_000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("interrupted", e);
                    }
                    throw new IllegalStateException("should have been interrupted before reaching here");
                });
        }

        @Test
        @DisplayName("A symbol still queued behind maxConcurrent is fully cancelled without doing any real work")
        void shouldCancelQueuedSymbolWithoutDoingRealWork() throws InterruptedException {
            JobRun startedRun = service.startRun(JobRun.TriggerType.MANUAL);

            assertThat(anySentimentStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(symbolsThatStartedSentiment).hasSize(1);
            String activeSymbol = symbolsThatStartedSentiment.iterator().next();
            String queuedSymbol = activeSymbol.equals("A") ? "B" : "A";

            service.cancelRun(startedRun.runId());

            assertThat(bothSymbolsFinished.await(10, TimeUnit.SECONDS))
                .as("both symbols' processSymbol() must finish (quickly) after cancel — the "
                    + "queued one should never do real work, and the active one should unwind "
                    + "promptly once interrupted")
                .isTrue();

            verify(dataIngestionService, never())
                .fetchLatestAndRepairGaps(eq(queuedSymbol), any(LocalDate.class));

            JobRunStageEntity queuedDataFetch =
                stageState.get(queuedSymbol + "::" + JobRunStage.StageName.DATA_FETCH.name());
            assertThat(queuedDataFetch.getStatus()).isEqualTo(JobRunStage.Status.CANCELLED.name());
            assertThat(queuedDataFetch.getErrorMessage()).containsIgnoringCase("cancel");

            JobRunStageEntity queuedPaperTrade =
                stageState.get(queuedSymbol + "::" + JobRunStage.StageName.PAPER_TRADE.name());
            assertThat(queuedPaperTrade.getStatus()).isEqualTo(JobRunStage.Status.CANCELLED.name());

            assertThat(runState.get().getStatus()).isEqualTo(JobRun.Status.CANCELLED.name());
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
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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
                    signalPipeline, sentimentGate, backtestEngine, tradingService,
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
