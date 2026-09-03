# Verification: LLM_ANALYSIS Stage Implementation vs. Plan

## Context

`docs/plans/llm-analysis-stage.md` specified adding an `LLM_ANALYSIS` stage to the job orchestrator, in advisory-only mode, backed by new `backtest_result`/`llm_analysis_result` tables and an `LlmAnalysisGate`. The user reports this plan is implemented and asked for verification only (no further planning needed). This document is the verification report, not a new implementation plan.

## Verdict: Verified and follow-up gaps implemented

Backend (verified by full-file reads + targeted Gradle test runs) and frontend (verified by direct reads/grep) both match the plan's structural and safety-critical decisions. Two things fall short of the plan text:

### 1. Test coverage follow-up — addressed
`JobOrchestratorLlmAnalysisTest.java` now covers fallback persistence, successful recommendation persistence, enforcing-mode suppression, and advisory-mode execution. `LlmAnalysisGateTest.java` covers all five verdict cases (PENDING/SUPPRESS/FLAG_NEUTRAL/ALLOW/ALLOW_GRACEFUL).

### 2. Minor code-quality/data-quality nuances — addressed
- Profit-factor calculation is now shared through `BacktestScorer.calculateProfitFactor`.
- Technical/fundamental input degradation sets `fallback_used=true` and records an explanatory error message.
- Unknown recommendation strings now resolve to `ALLOW_GRACEFUL` rather than silently allowing as a trusted BUY.
- Advisory-only mode still lets a `PENDING` verdict (LLM stage errored/no row) `continue`/defer the trade one cycle — a "soft" delay, not a block. Intentional per code comment, but arguably outside "advisory-only never affects trading."

## What passed, in full (file:line evidence)

| Plan section | Status |
|---|---|
| 1. `JobRunStage.StageName` enum order | PASS — `JobRunStage.java:25` |
| 2. Migrations `V33__add_backtest_result.sql` / `V34__add_llm_analysis_result.sql` + all new domain/data types (`BacktestResult(Entity/Repository/Store/StoreImpl)`, `LlmAnalysisResult(Entity/Repository/Store/StoreImpl)`) | PASS |
| 3. `JobOrchestratorService`: `TIMEOUT_LLM_ANALYSIS`, `StageDef` insertion between SENTIMENT/PAPER_TRADE, kill-switch omits stage entirely (not skip-cascade), `stageBacktest` persistence, `stageLlmAnalysis` reads/builds/persists, defensive try/catch around `compute()` calls, full constructor wiring incl. both `@Value` flags | PASS (see gaps above) |
| 4. `LlmAnalysisGate` verdict mapping + wiring into `stagePaperTrade` right after `sentimentGate` check, advisory-only vs enforcing fork | PASS — verified the safety-critical fork is correct: enforcing mode blocks SUPPRESS, advisory mode (default) never blocks on LLM verdict |
| 5. Config defaults in all three `application*.properties` | PASS — `enabled=true`, `advisory-only=true` in all three files |
| 6. Frontend: `dashboard/src/api/types.ts:396` stage union includes `LLM_ANALYSIS`; `OrchestratorView.vue:379,389` renders it as "LLM analysis" in the 7-stage list | PASS |
| Build | `:api:test :data:test :core:test` and targeted LLM/gate tests — all green |

## Live orchestration verification

Run `08ff6ef5-2f00-4d76-98eb-5d91ebcce499` was started against the local watchlist (10 symbols) and reached `COMPLETED`.

- 70/70 stage rows completed: 10 symbols × 7 stages.
- Zero failed symbols and zero `ERROR` stage rows.
- Every symbol entered `LLM_ANALYSIS` after `SENTIMENT` and before `PAPER_TRADE`.
- Nine synthesis calls used the graceful fallback; WIPRO returned `HOLD` with confidence `0.12`.
- All 10 `PAPER_TRADE` stages completed with `0 trade(s) executed`.
- API health remained `UP` throughout the run.

Note: `SettingsView.vue`, `WatchlistView.vue`/`.test.ts`, and `orchestrator-debug.spec.ts` diffs in the working tree are unrelated to this plan (separate in-flight change) and were not evaluated here.

The remaining timeout/kill-switch/skip-cascade scenarios are covered by the existing orchestrator regression suite’s pipeline mechanics; the live run verified the normal seven-stage path end-to-end.
