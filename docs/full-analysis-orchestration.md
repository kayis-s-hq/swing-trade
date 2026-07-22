# Full Analysis Orchestration — Implementation Plan

## Problem Statement

Currently the dashboard has two separate analysis flows:
1. **View Sentiment** — loads existing LLM sentiment from DB (static, potentially stale)
2. **Analyze** — calls `CompositeAnalysisService.analyze()` which auto-pulls data and runs composite analysis

Users need a single "Analyze" button that:
- Checks data completeness before running
- Auto-backfills OHLCV candles if gaps exist
- Fetches news articles if none found
- Runs LLM sentiment analysis
- Computes technical indicators
- Calculates composite score
- Runs backtest
- Streams progress to the dashboard with stage-by-stage visual feedback

## Architecture

```
┌─────────────┐     POST /api/analysis/run-full     ┌──────────────────────┐
│   Dashboard │ ──────────────────────────────────► │  AnalysisController  │
│   (Vue)     │  ◄── SSE: text/event-stream         │  (SSE endpoint)      │
└─────────────┘                                     └──────────┬───────────┘
                                                             │
                                                    ┌────────▼──────────┐
                                                    │OrchestratorService│
                                                    └────────┬──────────┘
                                                             │
                                    ┌────────────────────────┼────────────────────────┐
                                    │                        │                        │
                              ┌─────▼─────┐           ┌─────▼─────┐           ┌─────▼─────┐
                              │ Stage 1-3 │           │ Stage 4   │           │ Stage 5-7 │
                              │ (Data)    │           │ (LLM)     │           │ (Compute) │
                              │           │           │           │           │           │
                              │ • Check   │           │ • Analyze │           │ • TA4j    │
                              │ • Backfill│           │ • Save    │           │ • Composite│
                              │ • Fetch   │           │           │           │ • Backtest │
                              │   news    │           │           │           │           │
                              └───────────┘           └───────────┘           └───────────┘
```

## Pipeline Stages

| # | Stage | Service | Condition | Duration |
|---|-------|---------|-----------|----------|
| 1 | Checking data completeness... | `OhlcvCandleRepository` | Always | <1s |
| 2 | Backfilling OHLCV candles... | `DataIngestionService.backfillStockData()` | <100 candles | 10-60s |
| 3 | Ingesting news articles... | `NewsIngestionService.fetchStockNews()` | 0 articles found | 5-15s |
| 4 | Running LLM sentiment... | `SentimentService.analyzeStockSentiment()` | Always | 10-30s |
| 5 | Computing technical indicators... | `TechnicalAnalysisService.compute()` | Always | <1s |
| 6 | Calculating composite score... | `CompositeAnalysisService.analyze()` | Always | <1s |
| 7 | Running backtest... | `BacktestScorer.compute()` | Always | 5-15s |

Total estimated duration: 30-120 seconds depending on data availability.

## Backend Implementation

### 1. New DTOs

#### `AnalysisProgress`
```java
public record AnalysisProgress(
    int stageNumber,
    String stageName,
    String status,    // "running" | "completed" | "skipped" | "error"
    String message,
    LocalDateTime timestamp
) {
    public static AnalysisProgress running(int stage, String name) {
        return new AnalysisProgress(stage, name, "running", "Starting...", LocalDateTime.now());
    }
    public static AnalysisProgress completed(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "completed", message, LocalDateTime.now());
    }
    public static AnalysisProgress skipped(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "skipped", message, LocalDateTime.now());
    }
    public static AnalysisProgress error(int stage, String name, String message) {
        return new AnalysisProgress(stage, name, "error", message, LocalDateTime.now());
    }
}
```

#### `FullAnalysisResult`
```java
public record FullAnalysisResult(
    CompositeAnalysis composite,
    List<AnalysisProgress> progress,
    long durationMs,
    String symbol
) {}
```

### 2. New Service: `AnalysisOrchestratorService`

Location: `backend/api/src/main/java/com/swingtrade/api/service/AnalysisOrchestratorService.java`

Responsibilities:
- Sequential pipeline execution with progress tracking
- Prerequisite checking per stage (skip if data exists)
- Error handling per stage (continue pipeline if one stage fails)
- Duration tracking per stage
- Returns `FullAnalysisResult` with full progress history

Key method:
```java
public FullAnalysisResult runFullAnalysis(String symbol, SseEmitter emitter)
```

Pipeline logic:
```
1. Check candle count for symbol
   - If <100: emit progress "backfilling", call DataIngestionService.backfillStockData()
   - If >=100: emit progress "skipped"
2. Check news article count for symbol
   - If 0: emit progress "fetching news", call NewsIngestionService.fetchStockNews()
   - If >0: emit progress "skipped"
3. Run LLM sentiment analysis
   - Always run (fresh analysis)
   - Call SentimentService.analyzeStockSentiment()
   - Save result to DB
4. Compute technical indicators
   - Call TechnicalAnalysisService.compute()
5. Compute composite score
   - Call CompositeAnalysisService.analyze() (or extract parts to avoid re-running)
6. Run backtest
   - Call BacktestScorer.compute()
7. Assemble FullAnalysisResult with progress history
```

Progress emission via SSE:
```java
private void emitProgress(SseEmitter emitter, AnalysisProgress progress) {
    try {
        emitter.send(SseEmitter.event()
            .name("progress")
            .data(progress));
    } catch (IOException e) {
        logger.error("Failed to emit progress: {}", e.getMessage());
    }
}
```

### 3. New Controller: `AnalysisOrchestrationController`

Location: `backend/api/src/main/java/com/swingtrade/api/controller/AnalysisOrchestrationController.java`

```java
@RestController
@RequestMapping("/api")
public class AnalysisOrchestrationController {

    private final AnalysisOrchestratorService orchestrator;

    @PostMapping("/analysis/run-full")
    public SseEmitter runFullAnalysis(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "3") int backfillYears) {
        
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        
        // Send initial "started" event
        try {
            emitter.send(SseEmitter.event()
                .name("started")
                .data(Map.of("symbol", symbol, "backfillYears", backfillYears)));
        } catch (IOException e) {
            return emitter;
        }
        
        // Run orchestration in background thread
        CompletableFuture.runAsync(() -> {
            try {
                FullAnalysisResult result = orchestrator.runFullAnalysis(symbol, emitter, backfillYears);
                // Send final result
                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(result));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", e.getMessage())));
                } catch (IOException ioEx) {
                    logger.error("Failed to send error: {}", ioEx.getMessage());
                }
                emitter.completeWithError(e);
            }
        });
        
        // Handle client disconnect
        emitter.onCompletion(() -> logger.info("Client disconnected from analysis stream"));
        emitter.onTimeout(() -> logger.warn("Analysis stream timed out for {}", symbol));
        emitter.onError(e -> logger.error("Analysis stream error: {}", e.getMessage()));
        
        return emitter;
    }
}
```

### 4. Dependency Injection

The orchestrator needs these services injected:
- `DataIngestionService` — for OHLCV backfill
- `NewsIngestionService` — for news fetching
- `SentimentService` — for LLM analysis
- `TechnicalAnalysisService` — for TA computation
- `CompositeAnalysisService` — for composite scoring
- `BacktestScorer` — for backtest
- `OhlcvCandleRepository` — for data checking
- `SentimentResultRepository` — for news count checking

## Frontend Implementation

### 1. New API Client Function

Location: `dashboard/src/api/client.ts`

```typescript
export async function* runFullAnalysis(
  symbol: string,
  years: number = 3
): AsyncIterable<AnalysisProgress | FullAnalysisResult> {
  const params = new URLSearchParams({
    symbol: encodeURIComponent(symbol),
    years: String(years),
  })
  
  const response = await fetch(`${API_BASE_URL}/analysis/run-full?${params}`, {
    headers: DEFAULT_HEADERS,
  })
  
  if (!response.ok) {
    throw new Error(`Analysis failed: ${response.statusText}`)
  }
  
  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = JSON.parse(line.slice(6))
          yield data
        }
      }
    }
  } finally {
    reader.releaseLock()
  }
}
```

### 2. New Types

Location: `dashboard/src/api/types.ts`

```typescript
export interface AnalysisProgress {
  stageNumber: number
  stageName: string
  status: 'running' | 'completed' | 'skipped' | 'error'
  message: string
  timestamp: string
}

export interface FullAnalysisResult {
  composite: CompositeAnalysis
  progress: AnalysisProgress[]
  durationMs: number
  symbol: string
}
```

### 3. New Component: `AnalysisProgress.vue`

Location: `dashboard/src/components/AnalysisProgress.vue`

Props:
- `stages: AnalysisProgress[]` — all stage progress records
- `currentStage: number` — currently running stage index
- `isComplete: boolean` — analysis finished
- `durationMs: number` — total execution time
- `error: string | null` — error message if any

Features:
- Progress bar (0-100% based on completed stages)
- Stage list with icons (spinner/checked/error)
- Current stage highlighted with pulse animation
- Total duration shown on completion
- Error state with retry button

### 4. Updated `SentimentView.vue`

Changes:
- Replace `analyzeComposite()` with new orchestration call
- Show `AnalysisProgress` component during analysis
- On completion, display full composite results (existing components)
- On error, show error message with stage info
- Keep "View Sentiment" button for loading existing data

New flow:
```
User enters symbol → clicks "Analyze"
  → AnalysisProgress shows (stages 1-7)
  → Each stage: name, status icon, message
  → On complete: hide progress, show ScoreCard + SourceBreakdown + TechnicalIndicators + FundamentalsPanel + BacktestPanel
  → On error: show error with which stage failed
```

### 5. Updated `client.ts`

Add import for new types:
```typescript
import type { AnalysisProgress, FullAnalysisResult } from './types'
```

## File Changes Summary

### Create (5 files):
| File | Location | Purpose |
|------|----------|---------|
| `AnalysisProgress.java` | `backend/api/src/main/java/com/swingtrade/api/dto/` | Progress DTO |
| `FullAnalysisResult.java` | `backend/api/src/main/java/com/swingtrade/api/dto/` | Result DTO |
| `AnalysisOrchestratorService.java` | `backend/api/src/main/java/com/swingtrade/api/service/` | Pipeline logic |
| `AnalysisOrchestrationController.java` | `backend/api/src/main/java/com/swingtrade/api/controller/` | SSE endpoint |
| `AnalysisProgress.vue` | `dashboard/src/components/` | Progress UI component |

### Modify (4 files):
| File | Changes |
|------|---------|
| `dashboard/src/api/types.ts` | Add `AnalysisProgress`, `FullAnalysisResult` types |
| `dashboard/src/api/client.ts` | Add `runFullAnalysis()` async generator |
| `dashboard/src/views/SentimentView.vue` | Replace analyze logic with orchestration, add progress UI |

## Error Handling

- **Per-stage**: If a stage fails, emit error progress and continue to next stage
- **Per-stage timeout**: Each stage has a timeout (backfill: 60s, LLM: 120s, backtest: 30s)
- **Total timeout**: 5 minutes for entire pipeline
- **Client disconnect**: SSE handles automatically via `onCompletion`/`onTimeout`/`onError`
- **Partial results**: If composite fails but TA succeeded, still return partial data

## Testing Strategy

### Backend unit tests:
- `AnalysisOrchestratorServiceTest` — mock each stage, verify progress emissions
- Verify skip logic (e.g., skip backfill if >=100 candles)
- Verify error handling (stage failure doesn't kill pipeline)

### E2E tests:
- `POST /api/analysis/run-full?symbol=RELIANCE` — verify SSE stream
- Verify progress events arrive in order
- Verify final result contains all fields

### Manual testing:
- Run on symbol with no data (full pipeline)
- Run on symbol with existing data (skip stages)
- Verify dashboard shows progress stages correctly
- Verify error handling with invalid symbol

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| LLM analysis takes too long | Configurable timeout, emit progress at key points |
| Backfill blocks other requests | Run in async thread, don't block servlet thread |
| SSE connection drops mid-stream | Client reconnects, partial results still returned on final event |
| Memory pressure from many concurrent analyses | Bounded thread pool for async execution |
| Database connection timeout during long pipeline | Use transaction template, keep DB ops short |

## Implementation Order

1. **Backend DTOs** — `AnalysisProgress.java`, `FullAnalysisResult.java`
2. **Backend Service** — `AnalysisOrchestratorService.java`
3. **Backend Controller** — `AnalysisOrchestrationController.java` (SSE endpoint)
4. **Backend Tests** — unit tests for orchestrator
5. **Frontend Types** — add to `types.ts`
6. **Frontend API Client** — `runFullAnalysis()` in `client.ts`
7. **Frontend Component** — `AnalysisProgress.vue`
8. **Frontend View** — update `SentimentView.vue`
9. **E2E Tests** — verify full pipeline end-to-end
10. **Manual Testing** — run on live data