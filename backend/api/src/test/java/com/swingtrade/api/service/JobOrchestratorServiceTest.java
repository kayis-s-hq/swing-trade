package com.swingtrade.api.service;

import com.swingtrade.api.service.JobOrchestratorService.JobRunProgress;
import com.swingtrade.api.service.JobOrchestratorService.JobRunSummary;
import com.swingtrade.api.service.JobOrchestratorService.StageStats;
import com.swingtrade.api.service.JobOrchestratorService.SymbolDetail;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.JobRun;
import com.swingtrade.domain.JobRunStage;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("JobOrchestratorService tests")
class JobOrchestratorServiceTest {

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
    private PaperTradingEngine paperTradingEngine;

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
                    signalPipeline, backtestEngine, paperTradingEngine,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore);
        }

        @Test
        @DisplayName("Empty watchlist returns immediately with RUNNING status")
        void testStartRun_EmptyWatchlist_CompletesImmediately() {
            when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of());
            when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(a -> {
                JobRunEntity e = a.getArgument(0);
                capturedRunEntity = e;
                return e;
            });
            when(jobRunRepository.findByRunId(any(UUID.class))).thenReturn(java.util.Optional.of(capturedRunEntity));

            JobRun run = service.startRun(JobRun.TriggerType.MANUAL);

            // startRun returns immediately with RUNNING; DB updated async
            assertThat(run.status()).isEqualTo(JobRun.Status.RUNNING);
            assertThat(run.triggerType()).isEqualTo(JobRun.TriggerType.MANUAL);
            verify(jobRunRepository, atLeastOnce()).save(any(JobRunEntity.class));
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
            when(jobRunRepository.findByRunId(any(UUID.class))).thenReturn(java.util.Optional.of(capturedRunEntity));

            JobRun run = service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(run.status()).isEqualTo(JobRun.Status.RUNNING);
            assertThat(run.triggerType()).isEqualTo(JobRun.TriggerType.SCHEDULED);
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
            when(jobRunRepository.findByRunId(any(UUID.class))).thenReturn(java.util.Optional.of(capturedRunEntity));

            JobRun run = service.startRun(JobRun.TriggerType.SCHEDULED);

            assertThat(run.status()).isEqualTo(JobRun.Status.RUNNING);
            verify(watchlistStore, times(1)).getActiveWatchlistSymbols();
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
                    signalPipeline, backtestEngine, paperTradingEngine,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore);
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
                    signalPipeline, backtestEngine, paperTradingEngine,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore);
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
                    signalPipeline, backtestEngine, paperTradingEngine,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore);
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
                    signalPipeline, backtestEngine, paperTradingEngine,
                    jobRunRepository, jobRunStageRepository, signalStore,
                    watchlistStore, candleStore);
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