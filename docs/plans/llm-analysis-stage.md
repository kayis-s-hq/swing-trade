# Add LLM_ANALYSIS Stage to Job Orchestrator

## Context

The job orchestrator (`JobOrchestratorService`) currently runs 6 stages per symbol: `DATA_FETCH → SIGNAL → BACKTEST → NEWS → SENTIMENT → PAPER_TRADE`. Each stage runs independently and reads/writes persisted artifacts (`SignalStore`, `SentimentStore`), with `SentimentGate.evaluatePersisted(...)` gating whether `PAPER_TRADE` actually executes a BUY signal.

We want a final synthesis step: an LLM that looks at everything the pipeline has already produced (technical signal, sentiment, backtest performance, fundamentals) and issues one final buy/sell/hold verdict — a second, LLM-driven gate on top of the existing sentiment gate, before a trade is placed. The building blocks largely already exist (`SynthesisService`, `CompositeAnalysisService`) but are currently only wired into a separate on-demand/SSE pipeline (`AnalysisOrchestratorService`), never into the unattended watchlist job orchestrator. Because this stage can suppress real paper trades, it ships in **advisory-only mode** first (verdicts recorded but never block), promoted to enforcing later once verdict quality is manually validated.

## Decisions

- **Placement**: new `LLM_ANALYSIS` stage runs after `SENTIMENT`, before `PAPER_TRADE`.
- **LLM service**: reuse `SynthesisService` + `CompositeAnalysisService` (already built for exactly this shape of composition) rather than a new LLM client.
- **Persistence**: new tables via Flyway migrations, not bolted onto `signals` or the existing `llm_analysis_audit` table.
- **Failure handling**: `SynthesisService.synthesize()` never throws — it self-falls-back internally (`success=false`). The stage is always marked COMPLETED; failure surfaces as a `fallback_used` flag, never an ERROR/skip-cascade.
- **No Discord notification** for this stage (future work).
- **Backtest artifact**: currently only free text (`resultSummary`) — add minimal structured persistence for backtest results so this stage (and the LLM prompt) can read real numbers instead of parsing text.
- **Rollout**: `advisory-only=true` by default (gate evaluates and persists verdicts, but never blocks a trade) plus a separate `enabled` kill-switch to disable the stage outright.

## Changes

### 1. Enum

`backend/core/src/main/java/com/swingtrade/domain/JobRunStage.java`
```java
public enum StageName { DATA_FETCH, SIGNAL, BACKTEST, NEWS, SENTIMENT, LLM_ANALYSIS, PAPER_TRADE }
```
Keep the javadoc note about enum order matching `processSymbol()`'s stage list order.

### 2. New Flyway migrations (`backend/data/src/main/resources/db/migration/`)

**`V33__add_backtest_result.sql`** — structured backtest artifact, one row per (symbol, run_date), written by `stageBacktest` after `backtestEngine.runBacktest(...)`, upserted on `(symbol, run_date)`:
```sql
CREATE TABLE IF NOT EXISTS backtest_result (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    run_date DATE NOT NULL,
    total_trades INTEGER NOT NULL,
    winning_trades INTEGER NOT NULL,
    losing_trades INTEGER NOT NULL,
    win_rate DOUBLE PRECISION NOT NULL,
    avg_gain_pct DOUBLE PRECISION NOT NULL,
    avg_loss_pct DOUBLE PRECISION NOT NULL,
    max_drawdown_pct DOUBLE PRECISION NOT NULL,
    sharpe_ratio DOUBLE PRECISION NOT NULL,
    total_return DOUBLE PRECISION NOT NULL,
    expectancy DOUBLE PRECISION NOT NULL,
    profit_factor DOUBLE PRECISION NOT NULL,
    has_enough_data BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_backtest_result_symbol_date UNIQUE (symbol, run_date)
);
CREATE INDEX IF NOT EXISTS idx_backtest_result_symbol_date ON backtest_result(symbol, run_date);
```
On the `BacktestScorer`-style insufficient-data path, persist `has_enough_data=false` with zeroed metrics rather than skipping the row, so reads always find something for the day.

**`V34__add_llm_analysis_result.sql`** — the LLM stage's decision record, one row per (symbol, run_date):
```sql
CREATE TABLE IF NOT EXISTS llm_analysis_result (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL,
    recommendation VARCHAR(20),
    confidence DOUBLE PRECISION,
    narrative TEXT,
    key_drivers TEXT,
    bullish_factors TEXT,
    bearish_factors TEXT,
    composite_score INTEGER,
    composite_signal VARCHAR(20),
    success BOOLEAN NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_llm_analysis_result_symbol_date UNIQUE (symbol, analysis_date)
);
CREATE INDEX IF NOT EXISTS idx_llm_analysis_result_run_id ON llm_analysis_result(run_id);
```
Follow whatever list-encoding convention `SentimentResult`'s entity already uses (JSON vs newline-joined) for `key_drivers`/`bullish_factors`/`bearish_factors` — mirror it exactly rather than inventing a new one. No raw-prompt audit table is added in this iteration (scope cut — `SynthesisService` doesn't audit its own calls today either); call this out explicitly as intentional in the PR.

New domain/data types, following the `SentimentStore`/`SentimentResult` split (core interface, data-module JPA impl):
- `BacktestResultEntity`, `BacktestResultRepository`, `BacktestResultStore`
- `LlmAnalysisResultEntity`, `LlmAnalysisResultRepository`, `LlmAnalysisResultStore`

### 3. `JobOrchestratorService.java`

- Add `TIMEOUT_LLM_ANALYSIS = 600L` constant (mirrors `TIMEOUT_SENTIMENT`, matches `SynthesisService`'s own 600s internal timeout); include it in `STAGE_TIMEOUT_SUM_SECONDS`.
- Insert a new `StageDef` for `LLM_ANALYSIS` between the `SENTIMENT` and `PAPER_TRADE` entries in `processSymbol()`'s stage list.
- New `stageBacktest` addition: after a successful backtest run, compute `profitFactor` (extract `BacktestScorer.calculateProfitFactor`'s logic into a reusable method rather than duplicating it) and persist via `backtestResultStore.save(...)`.
- New private method `stageLlmAnalysis(String symbol, LocalDate date)`:
  - Reads `technicalAnalysisService.compute(symbol)`, `fundamentalScorer.compute(symbol)`, `backtestResultStore.findBySymbolAndDate(symbol, date)` (mapped to `CompositeAnalysis.BacktestScore`, or a zeroed/`hasEnoughData=false` fallback if absent), and `sentimentStore.findBySymbolAndDate(symbol, date)`.
  - Builds `CompositeAnalysis` via `compositeAnalysisService.analyze(...)`, then `synthesisService.synthesize(composite)` (never throws).
  - Verify `TechnicalAnalysisService.compute`/`FundamentalScorer.compute`'s own exception behavior before finalizing; if either can throw (unlike `BacktestScorer.compute`, which self-guards), wrap defensively and substitute a neutral score rather than letting the stage ERROR.
  - Persists the full result via `llmAnalysisResultStore.save(...)` (including `fallback_used = !result.success()`).
  - Writes a short human-readable line to `resultSummary`; full structured decision lives in `llm_analysis_result`.
  - Respects the `enabled` config flag: if disabled, the stage isn't added to the `StageDef` list at all (shows as SKIPPED, "disabled by config").

### 4. Gating: new `LlmAnalysisGate`

New `@Component` in `backend/api/.../service/`, structurally mirroring `SentimentGate` (own `evaluatePersisted(symbol, date)` returning a verdict enum: `PENDING` / `SUPPRESS` / `FLAG_NEUTRAL` / `ALLOW` / `ALLOW_GRACEFUL`). Not merged into `SentimentGate` — keeps each gate's fallback semantics independently testable.

- `fallback_used=true` → `ALLOW_GRACEFUL` (LLM unavailable, never blocks).
- `recommendation` SELL/AVOID/STRONG_SELL → `SUPPRESS`.
- `HOLD` → `FLAG_NEUTRAL` (allows, flags for dashboard).
- `BUY`/`STRONG_BUY` → `ALLOW`.
- No row found (stage skipped/not yet run) → `PENDING` (same defer semantics as `SentimentGate` PENDING, not treated as suppress).

Wire into `stagePaperTrade` right after the existing `sentimentGate.evaluatePersisted(...)` check, same shape (PENDING → defer/continue, SUPPRESS → mark processed + new `blockedByLlm` counter + continue, otherwise fall through to trade execution).

**Advisory-only mode**: when `job.orchestrator.llm-analysis.advisory-only=true` (default), the gate check in `stagePaperTrade` still runs and is logged/persisted, but its result is always treated as ALLOW for trading purposes — only surfaced on the dashboard. Flip to `false` after a manual validation period to make `SUPPRESS` actually block trades.

### 5. Config

New properties, wired via `@Value` constructor params on `JobOrchestratorService` (same pattern as existing `maxConcurrent`/reaper flags):
```
job.orchestrator.llm-analysis.enabled=true          # kill switch — stage skipped entirely if false
job.orchestrator.llm-analysis.advisory-only=true    # gate observes but doesn't block trades
```

### 6. DI / constructor changes

`JobOrchestratorService` constructor gains: `TechnicalAnalysisService`, `FundamentalScorer`, `CompositeAnalysisService`, `SynthesisService`, `BacktestResultStore`, `LlmAnalysisResultStore`, `LlmAnalysisGate`, plus the two `@Value` flags.

### 7. Tests (`JobOrchestratorServiceTest.java`)

All ~11 existing `new JobOrchestratorService(...)` call sites need the new mocks threaded through — budget for this mechanical cost explicitly.

New tests:
1. Happy path: `synthesize` returns `success=true`/`BUY` → stage COMPLETED, result persisted, PAPER_TRADE still runs (`inOrder`).
2. LLM failure/fallback: `synthesize` returns `success=false` → stage still COMPLETED (not ERROR), `fallback_used=true` persisted, PAPER_TRADE not skipped.
3. Timeout: stage hangs past `TIMEOUT_LLM_ANALYSIS` → stage ERROR, but `PAPER_TRADE` sees `PENDING` from the gate (defers, doesn't suppress) since no row was persisted.
4. Gate suppression (enforcing mode): `recommendation=SELL` → trade not executed, "blocked by LLM analysis" recorded.
5. Advisory-only mode: same SELL verdict, but `advisory-only=true` → trade still executes, verdict only logged.
6. Stage ordering: extend existing `inOrder(...)` test to include `LLM_ANALYSIS` between `SENTIMENT` and `PAPER_TRADE`.
7. Skip-cascade: `SENTIMENT` errors → `LLM_ANALYSIS` and `PAPER_TRADE` both SKIPPED (existing mechanic, extend assertion).
8. Kill switch: `enabled=false` → stage row shows SKIPPED/disabled, not attempted.

New test classes: `LlmAnalysisGateTest` (pure Mockito — PENDING/SUPPRESS/FLAG_NEUTRAL/ALLOW/ALLOW_GRACEFUL cases), plus store-level tests for `BacktestResultStore`/`LlmAnalysisResultStore` if `backend/data` has an existing H2/testcontainers convention for stores (check `SentimentStore`'s test for the pattern).

### Critical files
- `backend/core/src/main/java/com/swingtrade/domain/JobRunStage.java`
- `backend/api/src/main/java/com/swingtrade/api/service/JobOrchestratorService.java`
- `backend/api/src/main/java/com/swingtrade/api/service/SentimentGate.java` (template for `LlmAnalysisGate`)
- `backend/api/src/main/java/com/swingtrade/api/service/CompositeAnalysisService.java`
- `backend/llm/src/main/java/com/swingtrade/llm/service/SynthesisService.java`
- `backend/data/src/main/resources/db/migration/V32__add_llm_analysis_audit.sql` (migration template)
- `backend/api/src/test/java/com/swingtrade/api/service/JobOrchestratorServiceTest.java`

## Verification

- `./gradlew :api:test :data:test :core:test` (or repo's standard test command) — new unit tests above plus full existing suite green.
- Manually run the local dev stack (`dev-stack` skill) against a watchlist symbol, confirm `job_run_stages` shows `LLM_ANALYSIS` between `SENTIMENT` and `PAPER_TRADE`, and `llm_analysis_result`/`backtest_result` rows are populated.
- Confirm advisory-only default: force a SELL verdict (e.g. stub/test data) and verify the trade still executes with the verdict only visible in logs/dashboard.
- Toggle `job.orchestrator.llm-analysis.enabled=false` and confirm the stage is skipped entirely with no behavior change to the rest of the pipeline.
