# Full Analysis Orchestration Plan

## Overview
Create a single "Analyze" button that runs the complete pipeline server-side with streaming progress updates via SSE (Server-Sent Events). The dashboard shows a progress bar with 7 stages.

## Pipeline Stages
1. Checking data completeness...
2. Backfilling OHLCV candles...
3. Ingesting news articles...
4. Running LLM sentiment...
5. Computing technical indicators...
6. Calculating composite score...
7. Running backtest...
8. Analysis complete

## Backend Changes

### 1. New DTO: `AnalysisProgress`
- `stage` (string): stage name
- `stageNumber` (int): 1-8
- `status` (string): "running", "completed", "skipped", "error"
- `message` (string): human-readable status
- `timestamp` (string): ISO datetime

### 2. New DTO: `FullAnalysisResult`
- All fields from `CompositeAnalysis`
- Plus `progress` (list of AnalysisProgress)
- Plus `durationMs` (total execution time)

### 3. New Service: `AnalysisOrchestratorService`
- `runFullAnalysis(String symbol)`: runs all stages sequentially
- Each stage checks if data exists before running
- Emits progress events via SynchronousEventPublisher or callback
- Skips stages if data already exists (e.g., if 100+ candles already present, skip backfill)
- Returns FullAnalysisResult with progress history

### 4. New Endpoint: `POST /api/analysis/run-full`
- Returns SSE stream with progress events
- Final event contains the complete FullAnalysisResult
- Accepts optional query params: `?skipBackfill=false&forceNews=false`

### 5. New Endpoint: `GET /api/analysis/status/{runId}`
- For polling if needed (fallback to SSE)

## Dashboard Changes

### 1. New API client function
- `runFullAnalysis(symbol: string): AsyncIterable<AnalysisProgress | FullAnalysisResult>`
- Uses EventSource or fetch with ReadableStream

### 2. Updated SentimentView.vue
- Replace existing "Analyze" button logic with orchestration call
- Show progress bar with stage indicators
- Each stage shows: icon, name, status (spinner/checked/error)
- On completion, display full composite results
- On error, show error message with which stage failed

### 3. Progress UI Components
- `AnalysisProgress.vue`: shows current stage, progress bar, stage list
- Reuses existing ScoreCard, SourceBreakdown, etc. for results

## Files to Create/Modify

### Create:
- `backend/api/src/main/java/com/swingtrade/api/dto/AnalysisProgress.java`
- `backend/api/src/main/java/com/swingtrade/api/dto/FullAnalysisResult.java`
- `backend/api/src/main/java/com/swingtrade/api/service/AnalysisOrchestratorService.java`
- `backend/api/src/main/java/com/swingtrade/api/controller/AnalysisOrchestrationController.java`
- `dashboard/src/components/AnalysisProgress.vue`

### Modify:
- `dashboard/src/views/SentimentView.vue` — single button, progress UI, results display
- `dashboard/src/api/client.ts` — new orchestration API function
- `dashboard/src/api/types.ts` — new types

## Key Design Decisions
- Use SSE (Server-Sent Events) for streaming progress — simpler than WebSocket for server→client
- Each stage is checked for prerequisites before running (e.g., need 100+ candles for TA)
- Backfill only runs if <100 candles exist
- News ingestion only runs if no articles found for symbol
- LLM sentiment only runs if no recent sentiment result exists
- All stages run sequentially in one transaction-like flow
- Total execution time tracked and reported