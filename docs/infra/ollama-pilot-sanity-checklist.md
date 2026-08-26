# Ollama LLM Pilot — Pre-Stage Sanity Checklist

Work top-to-bottom, one item at a time. Tick each box only when the item passes.
**Section A is a hard gate** — if Ollama connectivity fails, stop; the rest will silently
run on the sentiment fallback (see "Hard gates" at the bottom).

Context: LLM backend configured to **Ollama** (local server) for a pilot run to exercise
dashboard functionality before pushing to stage.

---

## A. Ollama connectivity / readiness (GATE — do first)

The app never starts Ollama itself — `LlmServerManagerProvider.getManager()` returns
`null` for the `OLLAMA` backend (`backend/llm/.../LlmServerManagerProvider.java:33`).
Ollama must be running and the model loaded **externally**.

- [ ] **A1. Ollama server is up.** `ollama serve` is running on the host.
- [ ] **A2. Model is loaded.** `curl http://localhost:11434/v1/models` lists the model
      you intend to use (default `qwen3:4b`).
- [ ] **A3. Config is in the DB, not properties.** `app_settings` has
      `ollama.base_url` (default `http://localhost:11434/v1`), `ollama.model`
      (default `qwen3:4b`), and `llm.backend=ollama`.
      (`LlmConfig.ollamaChatModel` reads these — `LlmConfig.java:62-67`.)
- [ ] **A4. Inference probe passes.** `POST /api/settings/test/ollama`
      (`SettingsController.java:266`) returns a coherent reply.
      UI: `SettingsView.vue` → select **Ollama** → set base URL + model → **Test** → green result.
- [ ] **A5. Do NOT rely on `/api/health/llm`.** `HealthController.java:131` only reports
      the `SentimentService` bean is present — it does **not** confirm Ollama is reachable.

**Gate check:** A1–A4 all ticked before proceeding to B.

---

## B. LLM-dependent dashboard features to exercise

`SentimentService.analyzeStockSentiment` (`SentimentService.java:117`) is the primary LLM
call site. For each feature below, **pass = real LLM output, not the 0.1/0.2 fallback**
(the fallback is the "no valid response"/timeout path and masks a dead Ollama).

- [ ] **B1. SentimentView.** `/sentiment/{symbol}/latest` + `/history` render in
      SentimentTimeline / SentimentBadge; AccuracyMetricCard populates.
      Pass: a real score + confidence, not the 0.1/0.2 fallback.
- [ ] **B2. NewsView + ArticleBrowser.** `GET /api/news/{symbol}/latest` returns articles
      (these feed the sentiment prompt). Verify articles load **before** running sentiment.
- [ ] **B3. SignalsView / SignalCard.** `SignalPipeline` runs `SentimentGate` with a
      `SUPPRESS` action (`SignalPipeline.java:103-109,150-156`).
      Pass: signals appear AND any suppressed signal is explained.
- [ ] **B4. OrchestratorView (full analysis).** `POST /api/analysis/run-full`
      → `AnalysisOrchestratorService` → `SynthesisService` (second LLM call site).
      Pass: StageSynthesisDetail / AnalysisAccordion show real LLM synthesis text.
- [ ] **B5. MonitoringView.** HealthStatus + LlmMetrics counters update
      (call count, success, sentiment-analyzed).

---

## C. End-to-end pipeline

- [ ] **C1. Trigger a full job run.** `POST /api/job/runs/start`
      → `JobOrchestratorService` 6 stages: DATA_FETCH, NEWS, SENTIMENT, SIGNAL,
      BACKTEST, PAPER_TRADE (`JobOrchestratorService.java:237-247`).
- [ ] **C2. All six stages COMPLETED** in OrchestratorView — no stage stuck in PENDING/ERROR.
- [ ] **C3. LLM-critical stages are real.** NEWS, SENTIMENT, and SIGNAL produced genuine
      output — SENTIMENT shows a real score, not an error or the fallback.

---

## D. Pre-stage automated gates

- [ ] **D1. Backend check.** `cd backend && ./gradlew check`
      (unit + integration + checkstyle + PMD; Java 21 via sdkman).
- [ ] **D2. Coverage.** `./gradlew jacocoTestCoverageVerification` (80% line threshold).
- [ ] **D3. Frontend.** `cd dashboard && yarn typecheck && yarn test:run`.
- [ ] **D4. ArchUnit.** `./gradlew :api:test --tests=ModuleBoundaryTest`.
- [ ] **D5. Migration.** `V26__add_broker_position_id_to_positions.sql` applies cleanly on
      the stage DB (continues after V25).
- [ ] **D6. Stage config trap.** `application-stage.properties` sets only `llm.retry.*` —
      `llm.backend` / `ollama.*` come from the **stage DB `app_settings`**, not the
      properties file. Confirm they are set in the **stage** DB, not just locally.
- [ ] **D7. Working tree.** `git status` — LLM-module + dashboard changes committed before
      push; confirm `infra/env/.env` is **not** staged.

---

## Hard gates (the two that matter most)

The sentiment fallback returns a plausible 0.1/0.2 score instead of erroring when Ollama is
dead — so a "working" dashboard can be silently running **without** the LLM. Your two
non-negotiable checks:

1. **`POST /api/settings/test/ollama`** returns a real coherent reply (A4).
2. **A real SENTIMENT stage score** in a full job run — not the fallback (C3).

If both pass, Ollama is genuinely in the loop. Push to stage.
