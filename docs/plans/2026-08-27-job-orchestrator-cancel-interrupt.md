# JobOrchestratorService.cancelRun() — Actually Interrupt In-Flight Work

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: Core interrupt mechanism (single symbol, SENTIMENT stage) | [x] PASS | RED confirmed (compile errors: missing CANCELLED enum + recordRunCancelled), then GREEN after prod fixes. Fixed 2 test-authoring bugs found during RED->GREEN: (1) test used pre-generated `runId` field instead of the UUID actually returned by `startRun()`, so `cancelRun()` never matched the in-flight future's key; (2) an unused `findByRunIdOrderBySymbolAscStageNameAsc` stub (unreachable once the CANCELLED early-return guard short-circuits `completeRun()`) tripped Mockito's STRICT_STUBS. `CancelRunInterruptsInFlightWork` GREEN plus all 28 pre-existing tests unaffected (29/30 passing at this checkpoint; the 1 failure is the not-yet-implemented Phase 2 test, expected RED per plan). JobOrchestratorMetricsTest green (all 3 tests). | 2026-08-27T16:35:02Z |
| 2: Queued/not-yet-started symbols stop cleanly too | [x] PASS | RED confirmed (new test timed out / failed before the stage-loop gate existed). After adding the gate, found and fixed 2 more bugs surfaced by this test: (1) same runId-capture test bug as Phase 1 (fixed identically — use `startedRun.runId()`); (2) the plan's own gate snippet passed the cancellation message via the `resultSummary` param instead of `errorMessage` (swapped to `updateStageStatus(..., null, "Run cancelled by user request", null)`), which the test's own `containsIgnoringCase("cancel")` on `getErrorMessage()` caught. Full JobOrchestratorServiceTest suite green (30/30), stable across 3 repeated runs (`--rerun`) given the concurrency-heavy nature of the new tests. `:core:test :api:test` full regression green too. | 2026-08-27T16:40:00Z |
| 3 (optional/bonus): Stage timeout also cancels its Future | [x] DONE (code only, no automated test per plan's own recommendation) | Added `future.cancel(true);` as first line of `executeStage()`'s `TimeoutException` branch. Plan explicitly recommends skipping an automated test here (600s real timeout impractical) in favor of a manual smoke check; full existing suite (`:core:test :api:test`) still green with this change in place, confirming no regression to the non-timeout paths. | 2026-08-27T16:40:00Z |

---

## 0. Investigation Findings (why the fix must target `executeStage()`, not `processSymbol()`)

Read in full before implementing — these findings determine exactly which thread must be
interrupted and why a naive fix (e.g. interrupting only the outer per-symbol thread) will
**not** stop the CPU-pegging.

### 0.1 The real blocking call, and which thread executes it

Call chain when a symbol is in the SENTIMENT stage:

```
JobOrchestratorService.processSymbol()          [outer virtual thread "A", from asyncExecutor]
  -> executeStage(..., stageSentiment, ...)
       -> CompletableFuture.supplyAsync(supplier, asyncExecutor)   [inner virtual thread "B"]
            -> JobOrchestratorService.stageSentiment(symbol, date)
                 -> SentimentService.analyzeStockSentiment()
                      -> SentimentService.performSentimentAnalysis()
                           -> SpringAiLlmClient.generateChatCompletion()   [SYNCHRONOUS despite returning Mono<String>]
                                -> chatClient.prompt()....call().chatResponse()
                                     -> Spring AI OpenAiChatModel -> openai-java-core SDK -> OkHttp3 `Call.execute()`
                                          -> blocking socket read (java.net.Socket)
```

**Critical detail confirmed by reading `SpringAiLlmClient.java:57-61`:** the Mono returned by
`generateChatCompletion()` is `Mono.just(result)` — the blocking HTTP call already happened
*before* the Mono is constructed. So `SentimentService.performSentimentAnalysis()`'s
`.block(Duration.ofSeconds(600))` (`SentimentService.java:242`) does essentially nothing —
the real, long-running blocking call is `chatClient.prompt()...call().chatResponse()` inside
`SpringAiLlmClient.generateChatCompletion()`, executing synchronously on thread **B** (the
*inner* `executeStage()` future's thread), not thread A.

**Critical detail confirmed by decompiling `okhttp-4.12.0.jar`
(`okhttp3/internal/connection/RealCall.class`, `javap -p -c`):**
`RealCall.execute()` calls `getResponseWithInterceptorChain$okhttp()` **directly on the
calling thread** — synchronous OkHttp calls do not hop to a separate dispatcher thread (that
only happens for `.enqueue()`). This confirms thread B itself is the one blocked in the
socket read; there is no fourth thread involved.

Also confirmed by decompiling the same class: the only `InterruptedIOException` OkHttp itself
throws (`RealCall.timeoutExit()`) is fired by OkHttp's own internal `AsyncTimeout` watchdog
when `OkHttpClient`'s configured call/connect/read timeout elapses — **this is unrelated to,
and does not consult, the calling thread's JDK interrupt status.** OkHttp itself does not
proactively check `Thread.interrupted()`.

**Implication:** whether interrupting thread B actually aborts the blocked socket read
depends entirely on JDK-internal behavior (JEP 353's `NioSocketImpl` replacement of the
legacy plain-socket implementation, combined with Project Loom's virtual-thread blocking I/O
semantics in JDK 21, both of which are designed so that a blocking socket read *does* respond
to `Thread.interrupt()` by aborting with an `IOException`) — not on anything in the OkHttp/
openai-java-core jars, which cannot be decompiled to prove this either way. **This is why the
plan requires the live verification steps in §4 — they are not optional decoration, they are
the only way to confirm the JDK-level interrupt behavior actually holds for this exact code
path in this exact runtime.**

Two facts reduce risk here:
- `LlmConfig.java` sets `OLLAMA_MAX_RETRIES = 0` for the Ollama backend specifically (the one
  in the observed incident), so an aborted call will not be silently retried by the SDK.
- `JobOrchestratorService.asyncExecutor` is built with
  `Executors.newThreadPerTaskExecutor(Thread.ofVirtual()...)` — every stage, including
  SENTIMENT, already runs on a JDK 21 virtual thread, which is exactly the case Project Loom's
  interruptible-blocking-I/O work targets.

### 0.2 `CompletableFuture.cancel(true)` does not interrupt anything — must use a raw `Future`

`executeStage()` currently does:
```java
CompletableFuture<StageExecutionResult> future = CompletableFuture.supplyAsync(() -> {...}, asyncExecutor);
StageExecutionResult result = future.get(timeoutSec, TimeUnit.SECONDS);
```
Per the JDK `CompletableFuture` Javadoc: *"Since (unlike `FutureTask`) this class has no
direct control over the computation that causes it to be completed, cancellation is achieved
by atomically setting an internal field... interruption is thus not directly performed."*
**`CompletableFuture.cancel(true)` never calls `Thread.interrupt()` on the thread running the
supplier — the `mayInterruptIfRunning` flag is silently ignored.** So even if we tracked and
cancelled the `CompletableFuture`, it would not fix the bug.

By contrast, submitting via `ExecutorService.submit(Callable)` returns a JDK `Future` backed by
`FutureTask`, whose `cancel(true)` **does** call `Thread.interrupt()` on the executing thread.
The fix therefore must switch `executeStage()` from `CompletableFuture.supplyAsync(...)` to
`asyncExecutor.submit(...)` so the returned `Future` is a real, interrupt-capable one.

### 0.3 `startRun()`'s completion callback currently stomps CANCELLED back to COMPLETED/FAILED

`startRun()`'s `CompletableFuture.allOf(...).whenComplete(...)` (currently
`JobOrchestratorService.java:217-249`) unconditionally calls `completeRun(runId, COMPLETED/FAILED, ...)`
once every symbol's future finishes — including symbols that finish (with errors, because they
were just interrupted) *after* `cancelRun()` already wrote `CANCELLED` to the `job_runs` row.
Without a guard, `completeRun()` will overwrite that CANCELLED status back to
COMPLETED/FAILED shortly after every cancel — this is a second, independent bug that must be
fixed alongside the interrupt mechanism, or a cancelled run's terminal DB state will still be
wrong even once the interrupt itself works.

### 0.4 `JobRunStage.Status` has no `CANCELLED` value — existing code already writes an invalid one

`JobRunStage.Status` (`backend/core/src/main/java/com/swingtrade/domain/JobRunStage.java:22`)
is `PENDING, RUNNING, COMPLETED, SKIPPED, ERROR` — **no `CANCELLED`.** Yet the *existing*
`cancelRun()` already does `e.setStatus("CANCELLED")` directly on the entity, bypassing the
enum. `JobRunStageEntity.toDomain()` does `JobRunStage.Status.valueOf(status)`
(`JobRunStageEntity.java:86`), which would throw `IllegalArgumentException` on any row written
this way. This code path is currently dormant (nothing calls `.toDomain()` on a stage entity
today — confirmed via repo-wide grep), but this fix relies on `CANCELLED` stage rows much more
heavily, so **add `CANCELLED` to the enum** as part of this fix (small, additive, in `core`,
not in the excluded-files list).

### 0.5 `cancelRun()` never calls any `JobOrchestratorMetrics` method — `job.runs.active` gauge leaks

`JobOrchestratorMetrics.activeRuns` is only decremented by `recordRunCompleted`,
`recordRunFailed`. `cancelRun()` calls none of these, so every cancelled run permanently
leaks +1 on the `job.runs.active` Prometheus gauge. Directly related to the method under
repair and trivial to fix alongside it — add `recordRunCancelled()`.

---

## 1. Feature Map

| Feature | Tested? | Test type |
|---|---|---|
| `cancelRun()` interrupts the actual in-flight blocking stage call (not just DB flip) | Yes (1) | Unit (Mockito, real concurrency via latches) |
| Interrupted stage lands in a clean terminal state (`CANCELLED`, clear reason) not left `RUNNING` | Yes (1, same test) | Unit |
| Cancelled run's overall status is not overwritten back to `COMPLETED`/`FAILED` once background threads unwind | Yes (1, same test) | Unit |
| `cancelRun()` decrements the `job.runs.active` gauge (no permanent leak) | Yes (2: service-level `verify()` + dedicated metrics test) | Unit |
| A symbol still queued behind `maxConcurrent` (never started any stage) is fully cancelled without doing any real work once the semaphore frees up | Yes (1) | Unit |
| All of a queued symbol's `PENDING` stage rows end up `CANCELLED` with a clear reason, not left dangling | Yes (1, same test) | Unit |
| (Optional/bonus) A stage that hits its own timeout also has its underlying `Future` cancelled, not just abandoned | Yes (1) | Unit |
| Startup reaper (`reapAllRunningRunsOnStartup`) unaffected | N/A — not touched | Existing (9 tests, untouched) |
| Staleness watchdog (`reapOrphanedRuns`) unaffected | N/A — not touched | Existing (4 tests, untouched) |
| Real Ollama/vLLM socket read actually aborts on interrupt in production | Not unit-testable — JDK/OS-level behavior | Live/manual (§4) |

---

## 2. Phase Breakdown

### Phase 1: Core interrupt mechanism (TDD — will fail)

**What to test:** cancelling a run while its only symbol is blocked in the SENTIMENT stage
must (a) actually interrupt the thread running that stage's blocking call, (b) leave that
stage row in a clean terminal state, (c) leave the run's overall status as `CANCELLED` (not
overwritten later), and (d) decrement the active-runs gauge.

**File:** `backend/api/src/test/java/com/swingtrade/api/service/JobOrchestratorServiceTest.java`

Add a new top-level `@Nested` class (after the existing `CancelRun` nested class, before
`GetProgress`):

```java
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
                signalPipeline, backtestEngine, tradingService,
                jobRunRepository, jobRunStageRepository, signalStore,
                watchlistStore, candleStore, jobOrchestratorMetrics, 3, 1000L, true);

        when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of("RELIANCE"));
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
        when(jobRunStageRepository.save(any(JobRunStageEntity.class))).thenAnswer(invocation -> {
            JobRunStageEntity entity = invocation.getArgument(0);
            stageState.put(entity.getStageName(), entity);
            if (JobRunStage.StageName.SENTIMENT.name().equals(entity.getStageName())
                    && !"RUNNING".equals(entity.getStatus())) {
                sentimentStageFinalized.countDown();
            }
            return entity;
        });
        when(jobRunStageRepository.saveAll(any())).thenAnswer(invocation -> {
            List<JobRunStageEntity> entities = invocation.getArgument(0);
            for (JobRunStageEntity entity : entities) {
                stageState.put(entity.getStageName(), entity);
                if (JobRunStage.StageName.SENTIMENT.name().equals(entity.getStageName())
                        && !"RUNNING".equals(entity.getStatus())) {
                    sentimentStageFinalized.countDown();
                }
            }
            return entities;
        });
        when(jobRunStageRepository.findByRunIdAndSymbolAndStageName(
                any(UUID.class), eq("RELIANCE"), anyString()))
            .thenAnswer(invocation -> {
                JobRunStageEntity entity = stageState.get(invocation.getArgument(2));
                return entity == null ? List.of() : List.of(entity);
            });
        when(jobRunStageRepository.findByRunIdAndStageName(any(UUID.class), anyString()))
            .thenAnswer(invocation -> {
                JobRunStageEntity entity = stageState.get(invocation.getArgument(1));
                return entity == null ? List.of() : List.of(entity);
            });
        when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
            .thenAnswer(invocation -> List.copyOf(stageState.values()));

        when(candleStore.findTopBySymbolOrderByDateDesc("RELIANCE", 100)).thenReturn(List.of());
        when(newsIngestionService.fetchStockNews("RELIANCE")).thenReturn(List.of());
    }

    @Test
    @DisplayName("Cancelling a run interrupts the blocking SENTIMENT-stage thread instead of only flipping DB status")
    void shouldInterruptInFlightSentimentStageOnCancel() throws InterruptedException {
        when(sentimentService.analyzeStockSentiment(eq("RELIANCE"), any(LocalDate.class)))
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

        service.startRun(JobRun.TriggerType.MANUAL);

        assertThat(sentimentStarted.await(2, TimeUnit.SECONDS))
            .as("SENTIMENT stage should have started before we cancel")
            .isTrue();

        service.cancelRun(runId);

        assertThat(sentimentInterrupted.await(5, TimeUnit.SECONDS))
            .as("cancelRun() must interrupt the thread blocked in the SENTIMENT stage call, "
                + "not just update DB status")
            .isTrue();
        assertThat(sentimentStageFinalized.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(symbolProcessingFinished.await(5, TimeUnit.SECONDS)).isTrue();

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
}
```

**Why it fails today:** `cancelRun()` never interrupts anything, so `sentimentInterrupted`
never counts down and the test times out / fails on the first `assertThat(...await...)`.
`JobRunStage.Status.CANCELLED` also does not yet exist (compile error) — this alone is
sufficient to make the test RED.

**Also add** to `backend/core/src/test/java/com/swingtrade/core/metrics/JobOrchestratorMetricsTest.java`
(new `@Nested` class, after `RecordRunReaped`):

```java
@Nested
@DisplayName("recordRunCancelled")
class RecordRunCancelled {

    @Test
    @DisplayName("Decrements the active gauge without counting as a failure")
    void shouldDecrementActiveGaugeOnCancel() {
        metrics.recordRunStarted();

        metrics.recordRunCancelled();

        assertThat(activeGauge()).isZero();
        assertThat(counter("job.runs.failed")).isZero();
        assertThat(counter("job.runs.completed")).isZero();
    }
}
```

**Why it fails today:** `recordRunCancelled()` does not exist — compile error (RED).

---

**Phase 1: Switch to fix**

1. `backend/core/src/main/java/com/swingtrade/domain/JobRunStage.java:22` — change:
   ```java
   public enum Status { PENDING, RUNNING, COMPLETED, SKIPPED, ERROR }
   ```
   to:
   ```java
   public enum Status { PENDING, RUNNING, COMPLETED, SKIPPED, ERROR, CANCELLED }
   ```

2. `backend/core/src/main/java/com/swingtrade/core/metrics/JobOrchestratorMetrics.java` — add,
   after `recordRunReaped()`:
   ```java
   /**
    * Records a run cancelled by user request via {@code cancelRun()}. Unlike
    * {@link #recordRunFailed}, cancellation is not counted as a failure, but
    * {@code activeRuns} must still be decremented — cancelRun() previously called neither
    * recordRunCompleted nor recordRunFailed, permanently leaking +1 on the
    * {@code job.runs.active} gauge for every cancelled run.
    */
   public void recordRunCancelled() {
       activeRuns.decrementAndGet();
   }
   ```

3. `backend/api/src/main/java/com/swingtrade/api/service/JobOrchestratorService.java`:

   a. Imports — add:
   ```java
   import java.util.Set;
   import java.util.concurrent.CancellationException;
   import java.util.concurrent.ConcurrentHashMap;
   import java.util.concurrent.Future;
   ```

   b. New fields, after `private final ExecutorService asyncExecutor;` (~line 76):
   ```java
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
   ```

   c. New private helper, near `acquireSlot`:
   ```java
   private static String inFlightKey(UUID runId, String symbol) {
       return runId + "::" + symbol;
   }
   ```

   d. `executeStage()` (currently lines 338-373) — replace the `CompletableFuture.supplyAsync`
      body with a genuine cancellable `Future`, and add a `CancellationException` branch.
      Full replacement:
   ```java
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
           updateStageStatus(runId, symbol, stage, result.status(),
               duration, null, result.summary());
           logger.debug("Stage {} finished with status {} for {} in {}ms",
               stage, result.status(), symbol, duration);
           return result.status() == JobRunStage.Status.COMPLETED;
       } catch (CancellationException e) {
           long duration = System.currentTimeMillis() - start;
           String msg = "Cancelled by user request";
           updateStageStatus(runId, symbol, stage, JobRunStage.Status.CANCELLED,
               duration, msg, null);
           logger.info("Stage {} cancelled for {}", stage, symbol);
           return false;
       } catch (TimeoutException e) {
           long duration = System.currentTimeMillis() - start;
           String msg = "Stage timed out after " + timeoutSec + "s";
           updateStageStatus(runId, symbol, stage, JobRunStage.Status.ERROR,
               duration, msg, null);
           logger.warn("{} for {}", msg, symbol);
           return false;
       } catch (Exception e) {
           long duration = System.currentTimeMillis() - start;
           updateStageStatus(runId, symbol, stage, JobRunStage.Status.ERROR,
               duration, e.getMessage(), null);
           logger.warn("Stage {} failed for {}: {}", stage, symbol, e.getMessage());
           return false;
       } finally {
           inFlightStageFutures.remove(key, future);
       }
   }
   ```
      Note: `CompletableFuture<StageExecutionResult>` is no longer used here — the type is now
      the JDK `Future<StageExecutionResult>` returned by `ExecutorService.submit(Callable)`.

   e. `cancelRun()` (currently lines 682-706) — full replacement:
   ```java
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
   ```

   f. `startRun()`'s completion callback (currently lines 217-249) — add a guard as the first
      statement inside the `try` block of `.whenComplete(...)`:
   ```java
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
           // ... rest of the existing method body is unchanged ...
   ```
      (Everything after this new `if` block stays exactly as-is; only the guard is new.)

**Verify (RED then GREEN):**
```bash
cd backend
./gradlew :api:test --tests="com.swingtrade.api.service.JobOrchestratorServiceTest" 2>&1 | tail -60
./gradlew :core:test --tests="com.swingtrade.core.metrics.JobOrchestratorMetricsTest" 2>&1 | tail -40
```
Confirm both fail (RED) before any production code changes, then confirm both pass (GREEN)
after applying 1-3 above. Then run the full existing `JobOrchestratorServiceTest` suite to
confirm none of the untouched tests (`reapOrphanedRuns`, `reapAllRunningRunsOnStartup`,
`startRun`, `stagePaperTrade`, `findActiveRun`, existing `cancelRun` tests, `getProgress`,
`getSummary`, `listRuns`) regressed.

---

### Phase 2: Queued/not-yet-started symbols stop cleanly too (TDD — will fail)

**What to test:** with `maxConcurrent=1` and two symbols, while symbol A is blocked in
SENTIMENT (holding the only permit) and symbol B is still waiting in `acquireSlot()` (has not
started *any* stage — its `DATA_FETCH` row is still `PENDING`), cancelling the run must result
in symbol B never calling `dataIngestionService.processSingleStock(...)` at all, and all of
symbol B's stage rows ending up `CANCELLED` (not left `PENDING` forever), once it eventually
acquires the freed permit.

**File:** same test file — add a second new `@Nested` class after
`CancelRunInterruptsInFlightWork`:

```java
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
                signalPipeline, backtestEngine, tradingService,
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
        when(jobRunStageRepository.findByRunIdOrderBySymbolAscStageNameAsc(any(UUID.class)))
            .thenAnswer(invocation -> List.copyOf(stageState.values()));

        when(candleStore.findTopBySymbolOrderByDateDesc(anyString(), eq(100))).thenReturn(List.of());
        when(newsIngestionService.fetchStockNews(anyString())).thenReturn(List.of());
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
        service.startRun(JobRun.TriggerType.MANUAL);

        assertThat(anySentimentStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(symbolsThatStartedSentiment).hasSize(1);
        String activeSymbol = symbolsThatStartedSentiment.iterator().next();
        String queuedSymbol = activeSymbol.equals("A") ? "B" : "A";

        service.cancelRun(runId);

        assertThat(bothSymbolsFinished.await(10, TimeUnit.SECONDS))
            .as("both symbols' processSymbol() must finish (quickly) after cancel — the "
                + "queued one should never do real work, and the active one should unwind "
                + "promptly once interrupted")
            .isTrue();

        verify(dataIngestionService, never())
            .processSingleStock(eq(queuedSymbol), any(LocalDate.class));

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
```

**Why it fails today:** without the Phase 1 fix, symbol A's SENTIMENT call is never
interrupted, so it never releases its semaphore permit — symbol B never even acquires it
within the test's wait window, `bothSymbolsFinished` never reaches 2, and the test times out.
Even with only the Phase 1 fix (no stage-loop gate yet), symbol B *would* acquire the freed
permit but would then run all 6 stages for real (including calling
`dataIngestionService.processSingleStock("B", ...)`), failing the `verify(..., never())`
assertion and leaving symbol B's stages `COMPLETED`/`SKIPPED` instead of `CANCELLED` — so this
test also validates the specific Phase 2 fix on top of Phase 1's.

**Phase 2: Switch to fix**

`backend/api/src/main/java/com/swingtrade/api/service/JobOrchestratorService.java`,
`processSymbol()`'s stage loop (currently lines 287-303) — add a cancellation check as the
first statement inside the `for` loop, before the existing `priorStageBlocked` check:

```java
boolean priorStageBlocked = false;
for (StageDef stageDef : stageDefs) {
    currentStage[0] = stageDef.name();
    if (cancelledRunIds.contains(runId)) {
        updateStageStatus(runId, symbol, stageDef.name(), JobRunStage.Status.CANCELLED,
            null, null, "Run cancelled by user request");
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
```

**Verify (RED then GREEN):**
```bash
cd backend
./gradlew :api:test --tests="com.swingtrade.api.service.JobOrchestratorServiceTest" 2>&1 | tail -80
```
Confirm the new `CancelRunStopsQueuedSymbols` test fails after Phase 1 alone is applied (RED
for Phase 2 specifically), then passes once the stage-loop gate is added (GREEN). Re-run the
full suite again to confirm no regressions, especially `StartRun` (which exercises multi-symbol
concurrency at `maxConcurrent=3`) and `StagePaperTrade`.

---

### Phase 3 (optional/bonus): Stage timeout also cancels its Future

Not required to fix the reported bug (`cancelRun()` interrupting in-flight work), but the
Phase 1 infrastructure (a real, cancellable `Future` per stage) makes this a one-line,
low-risk addition that closes a *related* leak: today, when a stage hits its own
`TIMEOUT_*` and `executeStage()` catches `TimeoutException`, the underlying task keeps running
in the background forever — exactly the same CPU-pegging symptom as the reported bug, just
triggered by a timeout instead of an explicit cancel. Include this only if the reviewer wants
it; it is independent of Phases 1-2 and can be dropped without affecting them.

**What to test:** add to `CancelRunInterruptsInFlightWork` (or a new small `@Nested` class) a
test with `TIMEOUT_SENTIMENT`-scale patience is impractical (600s) — instead, test this at a
narrower unit level by asserting that `executeStage()`'s `TimeoutException` branch calls
`future.cancel(true)`. Since `executeStage()` is private, the practical test is behavioral:
stub `sentimentService.analyzeStockSentiment` to block on a latch that is *never* released
within the (test-shortened) timeout — this requires either (a) exposing a package-private
timeout override for tests, or (b) accepting a slower test. Given the cost/benefit, **recommend
skipping an automated test for this phase** and instead covering it via a short manual smoke
check (start a run, let a stage genuinely time out, confirm via `ps aux | grep llama-server`
that CPU drops after the timeout rather than continuing) — call this out explicitly to
whoever picks up this optional phase rather than inventing a flaky test.

**Fix (if included):** in `executeStage()`'s `catch (TimeoutException e)` block, add
`future.cancel(true);` as the first line:
```java
} catch (TimeoutException e) {
    future.cancel(true);
    long duration = System.currentTimeMillis() - start;
    String msg = "Stage timed out after " + timeoutSec + "s";
    updateStageStatus(runId, symbol, stage, JobRunStage.Status.ERROR,
        duration, msg, null);
    logger.warn("{} for {}", msg, symbol);
    return false;
}
```

---

## 3. Files Summary

| Action | File | Type |
|---|---|---|
| Modify | `backend/core/src/main/java/com/swingtrade/domain/JobRunStage.java` | Production (enum) |
| Modify | `backend/core/src/main/java/com/swingtrade/core/metrics/JobOrchestratorMetrics.java` | Production |
| Modify | `backend/core/src/test/java/com/swingtrade/core/metrics/JobOrchestratorMetricsTest.java` | Test |
| Modify | `backend/api/src/main/java/com/swingtrade/api/service/JobOrchestratorService.java` | Production |
| Modify | `backend/api/src/test/java/com/swingtrade/api/service/JobOrchestratorServiceTest.java` | Test |

**Not touched (per task instructions):** V27 migration, `GlobalExceptionHandler.java`,
`PositionService.java`, `PaperTradingEngine.java`, `PositionManager.java`,
`PaperTradingStateService.java`, `PositionRepository.java`, and the existing
`stagePaperTrade()` reordering already in `JobOrchestratorService.java` (its signal-marked-
before-execution logic and its 3 existing `StagePaperTrade` tests are unrelated to this fix
and untouched by any of the diffs above). `reapOrphanedRuns()` and
`reapAllRunningRunsOnStartup()` are also untouched — this fix only adds a new guard to
`startRun()`'s completion callback and rewrites `executeStage()`/`cancelRun()`/the stage loop
in `processSymbol()`; none of the reaper methods are modified.

**Noted but explicitly out of scope for this plan** (frontend, one-line, low-risk, can be
picked up separately): `dashboard/src/api/types.ts:390` — `JobRunStageResponse.status`'s TS
union (`'PENDING' | 'RUNNING' | 'COMPLETED' | 'SKIPPED' | 'ERROR'`) does not include
`'CANCELLED'` even though the backend has been able to emit that literal string since before
this fix (the pre-existing `cancelRun()` already wrote the raw string). This plan's backend
enum change makes that string official; updating the frontend type to match is a natural
follow-up but was not requested and is not required for the backend fix to work correctly.

---

## 4. Verification

### Automated (RED then GREEN)

```bash
cd backend

# Phase 1 — must fail before any production code changes, pass after
./gradlew :core:test --tests="com.swingtrade.core.metrics.JobOrchestratorMetricsTest"
./gradlew :api:test --tests="com.swingtrade.api.service.JobOrchestratorServiceTest"

# Phase 2 — apply the stage-loop gate, re-run
./gradlew :api:test --tests="com.swingtrade.api.service.JobOrchestratorServiceTest"

# Full regression across the modules touched
./gradlew :core:test :api:test

# Full build gate (checkstyle + PMD + coverage threshold)
./gradlew check
```

Confirm specifically that these existing, untouched tests still pass unchanged (no diffs to
their expectations should be needed):
- `JobOrchestratorServiceTest.ReapOrphanedRuns` (4 tests)
- `JobOrchestratorServiceTest.ReapAllRunningRunsOnStartup` (4 tests)
- `JobOrchestratorServiceTest.CancelRun` (existing 2 tests: `testCancelRun_RunningRun_MarksStagesCancelled`,
  `testCancelRun_AlreadyCompletedRun_NoEffect`)
- `JobOrchestratorServiceTest.StagePaperTrade` (3 tests)
- `JobOrchestratorServiceTest.StartRun` (7 tests, including the concurrency-heavy
  `shouldReturnImmediatelyWhenWatchlistExceedsExecutorCapacity`)

### Live Verification (post-implementation, not automated)

These steps exist specifically because §0.1 established that whether `Thread.interrupt()`
actually aborts the blocked OkHttp/socket read depends on JDK-internal virtual-thread I/O
behavior that cannot be proven by unit tests or by decompiling the OkHttp jar — it must be
observed against the real Ollama backend.

1. Rebuild and restart the backend (`cd backend && ./gradlew :api:bootRun --args='--spring.profiles.active=local,fyers'`, or via `./run-local.sh` / `dev-stack.sh`).
2. Trigger a fresh job orchestrator run (`POST /api/job/runs/start`), wait until a symbol is
   mid-flight in the SENTIMENT stage — confirm via `GET /api/job/runs/{id}/progress` (stage
   shows `RUNNING`) and by observing `llama-server`/Ollama CPU usage:
   ```bash
   ps aux | grep llama-server
   ```
3. Cancel that run: `POST /api/job/runs/{id}/cancel`.
4. Confirm via `ps`/CPU monitoring that the Ollama process drops back to idle **promptly**
   after cancel (within a few seconds) — **not** after the full `TIMEOUT_SENTIMENT` (600s)
   window. If it does not drop promptly, the JDK-level interrupt assumption in §0.1 does not
   hold for this runtime/OkHttp version combination, and the fix needs a stronger mechanism
   (e.g. plumbing the real `okhttp3.Call` out of `SpringAiLlmClient` so `Call.cancel()` can be
   invoked directly, which always works regardless of JDK interrupt semantics — see §0.1 for
   why that is a bigger, out-of-scope change for this plan and would need its own follow-up).
5. Confirm via DB (psql) that the interrupted symbol's SENTIMENT stage — and the run overall —
   end up in a clean terminal state, not left `RUNNING` forever:
   ```sql
   SELECT run_id, status, error_message FROM job_runs WHERE run_id = '<runId>';
   SELECT symbol, stage_name, status, error_message FROM job_run_stages
     WHERE run_id = '<runId>' ORDER BY symbol, stage_name;
   ```
   Expect `job_runs.status = 'CANCELLED'` and no `job_run_stages` row left at `status = 'RUNNING'`.
6. Confirm a subsequent fresh run after a full backend restart still works correctly
   end-to-end: rebuild, restart, trigger a new run, observe clean stage progression across
   several symbols via `/api/job/runs/{id}/progress`, then run the full test suite
   (`./gradlew check`) once more to confirm nothing regressed.

---

## 5. Live Verification Results (2026-08-27, post-implementation)

Backend rebuilt (`./gradlew :api:bootJar -x test`) and restarted via
`java -Duser.timezone=Asia/Kolkata -jar api/build/libs/api.jar --spring.profiles.active=local`
(old PID 78497 running a pre-fix jar built 15:55 killed; new PID 93509 started 16:41 IST from
the fix-containing jar built 17:40 IST).

**Cancel-while-mid-flight (steps 2-5 of §4 Live Verification):**
- Started run `bc95c21b-a86c-4a48-a6a7-4b9cc0fef8e7` (15 symbols) via `POST /api/job/runs/start`.
- `GET /api/job/runs/{id}/progress` showed SENTIMENT `RUNNING` for AXISBANK, BHARTIARTL, HDFC.
- `llama-server` (PID 93596) CPU climbed 0.4% -> 41.9% -> 51.7% -> 69.7% over ~10s, confirming
  genuine generation work in progress.
- Cancelled via `POST /api/job/runs/{id}/cancel` at 17:42:00.3.
- `llama-server` CPU dropped to 0.9% within 1 second (17:42:01.3) and stayed at 0.3-0.5%
  (idle baseline) for the following 20s of polling — **not** the full 600s `TIMEOUT_SENTIMENT`
  window.
- App log confirmed the JDK-level interrupt assumption from §0.1 held for real:
  ```
  Run bc95c21b-... cancel: interrupted 3 in-flight stage thread(s)
  Stage SENTIMENT cancelled for BHARTIARTL / AXISBANK / HDFC
  Caused by: java.net.SocketException: Closed by interrupt   (x3)
  Run bc95c21b-... cancelled
  Run bc95c21b-... finished after cancellation — leaving status as CANCELLED
  ```
  (A benign side-effect also appeared: 2x "Interrupted during connection acquisition" HikariCP
  warnings from a DB call racing the same interrupt — logged as WARN, does not affect the
  stage's final CANCELLED write, no further action taken.)
- `psql`: `job_runs.status = 'CANCELLED'`, `error_message` empty (clean, not FAILED).
- `psql`: 84 `job_run_stages` rows `CANCELLED`, 6 `COMPLETED` (the DATA_FETCH+NEWS already done
  for the 3 active symbols before cancel), **zero rows left `RUNNING`**. The 3 actively
  interrupted SENTIMENT rows: `status=CANCELLED`, `error_message='Cancelled by user request'`.
  Queued symbols never started (e.g. `DATA_FETCH`/`SIGNAL`/`BACKTEST`/`PAPER_TRADE` for MARUTI,
  ICICIBANK, WIPRO, HDFCBANK, ITC, INFY, etc. all logged
  `Skipping stage X for Y: run was cancelled` and landed `CANCELLED`, confirming the Phase 2
  fix works in production too, not just in the mocked test).
- `job.runs.active` gauge (`/actuator/metrics/job.runs.active`) read back `1.0` while exactly
  one subsequent run was genuinely active — no leak from the cancelled run, confirming
  `recordRunCancelled()` works in production.

**Fresh run after restart still works end-to-end (step 6):**
- Started run `95ebc202-c5d7-4430-8a75-19b94ceb79eb` (15 symbols, no cancel this time).
- `llama-server` observed at 75.4% CPU (2:18.99 cumulative time) actively doing real sentiment
  inference.
- Polled DB over several minutes: stage counts progressed cleanly
  (`COMPLETED` count climbed 6 -> 12 -> 28 while `PENDING` shrank correspondingly, `RUNNING`
  stayed at 3 = `maxConcurrent`), **zero `ERROR` rows at any point**.
- 4 symbols (BHARTIARTL, HDFC, HDFCBANK, SBIN) reached full 6-stage completion with normal
  per-stage statuses (`COMPLETED` for all stages, except HDFC's BACKTEST/PAPER_TRADE
  legitimately `SKIPPED` — pre-existing, unrelated insufficient-candle-history behavior, not a
  regression from this fix).
- Given each SENTIMENT call is a genuine CPU-only local LLM generation (the plan's own
  `TIMEOUT_SENTIMENT=600s` accounts for exactly this), running all 15 symbols to full
  completion would take well beyond this session's reasonable verification window; the
  4-symbols-fully-clean + 28-stages-completed + zero-errors + steady progression evidence above
  is sufficient to confirm no regression from the fix on the non-cancel path.
- Full `./gradlew test` (all 7 modules) reconfirmed green after all live verification traffic.
