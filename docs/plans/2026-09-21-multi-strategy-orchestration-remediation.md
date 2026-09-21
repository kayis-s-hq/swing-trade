# Multi-Strategy Orchestration Remediation — 2026-09-21

Status: **Draft, awaiting owner approval**
Extends: `2026-09-16-configurable-multi-strategy.md` (this plan closes the gap between its Phases 1–3 design and what the live orchestrator does)
Related: `2026-08-06-job-orchestrator.md`, `2026-08-30-llm-analysis-stage-verification.md`, `2026-09-14-post-stage-follow-ups.md`
Primary goal: **a manual/scheduled run must actually evaluate every configured strategy, and any degradation must be visible, not hidden behind a green COMPLETED.**

---

## 1. Evidence (local run, `main` @ ed95365b, 2026-09-21)

Run `6491092e-c68a-41fa-8ed6-782afa34df56`, triggered with `POST /api/job/runs/start`, 10 symbols, local API + pi-node Postgres, local Ollama.

Configs in DB (all `current=true`): `breakout-v1` (BREAKOUT), `pullback-v1` (PULLBACK), `squeeze-v1` (SQUEEZE). All mode **SHADOW**; no CHAMPION.

| # | Observation | Evidence |
|---|---|---|
| E1 | All 3 strategies skipped for every symbol | Log: `Skipping configured strategy pullback-v1: unsupported strategy type PULLBACK (fail-closed)`; SIGNAL stage = `0 configured signal(s) generated` on 10/10 symbols |
| E2 | LLM returns empty text | Log: `Chat completion complete, received 0 chars` → `LLM returned an empty response`; model `qwen3.5:4b` via Ollama `localhost:11434/v1` |
| E3 | Sentiment silently degrades | Every symbol: `NEUTRAL, confidence 0.5` (keyword fallback) while stage status = COMPLETED |
| E4 | LLM_ANALYSIS is a no-op but "succeeds" | `recommendation=null, confidence=0.0`, 142–208 s per symbol, status COMPLETED |
| E5 | Stale/misleading PAPER_TRADE text | `selected: deferred pullback-v1 (awaiting sentiment)` although pullback-v1 was skipped |
| E6 | `GET /api/strategy-types` → 404 | Documented in `StrategyTypeRegistry` javadoc; no controller exists |
| E7 | Run is slow | ~3–4 min/symbol (LLM stages dominate), ~30+ min for 10 symbols |
| E8 | `dev-stack.sh` flips Docker context to `desktop-linux` | Seen after `status` and in `start`/`stop`; violates the pi-node rule (memory: `feedback_docker_context`) |

## 2. Root causes

**RC1 — two strategy SPIs, orchestrator on the wrong one (E1, E5, E6).**
- `StrategyRegistry` (`strategy/StrategyRegistry.java`) indexes the *legacy* `TradingStrategy` beans by their `NAME` (`PRICE_ACTION`, `PULLBACK_UPTREND`, `VOLATILITY_SQUEEZE`, `52W_HIGH_BREAKOUT`, …).
- The Phase 1–3 strategies are `SignalStrategy` beans (`LegacyPriceActionAdapter`=BREAKOUT, `PullbackStrategy`=PULLBACK, `SqueezeStrategy`=SQUEEZE), indexed by `StrategyTypeRegistry`.
- `JobOrchestratorService.stageSignal` (~L581–600) calls `strategyRegistry.resolve(config)` and `SignalPipeline.generateConfiguredSignal(symbol, config, TradingStrategy, champion)`. Neither knows `SignalStrategy`. `StrategyConfig.strategyType` values (`BREAKOUT`/`PULLBACK`/`SQUEEZE`) never match → fail-closed skip.
- Design principle 6 of the multi-strategy plan ("live and backtest share one evaluation path `SignalStrategy.evaluate`") is not implemented for the live path.

**RC2 — thinking model exhausts the token budget (E2–E4).** `SpringAiLlmClient` sends `maxTokens=1024`, `temperature=0`. `qwen3.5:4b` reasons first; if content is empty the client only recovers from a vLLM `reasoningContent` metadata field, not Ollama's `reasoning` field. (Hypothesis — confirm in Step 2.0 before fixing.)

**RC3 — no "degraded" state in the stage model (E3, E4, E5).** Stage status is binary COMPLETED/FAILED; fallbacks and skips are only logged.

**RC4 — no per-run scoping and no parallelism (E7).** `/start` takes only `triggerType`; symbols/strategies/LLM cannot be narrowed, so verification is 30+ min.

**RC5 — script uses `docker context use` (E8).**

## 3. Scope

In scope: RC1–RC5, the API/UI surface that exposes them, and the validation harness.
Out of scope: new strategy families (RS_NIFTY, mean reversion), walk-forward/portfolio backtest upgrades (multi-strategy plan Phase 4+), real-order champion promotion, universe expansion, auth.

## 4. Work plan

Order is by dependency and value. Each step is test-first (`tdd-*` workflow, agent routing in `CLAUDE.md`) and leaves `main` green.

### Step 0 — Baseline & guard rails (½ day)
- 0.1 Add a **contract test** `ConfiguredStrategyResolvableTest` (api or strategy module, H2): for every `StrategyConfig.strategyType` accepted by `StrategyConfigController` validation, live resolution must return a strategy. Initially **RED** — reproduces E1.
- 0.2 Confirm RC2 with a one-off `curl` to Ollama `/v1/chat/completions` with the sentiment prompt; record where the text lands (`content` vs `reasoning`) and whether `finish_reason=length`. Write result into §7.
- 0.3 Snapshot current behavior: save the run summary JSON from `6491092e-…` under `docs/analysis/` for before/after comparison.

### Step 1 — Unify live evaluation on `SignalStrategy` (RC1) — P0 (2–3 days)
1. **Resolver**: add `StrategyResolver` (strategy module) with `resolve(StrategyConfig) → ResolvedStrategy` where `ResolvedStrategy` is either a `SignalStrategy`+params view (new types) or a legacy `TradingStrategy` (old types such as `PRICE_ACTION_3_OF_4`, `RS_NIFTY_MOMENTUM`). Lookup order: `StrategyTypeRegistry` then legacy `StrategyRegistry`. Unknown type → typed `UnresolvedStrategy(reason)` result, never `null`/empty.
2. **Pipeline**: change `SignalPipeline.generateConfiguredSignal` to accept `ResolvedStrategy`; for `SignalStrategy` build `MarketContext` from candles (+ index series when `requiredIndicators` include `INDEX_*`), call `evaluateEntry(ctx, lastBar, params)`, persist signal with `strategy_id`, `strategy_version`, params hash (provenance, plan §0.1 #1). Reuse `ParamSchemaValidator` for params.
3. **Orchestrator**: replace the `resolve`/`Optional` block in `stageSignal` with the resolver; collect per-variant outcomes as `EVALUATED(signal|no-signal, score, rules)`, `SKIPPED(reason)`, `ERROR(msg)`.
4. **Backtest parity**: confirm `BacktestEngine` calls the same `evaluateEntry` (plan principle 6). Add a golden test: for a fixed candle fixture, live last-bar decision == backtest decision at that bar (extends `LegacyPriceActionAdapterGoldenParityTest`).
5. **Exits**: the orchestrator's SELL path (`~L915–937`, `liveConfigs()` loop) must use `evaluateExit` for `SignalStrategy` variants — verify open SHADOW positions close under the same resolver.
6. **Fail-closed stays** but is no longer silent (Step 3).

Acceptance: re-run the E1 scenario → SIGNAL stage reports `3 evaluated, N signals, 0 skipped`; test from 0.1 is GREEN; ArchUnit module-boundary tests pass (strategy must not depend on api/data).

### Step 2 — LLM empty-response handling (RC2) — P0 (1–2 days)
1. Per 0.2 result, apply the smallest fix: disable thinking for Ollama (`think:false`/model option or a non-thinking model), and/or raise `maxTokens` for reasoning models, and/or extend the reasoning-field extraction to Ollama's field name.
2. Treat 0 chars after recovery as a **failure**: one bounded retry, then `LlmUnavailableException`.
3. Sentiment fallback stays (keyword analysis) but returns `source=KEYWORD_FALLBACK` and `degradedReason` instead of a bare score. `LLM_ANALYSIS` with `recommendation=null` becomes stage status `DEGRADED` (Step 3), not COMPLETED.
4. Add per-stage timeout config (`llm.stage-timeout`) so a hung local model can't hold a run for hours.
Tests: `SpringAiLlmClient` with a stubbed ChatClient returning empty content / reasoning-only content / normal; `SentimentService` fallback metadata.

### Step 3 — Honest stage status: `DEGRADED` & per-strategy outcomes (RC3) — P1 (1–2 days)
- Extend stage status enum + `JobRunStageResponse` with `DEGRADED` (completed with fallback/skip) and a structured `details` JSON: `{strategies:[{variantId, version, outcome, reason?, score?}], source:"LLM|KEYWORD_FALLBACK", warnings:[…]}`.
- Flyway migration (next free `V__`) adds nullable `details jsonb`/`text` to the job-run-stage table; **reviewed by `migration-reviewer`**; backwards compatible (nullable, no backfill).
- Run status: `COMPLETED_WITH_WARNINGS` when any stage DEGRADED; counts exposed in `/summary` (`degradedStages`, `skippedStrategies`).
- Fix E5: PAPER_TRADE text derives from real outcomes only; never names a skipped variant.
- Metrics: extend `JobOrchestratorMetrics` with `strategy_skipped_total{variant,reason}`, `stage_degraded_total{stage,reason}`; alert rule in the monitoring stack for `strategy_skipped_total > 0`.
- Discord run summary lists degraded stages and skipped strategies.

### Step 4 — Run scoping, speed, and dry run (RC4) — P1 (2 days)
- `POST /api/job/runs/start` optional body/params: `symbols[]`, `variantIds[]`, `stages[]` (or `skipLlm=true`), `dryRun=true` (evaluate + report, no persistence of signals/paper trades). Defaults preserve current behavior (existing callers unchanged); validate ids → 400 on unknown.
- Persist the request on the run (`trigger_options` column, nullable) for provenance.
- Bounded parallelism across symbols (`orchestrator.parallelism`, default 1 → opt-in 3), LLM stages behind a semaphore sized to the LLM backend's concurrency. Keep cancel/interrupt semantics from `2026-08-27-job-orchestrator-cancel-interrupt.md`.
- Add `GET /api/strategy-types` (E6) returning `type`, `paramSchema`, `warmupBars`, `requiredIndicators` — also feeds the UI params editor (Step 5). Fix the javadoc either way.
- Champion guard: log/UI warning when zero or >1 CHAMPION exists (`championSeen` logic today silently downgrades).

### Step 5 — Dashboard (frontend-dev) — P1/P2 (3–4 days)
Views: `OrchestratorView`, `StrategiesView`, `SignalsView`, `SymbolDetailView`, `StrategyReportView`.
1. **Orchestrator start panel**: strategy multi-select, symbol filter, "Quick run (no LLM)", dry-run toggle; disabled + tooltip while a run is active (409 handling exists server-side).
2. **Run detail**: per-symbol × per-strategy matrix (evaluated/signal/no-signal/skipped/error with reason), DEGRADED badge on stages, warnings banner, ETA from stage-duration medians.
3. **Strategies view**: mode toggle SHADOW/CHAMPION (uses `PUT /api/strategy-configs/{id}/mode`), params editor driven by `/api/strategy-types` schema with server validation errors inline, new-version diff (immutable versions), warning when no/multiple CHAMPION.
4. **Sentiment/LLM cards**: show source (LLM vs keyword fallback) and degraded reason.
5. **Symbol detail**: reuse `/api/symbols/{symbol}/strategy-matrix` for side-by-side variant comparison.
6. Update Vitest tests for each touched view; run Playwright e2e per the UI verification rule; screenshots reviewed with the image-assessor agent.

### Step 6 — Tooling & docs (½ day)
- `dev-stack.sh`: replace every `docker context use …` with `docker --context pi-node …` (or a `DOCKER_CONTEXT` env scoped to the command); add a test/`bin/verify-changes` check that the script contains no `context use`. Ends E8.
- Add `dev-stack.sh run [--symbols=A,B] [--strategies=x,y] [--no-llm]` wrapper that triggers a run and polls to completion, printing the stage matrix (used by Step 7).
- Update `docs/status.md` **only after** Step 7 verification passes; mark this plan's items done; note in `2026-09-16-configurable-multi-strategy.md` §3/§4 that live evaluation now uses `SignalStrategy`; archive completed items per repo policy.

## 5. Validation plan

### 5.1 Automated (must pass before merge, per step)
| Layer | Check |
|---|---|
| Unit | Resolver (each type, unknown type, bad params), `SignalStrategy` live path per strategy, LLM empty/reasoning-only/normal, sentiment fallback metadata, stage-status derivation |
| Contract | `ConfiguredStrategyResolvableTest` (Step 0.1) |
| Parity | Live last-bar decision == backtest decision for the same fixture (per strategy type) |
| Integration (H2) | Orchestrator run with 3 configs + stub LLM → 3 evaluated, provenance columns set, DEGRADED when stub LLM returns empty |
| API | `/start` with filters/dryRun/invalid ids (400), 409 while active, `/strategy-types`, `/summary` degraded counts |
| Migration | `migration-reviewer` sign-off; apply on a copy of the dev DB |
| Architecture | ArchUnit module boundaries (`2026-08-22-ad-h1-archunit-and-module-boundaries.md`) |
| Frontend | Vitest for touched views/stores; `yarn lint`/type-check; Playwright e2e for orchestrator start/monitor and strategies edit |
| Repo gate | `./bin/verify-changes`, `./gradlew test checkstyleMain` |

### 5.2 Local end-to-end verification (repeat of today's scenario)
1. `./dev-stack.sh start` (after Step 6, Docker context stays `pi-node`; check `docker context show`).
2. Fast pass: `./dev-stack.sh run --no-llm` (10 symbols, 3 strategies) →
   - SIGNAL stage: `3 strategies evaluated` on every symbol, 0 skipped.
   - Signals table rows carry `strategy_id/version/params_hash` for each variant that fired.
   - Run status `COMPLETED_WITH_WARNINGS` only for the intentionally skipped LLM stages.
3. Full pass: normal run with Ollama → no `received 0 chars` in log; sentiment `source=LLM` (or explicit DEGRADED with reason); `LLM_ANALYSIS` has a real recommendation or a DEGRADED status.
4. Failure injection: (a) set a config `strategyType` to a bogus value → run shows SKIPPED with reason + `strategy_skipped_total` increments + Discord warning; (b) stop Ollama → stages DEGRADED, run still finishes; (c) trigger `/start` twice → second gets 409; (d) cancel mid-run → clean stop.
5. SHADOW behavior: after ≥2 consecutive runs, verify each variant's virtual portfolio opens/closes independently and exits use `evaluateExit`.
6. UI pass (Playwright + manual): start panel with subset of strategies, matrix renders skipped/evaluated states, Strategies mode toggle and params validation errors, degraded badges; desktop and narrow widths.
7. Compare against the baseline snapshot from Step 0.3 and record before/after in `docs/status.md`.

### 5.3 Stage (only after local passes, explicit owner go-ahead)
`./dev-stack.sh stage`, then health + one dry-run and one full run on `http://piworm.local:8081`; confirm the native image still starts (new beans/reflection hints) and the monitoring alert for skipped strategies fires in a forced case.

## 6. Risks & open questions
| Risk / question | Mitigation / default |
|---|---|
| Live and backtest behavior may diverge once live uses `SignalStrategy` (different thresholds than legacy) | Parity golden test; changes ship in SHADOW only — CHAMPION/real orders untouched |
| Signals become visible for the first time from pilot configs → noisy SHADOW trades | Expected; monitor via strategy report; cap of 12 variants unchanged |
| `qwen3.5:4b` may be unfit for the JSON tasks even after fix | Step 0.2 decides: non-thinking model, larger model, or `GPUHub`/MLX backend (PR #94 adds MLX) |
| Migration on shared pi-node dev DB | Copy first; nullable columns only; migration-reviewer |
| Parallelism vs. single local LLM | Default parallelism 1; semaphore for LLM stages |
| **Owner decisions needed:** (a) status name `DEGRADED` vs `COMPLETED_WITH_WARNINGS` only; (b) should any strategy skip **fail the run** in stage/prod (recommend: fail in stage, warn in dev); (c) keep legacy `TradingStrategy` types (`PRICE_ACTION_3_OF_4`, `RS_NIFTY_MOMENTUM`) resolvable long-term or migrate them to `SignalStrategy` (recommend: migrate, then delete the legacy registry in a follow-up) | Decide before Step 3 / Step 1 respectively |

## 6.5. Decisions logged

- **Authentication skipped.** Adding Spring Security API-key auth (audit finding AD-P0-2, and `docs/issues/014`) is deferred for now. Not in scope for the remediation plan; revisit in a dedicated auth effort.

## 7. Findings log (fill during execution)
- Step 0.2 Ollama raw response analysis: _pending_
- Step 0.3 baseline snapshot path: _pending_
- Final verification run id / date / result: _pending_

## 8. Estimated sequence
Step 0 → Step 1 (unblocks everything) → Step 2 (parallelizable with 1, different module) → Step 3 → Step 4 → Step 5 → Step 6 → Step 5.2/5.3 verification. Roughly 2 working weeks single-threaded; Steps 1 and 2 can be split across `backend-dev` agents, Step 5 to `frontend-dev` once Step 3/4 API shapes are agreed.
