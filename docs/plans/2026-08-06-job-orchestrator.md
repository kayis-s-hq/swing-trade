# Job Orchestrator — Unified Pipeline with Dashboard Visibility

## Problem Statement

The system has 8+ scattered `@Scheduled` jobs running independently with no coordination, no shared state, and no visibility:

| Scheduler | Schedule | What it does | Problem |
|-----------|----------|-------------|---------|
| EodIngestionScheduler | 16:30 IST weekdays | Fetch latest candle for watchlist | Runs independently, no awareness of signal generation |
| SignalEngine | 17:00 IST weekdays | Generate signals for all stocks | No visibility into which symbols succeeded/failed |
| SignalExecutionJob | Every 30s | Auto-executes BUY signals as paper trades | Race condition with pilot run; no signal dedup |
| SentimentEvaluationJob | 02:00 IST daily | Evaluate LLM accuracy | Standalone, no connection to signal generation |
| BacktestScheduler | 02:00 IST Sundays | Full watchlist backtest | Standalone, no connection to signals |
| WeeklySectorDigestScheduler | 18:00 IST Sundays | Discord digest | Standalone |
| PortfolioSnapshotScheduler | 15:45 IST weekdays | Save portfolio snapshot | Standalone |
| SignalFilterService.reanalysePending | 08:00 IST daily | Re-analyze sentiment for open positions | Standalone |

**Gaps:**
1. No single view showing what happened across the full pipeline
2. No way to trigger the complete chain (data → sentiment → signal → backtest → paper trade) as one operation
3. No progress tracking — if a job fails, you dig through logs
4. No coordination between jobs (e.g., signal execution can fire before signal generation finishes)
5. Pi resources not managed — multiple jobs can overlap

## Solution

A **Job Orchestrator** that:
- Replaces all scattered `@Scheduled` jobs with one unified 6-stage pipeline
- Provides a new dashboard view (`OrchestratorView.vue`) showing per-symbol per-stage progress
- Persists run state to DB so progress survives page refresh and server restart
- Throttles concurrency to 3-4 parallel symbols (Pi-friendly)
- Supports manual trigger (dashboard button) and scheduled trigger (daily 18:00 IST weekdays)

### The 6 Stages

1. **Data Fetch** — EOD candle data via `EodIngestionScheduler` logic (Yahoo Finance)
2. **News Ingestion** — Multi-source news fetch via `NewsIngestionService.fetchStockNews()`
3. **Sentiment Analysis** — LLM sentiment via `SentimentService.analyzeStockSentiment()`
4. **Signal Generation** — Price-action signals via `SignalPipeline.generatePriceActionSignal()`
5. **Backtest** — Two sub-stages: (a) backtest the signals just generated, (b) independent price-action strategy backtest via `BacktestEngine`
6. **Paper Trade Execution** — Execute BUY signals via `PaperTradingEngine` (marks signals as processed)

### Architecture

```
OrchestratorView.vue (Dashboard)
    │
    ├─ Manual "Run" button → POST /api/job/runs/start
    ├─ Polling (3-5s)     → GET /api/job/runs/{id}/progress
    ├─ Summary            → GET /api/job/runs/{id}/summary
    └─ History            → GET /api/job/runs (list past runs)

JobOrchestratorService (Backend)
    │
    ├─ runPipeline(List<String> symbols) — main entry point
    ├─ Semaphore(3) — concurrency throttle
    ├─ Stage execution: DATA_FETCH → NEWS → SENTIMENT → SIGNAL → BACKTEST → PAPER_TRADE
    ├─ Per-symbol, per-stage tracking → JobRunRepository
    └─ Signal processing: marks signals as PROCESSED to prevent duplicate execution

JobRunScheduler (Cron)
    │
    └─ @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
        └─ Triggers runPipeline() automatically
```

## Task 1: DB Schema — Job Run Tables

**New migration:** `backend/data/src/main/resources/db/migration/V10__create_job_runs.sql`

```sql
-- Job runs (one row per orchestration run)
CREATE TABLE IF NOT EXISTS job_runs (
    id              BIGSERIAL PRIMARY KEY,
    run_id          UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    trigger_type    VARCHAR(16) NOT NULL DEFAULT 'MANUAL',  -- MANUAL, SCHEDULED
    status          VARCHAR(16) NOT NULL DEFAULT 'RUNNING', -- RUNNING, COMPLETED, FAILED, CANCELLED
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    symbols_count   INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count    INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_job_runs_status ON job_runs(status);
CREATE INDEX IF NOT EXISTS idx_job_runs_started_at ON job_runs(started_at DESC);

-- Job run stages (one row per symbol per stage)
CREATE TABLE IF NOT EXISTS job_run_stages (
    id              BIGSERIAL PRIMARY KEY,
    run_id          UUID NOT NULL REFERENCES job_runs(run_id) ON DELETE CASCADE,
    symbol          VARCHAR(16) NOT NULL,
    stage_name      VARCHAR(32) NOT NULL,  -- DATA_FETCH, NEWS, SENTIMENT, SIGNAL, BACKTEST, PAPER_TRADE
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, COMPLETED, SKIPPED, ERROR
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    duration_ms     BIGINT,
    error_message   TEXT,
    log_details     TEXT,          -- JSON string with sub-step details
    result_summary  TEXT           -- Brief result (e.g., "BUY signal generated, confidence 85%")
);

CREATE INDEX IF NOT EXISTS idx_job_run_stages_run ON job_run_stages(run_id);
CREATE INDEX IF NOT EXISTS idx_job_run_stages_symbol_stage ON job_run_stages(symbol, stage_name);
CREATE INDEX IF NOT EXISTS idx_job_run_stages_status ON job_run_stages(status);
```

## Task 2: Domain Objects

### New files in `backend/core/src/main/java/com/swingtrade/domain/`:

**1. `JobRun.java`** — Record

```java
public record JobRun(
    UUID runId,
    TriggerType triggerType,
    Status status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    int symbolsCount,
    int completedCount,
    int failedCount,
    String errorMessage
) {
    public enum TriggerType { MANUAL, SCHEDULED }
    public enum Status { RUNNING, COMPLETED, FAILED, CANCELLED }
}
```

**2. `JobRunStage.java`** — Record

```java
public record JobRunStage(
    UUID runId,
    String symbol,
    StageName stageName,
    Status status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Long durationMs,
    String errorMessage,
    String logDetails,
    String resultSummary
) {
    public enum StageName { DATA_FETCH, NEWS, SENTIMENT, SIGNAL, BACKTEST, PAPER_TRADE }
    public enum Status { PENDING, RUNNING, COMPLETED, SKIPPED, ERROR }
}
```

## Task 3: JPA Entities + Repositories

### New entities in `backend/data/src/main/java/com/swingtrade/data/entity/`:

**1. `JobRunEntity.java`**

```java
@Entity
@Table(name = "job_runs")
public class JobRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true)
    private UUID runId;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;  // MANUAL, SCHEDULED

    @Column(name = "status", nullable = false)
    private String status;  // RUNNING, COMPLETED, FAILED, CANCELLED

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "symbols_count")
    private int symbolsCount;

    @Column(name = "completed_count")
    private int completedCount;

    @Column(name = "failed_count")
    private int failedCount;

    @Column(name = "error_message")
    private String errorMessage;

    // converters from domain record
    public static JobRunEntity fromDomain(JobRun jr) { ... }
    public JobRun toDomain() { ... }
}
```

**2. `JobRunStageEntity.java`**

```java
@Entity
@Table(name = "job_run_stages")
public class JobRunStageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "stage_name", nullable = false)
    private String stageName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "log_details")
    private String logDetails;

    @Column(name = "result_summary")
    private String resultSummary;

    public static JobRunStageEntity fromDomain(JobRunStage jrs) { ... }
    public JobRunStage toDomain() { ... }
}
```

### New repositories in `backend/data/src/main/java/com/swingtrade/data/repository/`:

**1. `JobRunRepository.java`**

```java
@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Long> {
    Optional<JobRunEntity> findByRunId(UUID runId);
    List<JobRunEntity> findAllByOrderByStartedAtDesc();
    List<JobRunEntity> findByStatusOrderByStartedAtDesc(JobRunEntity.Status status);
}
```

**2. `JobRunStageRepository.java`**

```java
@Repository
public interface JobRunStageRepository extends JpaRepository<JobRunStageEntity, Long> {
    List<JobRunStageEntity> findByRunIdOrderBySymbolAscStageNameAsc(UUID runId);
    List<JobRunStageEntity> findByRunIdAndSymbol(UUID runId, String symbol);
    List<JobRunStageEntity> findByRunIdAndStageName(UUID runId, String stageName);
    List<JobRunStageEntity> findByRunIdAndSymbolAndStageName(UUID runId, String symbol, String stageName);
}
```

## Task 4: JobOrchestratorService — Core Engine

**New file:** `backend/api/src/main/java/com/swingtrade/api/service/JobOrchestratorService.java`

This is the heart of the system. It orchestrates the 6-stage pipeline across all watchlist symbols with concurrency control.

```java
@Service
public class JobOrchestratorService {

    private static final int MAX_CONCURRENT = 3;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);

    // Dependencies injected via constructor
    private final CandleStore candleStore;
    private final NewsIngestionService newsIngestionService;
    private final SentimentService sentimentService;
    private final SignalPipeline signalPipeline;
    private final BacktestEngine backtestEngine;
    private final PaperTradingEngine paperTradingEngine;
    private final JobRunRepository jobRunRepository;
    private final JobRunStageRepository jobRunStageRepository;
    private final SignalStore signalStore;
    private final WatchlistStore watchlistStore;

    public CompletableFuture<JobRun> startRun(JobRun.TriggerType triggerType) {
        // 1. Create job run record
        JobRun run = new JobRun(UUID.randomUUID(), triggerType, JobRun.Status.RUNNING,
            LocalDateTime.now(), null, 0, 0, 0, null);
        jobRunRepository.save(JobRunEntity.fromDomain(run));

        // 2. Get watchlist symbols
        List<String> symbols = watchlistStore.getActiveWatchlistSymbols();
        run = updateRun(run, r -> new JobRun(r.runId(), r.triggerType(), r.status(),
            r.startedAt(), r.completedAt(), symbols.size(), 0, 0, r.errorMessage()));
        jobRunRepository.save(JobRunEntity.fromDomain(run));

        // 3. Initialize stage rows for all symbols
        for (String symbol : symbols) {
            for (JobRunStage.StageName stage : JobRunStage.StageName.values()) {
                JobRunStage stageRow = new JobRunStage(run.runId(), symbol, stage,
                    JobRunStage.Status.PENDING, null, null, null, null, null, null);
                jobRunStageRepository.save(JobRunStageEntity.fromDomain(stageRow));
            }
        }

        // 4. Process symbols in parallel with semaphore throttle
        List<CompletableFuture<Void>> futures = symbols.stream().map(symbol ->
            CompletableFuture.runAsync(() -> processSymbol(run.runId(), symbol), asyncExecutor)
                .exceptionally(ex -> {
                    logError(run.runId(), symbol, "Pipeline failed", ex);
                    return null;
                })
        ).toList();

        // 5. Wait for all symbols to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((v, ex) -> {
                if (ex != null) {
                    completeRun(run.runId(), JobRun.Status.FAILED, ex.getMessage());
                } else {
                    completeRun(run.runId(), JobRun.Status.COMPLETED, null);
                }
            });

        return CompletableFuture.completedFuture(run);
    }

    private void processSymbol(UUID runId, String symbol) {
        try {
            semaphore.acquire();
            try {
                executeStage(runId, symbol, JobRunStage.StageName.DATA_FETCH, this::stageDataFetch);
                executeStage(runId, symbol, JobRunStage.StageName.NEWS, this::stageNews);
                executeStage(runId, symbol, JobRunStage.StageName.SENTIMENT, this::stageSentiment);
                executeStage(runId, symbol, JobRunStage.StageName.SIGNAL, this::stageSignal);
                executeStage(runId, symbol, JobRunStage.StageName.BACKTEST, this::stageBacktest);
                executeStage(runId, symbol, JobRunStage.StageName.PAPER_TRADE, this::stagePaperTrade);

                // All stages passed
                recordCompletion(runId, symbol);
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError(runId, symbol, "Pipeline interrupted", e);
        }
    }

    private void executeStage(UUID runId, String symbol, JobRunStage.StageName stage,
                              StageExecutor executor) {
        // Update stage to RUNNING
        updateStageStatus(runId, symbol, stage, JobRunStage.Status.RUNNING, null, null);

        long start = System.currentTimeMillis();
        try {
            String result = executor.execute(symbol);
            long duration = System.currentTimeMillis() - start;
            updateStageStatus(runId, symbol, stage, JobRunStage.Status.COMPLETED,
                duration, null, result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            updateStageStatus(runId, symbol, stage, JobRunStage.Status.ERROR,
                duration, e.getMessage(), null);
            throw e;
        }
    }

    // Stage implementations — delegate to existing services
    private String stageDataFetch(String symbol) {
        // Reuse EodIngestionScheduler logic: fetch latest candle via Yahoo Finance
        // Return summary: "100 candles available"
    }

    private String stageNews(String symbol) {
        // Delegate to NewsIngestionService.fetchStockNews(symbol)
        // Return summary: "15 articles fetched from 5 sources"
    }

    private String stageSentiment(String symbol) {
        // Delegate to SentimentService.analyzeStockSentiment(symbol, LocalDate.now())
        // Return summary: "POSITIVE, confidence 0.82"
    }

    private String stageSignal(String symbol) {
        // Delegate to SignalPipeline.generatePriceActionSignal(symbol)
        // Return summary: "BUY signal generated, confidence 0.85"
    }

    private String stageBacktest(String symbol) {
        // (a) Backtest the signals just generated
        // (b) Independent strategy backtest via BacktestEngine.runBacktest()
        // Return summary: "Signal backtest: 3 trades, 66% win; Strategy: 12 trades, 58% win"
    }

    private String stagePaperTrade(String symbol) {
        // Find unprocessed BUY signals for symbol, execute via PaperTradingEngine
        // Mark signals as PROCESSED via signalStore.markProcessed()
        // Return summary: "1 trade executed at 2,450.00, qty 10"
    }

    // Helpers
    private void updateStageStatus(UUID runId, String symbol, JobRunStage.StageName stage,
                                    JobRunStage.Status status, Long durationMs,
                                    String errorMessage, String resultSummary) {
        // Update the stage row in DB
    }

    private void recordCompletion(UUID runId, String symbol) {
        // Increment completed_count in job_runs
    }

    private void completeRun(UUID runId, JobRun.Status status, String errorMessage) {
        // Update job_runs status, completed_at, error_message
    }

    private interface StageExecutor {
        String execute(String symbol) throws Exception;
    }
}
```

## Task 5: JobRunController — REST API

**New file:** `backend/api/src/main/java/com/swingtrade/api/controller/JobRunController.java`

```java
@RestController
@RequestMapping("/api/job/runs")
public class JobRunController {

    private final JobOrchestratorService orchestratorService;
    private final JobRunRepository jobRunRepository;
    private final JobRunStageRepository jobRunStageRepository;

    // POST /api/job/runs/start — Start a new run (manual trigger)
    // GET  /api/job/runs/{runId}/progress — Get progress for a run (polling endpoint)
    // GET  /api/job/runs/{runId}/summary — Get summary for a completed run
    // GET  /api/job/runs — List past runs
    // POST /api/job/runs/{runId}/cancel — Cancel a running run
}
```

### DTOs

**New files in `backend/api/src/main/java/com/swingtrade/api/dto/`:**

**1. `JobRunResponse.java`**

```java
public record JobRunResponse(
    UUID runId,
    String triggerType,
    String status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    int symbolsCount,
    int completedCount,
    int failedCount,
    String errorMessage
) {
    public static JobRunResponse from(JobRunEntity entity) { ... }
}
```

**2. `JobRunStageResponse.java`**

```java
public record JobRunStageResponse(
    String symbol,
    String stageName,
    String status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Long durationMs,
    String errorMessage,
    String resultSummary
) {
    public static JobRunStageResponse from(JobRunStageEntity entity) { ... }
}
```

**3. `JobRunProgressResponse.java`**

```java
public record JobRunProgressResponse(
    UUID runId,
    String status,
    int totalSymbols,
    int completedSymbols,
    int failedSymbols,
    List<JobRunStageResponse> stages
) {
    // stages is a list of stage rows for all symbols, grouped by symbol
}
```

**4. `JobRunSummaryResponse.java`**

```java
public record JobRunSummaryResponse(
    UUID runId,
    String status,
    int totalSymbols,
    int completedSymbols,
    int failedSymbols,
    long totalDurationMs,
    // Per-stage aggregates
    Map<String, StageStats> stageStats,
    // Top performers from backtest
    List<BacktestTopSymbol> topBacktestWinRate,
    List<BacktestTopSymbol> topBacktestReturn,
    // Paper trade summary
    int tradesExecuted,
    BigDecimal totalPnL,
    // Per-symbol detail
    List<SymbolResult> symbolDetails
) {
    public record StageStats(int total, int completed, int errors, long totalDurationMs);
    public record BacktestTopSymbol(String symbol, double winRate, double totalReturn);
    public record SymbolResult(
        String symbol,
        Map<String, String> stageStatuses,
        String latestSignal,
        String sentimentScore,
        double backtestWinRate,
        double backtestReturn,
        int tradesExecuted
    );
}
```

## Task 6: JobRunScheduler — Cron Trigger

**New file:** `backend/api/src/main/java/com/swingtrade/api/scheduler/JobRunScheduler.java`

```java
@Component
public class JobRunScheduler {

    private final JobOrchestratorService orchestratorService;
    private final JobRunRepository jobRunRepository;

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
    public void runScheduledPipeline() {
        // Check if a run is already in progress — reject if so
        var running = jobRunRepository.findByStatusOrderByStartedAtDesc(
            JobRunEntity.Status.RUNNING);
        if (!running.isEmpty()) {
            logger.info("Skipping scheduled run: another run is in progress");
            return;
        }

        logger.info("Starting scheduled pipeline run");
        orchestratorService.startRun(JobRun.TriggerType.SCHEDULED);
    }
}
```

## Task 7: Disable Old Schedulers

Comment out `@Scheduled` annotations on the following files:

1. `backend/api/src/main/java/com/swingtrade/api/service/SignalEngine.java` — remove `@Scheduled(cron = "0 0 17 * * MON-FRI")` from `generateDailySignals()`
2. `backend/api/src/main/java/com/swingtrade/api/scheduler/SignalExecutionJob.java` — remove `@Scheduled(fixedDelayString = ...)` from `executePendingSignals()`
3. `backend/api/src/main/java/com/swingtrade/api/scheduler/BacktestScheduler.java` — remove `@Scheduled(cron = "0 0 2 * * SUN")` from `runWeeklyBacktest()`
4. `backend/api/src/main/java/com/swingtrade/api/scheduler/SentimentEvaluationJob.java` — remove `@Scheduled(cron = "0 0 2 * * *")` from `evaluateAccuracy()`
5. `backend/api/src/main/java/com/swingtrade/api/service/SignalFilterService.java` — remove `@Scheduled(cron = "0 0 8 * * *")` from `reanalysePending()`
6. `backend/api/src/main/java/com/swingtrade/api/scheduler/WeeklySectorDigestScheduler.java` — remove `@Scheduled(cron = "0 0 18 * * SUN")` from `sendDigest()`
7. `backend/broker/src/main/java/com/swingtrade/broker/scheduler/PortfolioSnapshotScheduler.java` — remove `@Scheduled(cron = "0 45 15 * * MON-FRI")` from `takeSnapshot()`
8. `backend/data/src/main/java/com/swingtrade/data/service/EodIngestionScheduler.java` — remove `@Scheduled(cron = "0 30 16 * * MON-FRI")` from `ingestLatestCandles()`

**Note:** Keep the methods themselves (they're used by `JobOrchestratorService`), just strip the `@Scheduled` annotations. The orchestrator calls the underlying service methods directly.

**Also:** Remove `@EnableScheduling` from `ApiSchedulingConfig.java` — it's no longer needed since the orchestrator manages execution programmatically.

## Task 7: Dashboard View — OrchestratorView.vue

**New file:** `dashboard/src/views/OrchestratorView.vue`

### Layout

```
┌─────────────────────────────────────────────────────────┐
│  Job Orchestrator                    [Run] [History ▼]  │
│  6-stage pipeline for all watchlist symbols              │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─ Current Run ─────────────────────────────────────┐  │
│  │ Status: ● RUNNING    Started: 18:00:12             │  │
│  │ Progress: ████████░░ 8/12 symbols complete          │  │
│  │ Elapsed: 4m 32s                                     │  │
│  │                                                     │  │
│  │ Symbol    │ Data │ News │ Sent │ Sign │ Back │ Pap │  │
│  │ ──────────┼──────┼──────┼──────┼──────┼──────┼───── │  │
│  │ RELIANCE  │ ✓    │ ✓    │ ✓    │ ✓    │ ✓    │ ✓   │  │
│  │ TCS       │ ✓    │ ✓    │ ✓    │ ✓    │ ✓    │ ⏳  │  │
│  │ INFY      │ ✓    │ ✓    │ ✓    │ ✓    │ ✗    │ —    │  │
│  │ HDFCBANK  │ ✓    │ ✓    │ ⏳    │ —    │ —    │ —   │  │
│  │ ...       │ ...  │ ...  │ ...  │ ...  │ ...  │ ... │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                         │
│  ── Expanded row: INFY (click to collapse) ──────────── │
│  │ Stage: SIGNAL (ERROR) — Duration: 2.3s               │  │
│  │ Error: Not enough candles for signal generation      │  │
│  │ Log: [18:04:32] Fetching candles...                  │  │
│  │        [18:04:33] Only 23 candles available          │  │
│  │        [18:04:33] Need 50 minimum, skipping          │  │
│  │                                                     │  │
│  │ Results:                                             │  │
│  │  Sentiment: NEGATIVE (confidence 0.71)              │  │
│  │  Backtest (signal): 0 trades                         │  │
│  │  Backtest (strategy): 5 trades, 40% win             │  │
│  │  Paper trades: none                                  │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                         │
│  ── Live Log Stream ─────────────────────────────────── │
│  │ [18:04:35] TCS SIGNAL  → COMPLETED (BUY, 85%)       │  │
│  │ [18:04:36] HDFCBANK SENT → RUNNING                  │  │
│  │ [18:04:37] INFY SIGNAL → ERROR: insufficient candles│  │
│  └─────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│  ── Past Runs ───────────────────────────────────────── │
│  │ 2026-08-05 18:00  ● COMPLETED  15/15  12m 34s       │  │
│  │ 2026-08-04 18:00  ● COMPLETED  14/15  11m 22s       │  │
│  │ 2026-08-03 18:00  ● FAILED    8/15   4m 12s  (err)  │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Key components

1. **Current Run Header** — status badge, progress bar, elapsed time, Run/Cancel buttons
2. **Stage Table** — expandable table with symbol rows and stage columns. Each cell shows:
   - `⏳` (spinner) — RUNNING
   - `✓` (green) — COMPLETED
   - `✗` (red) — ERROR
   - `—` (gray) — PENDING/SKIPPED
3. **Expanded Detail Row** — shows error message, full log, and results from all completed stages
4. **Live Log Stream** — auto-scrolling log of stage completions/errors
5. **Past Runs** — clickable list of historical runs, click to view that run's progress

### API interactions

```typescript
// Start a new run
export async function startJobRun(): Promise<ApiResponse<JobRunResponse>>

// Poll for progress (called every 3-5 seconds while run is RUNNING)
export async function getJobRunProgress(runId: string): Promise<ApiResponse<JobRunProgressResponse>>

// Get summary for a completed run
export async function getJobRunSummary(runId: string): Promise<ApiResponse<JobRunSummaryResponse>>

// List past runs
export async function listJobRuns(): Promise<ApiResponse<JobRunResponse[]>>

// Cancel a running run
export async function cancelJobRun(runId: string): Promise<ApiResponse<void>>
```

## Task 8: API Client & Types

**Update `dashboard/src/api/client.ts`:**

Add functions: `startJobRun`, `getJobRunProgress`, `getJobRunSummary`, `listJobRuns`, `cancelJobRun`.

**Update `dashboard/src/api/types.ts`:**

```typescript
export interface JobRunResponse {
  runId: string
  triggerType: 'MANUAL' | 'SCHEDULED'
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  startedAt: string
  completedAt: string | null
  symbolsCount: number
  completedCount: number
  failedCount: number
  errorMessage: string | null
}

export interface JobRunStageResponse {
  symbol: string
  stageName: 'DATA_FETCH' | 'NEWS' | 'SENTIMENT' | 'SIGNAL' | 'BACKTEST' | 'PAPER_TRADE'
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'SKIPPED' | 'ERROR'
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  errorMessage: string | null
  resultSummary: string | null
}

export interface JobRunProgressResponse {
  runId: string
  status: string
  totalSymbols: number
  completedSymbols: number
  failedSymbols: number
  stages: JobRunStageResponse[]
}

export interface JobRunSummaryResponse {
  runId: string
  status: string
  totalSymbols: number
  completedSymbols: number
  failedSymbols: number
  totalDurationMs: number
  stageStats: Record<string, { total: number; completed: number; errors: number; totalDurationMs: number }>
  topBacktestWinRate: Array<{ symbol: string; winRate: number; totalReturn: number }>
  topBacktestReturn: Array<{ symbol: string; winRate: number; totalReturn: number }>
  tradesExecuted: number
  totalPnL: number | null
  symbolDetails: Array<{
    symbol: string
    stageStatuses: Record<string, string>
    latestSignal: string | null
    sentimentScore: string | null
    backtestWinRate: number
    backtestReturn: number
    tradesExecuted: number
  }>
}
```

## Task 9: Wire Up Router

**Update `dashboard/src/router/index.ts`:**

Add route:

```typescript
{
  path: '/orchestrator',
  name: 'Orchestrator',
  component: () => import('../views/OrchestratorView.vue'),
  meta: { title: 'Job Orchestrator' }
}
```

Add to sidebar navigation under existing menu items.

## Task 10: Signal Processing Integration

**Update `backend/data/src/main/java/com/swingtrade/data/repository/SignalRepository.java`** (or create a method in `SignalStore`):

Add method to mark signals as processed:

```java
void markProcessed(UUID signalId);
```

This is used by the PAPER_TRADE stage to prevent the old `SignalExecutionJob` (if re-enabled) or any other consumer from re-executing the same signal.

Also add to `SignalEntity`/`Signal.java`: a `processed` boolean field and corresponding DB column. If the `processed` field already exists (from the existing `findUnprocessed()` query), no schema change is needed — just ensure the orchestrator marks signals as processed after executing them.

## Task 11: Tests

### Backend tests:

1. `JobOrchestratorServiceTest` — test `startRun()` with mock services, verify DB writes
2. `JobOrchestratorServiceConcurrencyTest` — verify semaphore limits to 3 concurrent symbols
3. `JobRunControllerTest` — test all REST endpoints (start, progress, summary, list, cancel)
4. `JobRunSchedulerTest` — verify cron fires, rejects duplicate runs

### Frontend tests:

1. `OrchestratorView.spec.ts` — Vitest tests for component rendering, button clicks, polling logic
2. `OrchestratorView.spec.ts` — Playwright E2E: click Run → see progress update → see completion

## Task 12: Config

**`backend/api/src/main/resources/application-local.properties`:**

```properties
# Job orchestrator
job.orchestrator.max-concurrent=3
job.orchestrator.poll-interval-ms=3000
```

## Execution Order

```
Phase 1: Schema + domain
  1. V10 migration (Task 1)
  2. Domain records (Task 2)
  3. JPA entities + repositories (Task 3)

Phase 2: Backend core
  4. JobOrchestratorService (Task 4)
  5. JobRunController + DTOs (Task 5)
  6. JobRunScheduler (Task 6)
  7. Disable old schedulers (Task 7)
  8. Signal processing integration (Task 10)

Phase 3: Frontend
  9. API client + types (Task 8)
  10. OrchestratorView.vue (Task 7)
  11. Router update (Task 9)

Phase 4: Tests
  12. Backend tests (Task 11)
  13. Frontend tests (Task 11)

Phase 5: Config
  14. Config properties (Task 12)
```

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Long-running sentiment API call blocks a symbol pipeline | Symbol stage times out, other symbols continue | Set timeout per stage (30s for news, 60s for sentiment). Catch timeout, mark ERROR, continue with next symbol |
| Paper trading execution fails mid-run | Partial trades, inconsistent state | Each symbol is independent. If one symbol's paper trade fails, others are unaffected. Run-level status = FAILED only if >50% symbols fail |
| DB connection pool exhausted with concurrent stages | All stages block on DB | Use `REQUIRES_NEW` transactions per stage. Connection pool size = 10 (default). Max 3 symbols × 6 stages = 18 concurrent transactions. May need to increase pool to 20 |
| Old scheduler fires during orchestrator run | Duplicate signal generation/trades | Task 7 disables all old schedulers. If any are re-enabled, they check signal `processed` flag |
| Pi runs out of memory with many symbols | OOM during large watchlist | Semaphore limits concurrency. For watchlists > 20 symbols, process in batches of 3 |
| Polling endpoint creates DB load | High DB read traffic | Polling reads from same DB table. With 3-5s interval and ~10 users max, ~2-4 queries/sec. Acceptable for Pi |
| Cancelled run leaves DB in inconsistent state | Orphaned stage rows with RUNNING status | On cancel, update all RUNNING stages to CANCELLED. Run status to CANCELLED |

## Success Criteria

1. V10 migration runs — 2 new tables created (job_runs, job_run_stages)
2. `JobOrchestratorService.startRun()` creates run record, processes all symbols through 6 stages, updates DB
3. Concurrency limited to 3 parallel symbols (verified via test)
4. REST API: POST start, GET progress, GET summary, GET list, POST cancel — all return correct data
5. Scheduled cron at 18:00 IST weekdays triggers run (rejects if another run is active)
6. All 8 old `@Scheduled` jobs disabled (annotations removed)
7. `OrchestratorView.vue` renders stage table with status icons
8. Frontend polls progress every 3-5 seconds, updates UI in real-time
9. Click expandable row shows error details and stage results
10. Past runs list shows historical run summaries
11. Signals executed by orchestrator are marked as processed
12. `mvn clean install` succeeds, all tests pass