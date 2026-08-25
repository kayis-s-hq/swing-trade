# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Agent Routing — STRICT ENFORCEMENT

When ANY task matches an agent's domain below, you MUST use that agent via `Agent(subagent_type="...")`. Do NOT handle these tasks in the main context. This is not optional — routing is mandatory for any matching query.

### Routing Table

| When the task involves | Agent | subagent_type |
|------------------------|-------|---------------|
| Backend code, tests, services, domain models, REST endpoints | Backend specialist | `backend-dev` |
| Frontend code, Vue components, views, Pinia stores, API client | Frontend specialist | `frontend-dev` |
| TDD implementation, test-first development, RED-GREEN cycle | Test specialist | `test-writer` |
| Pre-deployment validation (tests, checkstyle, migrations, env vars) | Deploy validator | `deploy-validator` |
| Architecture review, module boundaries, dependency graph, tech debt | Arch auditor | `arch-auditor` |
| Flyway migration review (idempotency, rollback, concurrent safety) | Migration reviewer | `migration-reviewer` |
| Post-deployment verification (health endpoints, E2E tests, smoke tests) | Health check | `health-check` |
| Diff/PR review, bug hunting, security review, simplification | Code reviewer | `code-reviewer` |

### Enforcement Rules

1. **Auto-detect the agent** — Before responding to ANY user query, check if it matches an agent's domain. If yes, spawn the agent. Do NOT handle the task yourself.
2. **No exceptions for small tasks** — Even a one-line backend fix goes through `backend-dev`. Even a one-line frontend change goes through `frontend-dev`.
3. **No exceptions for simple questions** — "What's the Signal class?" → `backend-dev`. "Where is getSignals defined?" → `frontend-dev`.
4. **Multiple agents for multi-domain tasks** — If a task touches both frontend and backend (e.g., "add a new API endpoint and its UI"), spawn BOTH agents in parallel.
5. **Never skip routing** — If a query could be answered by an agent, use the agent. Do NOT answer from CLAUDE.md knowledge alone.
6. **Pass context, not the task** — The agent's file already contains all conventions. Your prompt to the agent should be the specific question, not a re-statement of all conventions.

### When NOT to Route

- Session setup, configuration changes, tool questions → handle directly
- Git operations (commit, push, branch) → handle directly
- Shell commands for file exploration → handle directly
- Questions about this routing table itself → handle directly
- Caveman mode / persona requests → handle directly

### How to Route

```
Agent(description="brief", prompt="specific question", subagent_type="backend-dev")
```

For multi-domain tasks, spawn multiple agents in a single message:
```
Agent(description="backend", prompt="...", subagent_type="backend-dev")
Agent(description="frontend", prompt="...", subagent_type="frontend-dev")
```

## One Active Backend

This repo has **one** active backend:

- **`backend/`** — Multi-module Gradle project (8 modules: core, data, strategy, llm, broker, api, gpuhub) with Spring Boot 4.1.1. Architecture: data ingestion, technical analysis, LLM sentiment, paper trading, GPU deployment management, REST API.

## Java Version

**MUST use Java 21 for Gradle builds.** The system JDK may be Java 25/26, which causes PMD 7.14.0 to crash. `JAVA_HOME` is set in `.zshrc` via sdkman's `current` symlink.

```bash
./gradlew help
```

## Git Hooks

**STRICT: NEVER modify `.git/hooks/` directly.** Git hooks are tracked in `.hooks/` and managed via `git config core.hooksPath`. All hook changes MUST go through `.hooks/`.

**Pre-commit hooks** (`.hooks/pre-commit`):
- Checkstyle on changed Java modules
- Prettier format check on Vue/TS files
- TypeScript typecheck on Vue/TS files
- ESLint --fix on staged Vue/TS files
- Structural checks: enforces correct file placement (no JARs in backend root, no env files outside infra/env/, no Dockerfiles in backend, no build output in source tree, issue files in docs/issues/ not .github/issues/)

**Pre-push hooks** (`.hooks/pre-push`):
- Checkstyle on changed backend modules before push

## Project Structure

```
swing-trade/
├── backend/                # Multi-module Gradle project (Spring Boot 4.1.1)
│   ├── build.gradle.kts    # Root build + dependency management (BOMs, shared plugins, test configs)
│   ├── settings.gradle.kts # Project settings + repository config
│   ├── gradle.properties   # Gradle config (cache, JVM args)
│   ├── config/             # Shared checkstyle + PMD config
│   ├── core/               # Domain models (com.swingtrade.domain: Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult, JobRun, NewsArticle, Order, StrategyParams, etc.)
│   ├── data/               # Data ingestion & storage (Upstox, Fyers, Yahoo, JPA repos, Flyway)
│   ├── strategy/           # TA with TA4j, signal generation, backtesting engine
│   ├── llm/                # LLM client (vLLM/OpenAI-compatible), sentiment analysis, news ingestion
│   ├── broker/             # Paper trading engine, order/position management, risk controls
│   ├── gpuhub/             # GPUHub elastic deployment API client (WebFlux)
│   └── api/                # REST endpoints, scheduled jobs, backtest API, GPUHub controller, metric collectors
│
├── infra/                  # Infrastructure (Docker, env, monitoring, nginx)
│   ├── env/                # Environment files (.env, .env.dev, .env.stage, .env.example)
│   ├── docker-compose.infra-dev.yml   # Dev PostgreSQL on pi-node
│   ├── docker-compose.infra-stage.yml # Stage PostgreSQL on pi-node
│   ├── Dockerfile          # Backend Dockerfile (JVM)
│   ├── Dockerfile.native    # Backend Dockerfile (GraalVM native image)
│   ├── monitoring/         # Prometheus + Grafana config
│   ├── nginx/              # Nginx configs (dashboard, reports)
│   └── dashboard/          # Dashboard Dockerfile
│
├── libs/                   # Shared local JAR dependencies
│   └── fyersjavasdk-1.9.0.jar
│
├── dashboard/              # Vue 3 + TypeScript frontend
│   ├── src/
│   │   ├── api/            # API client, types, config
│   │   ├── components/     # 27 components: cards, badges, status indicators, stage detail views, error boundary, article browser
│   │   ├── views/          # 13 views: Dashboard, Positions, Signals, Portfolio, Watchlist, DataIngestion, Settings, Backtest, Monitoring, News, Orchestrator, Sentiment, NotFound
│   │   ├── router/         # Vue Router
│   │   ├── stores/         # Pinia stores: appState, settings, theme
│   │   ├── composables/    # useAsyncData and other shared composables
│   │   └── test/           # Test utilities and helpers
│   └── tests/              # Vitest unit tests + Playwright E2E tests
│
├── docs/                   # Project documentation
│   ├── plans/              # Implementation plans (handoff artifacts)
│   ├── specs/              # Design specs
│   ├── analysis/           # Architecture audits, orchestration analysis, sentiment redesign, LLM accuracy monitoring
│   ├── api-references/     # External API docs (Fyers v3, Yahoo Finance, GPUHub)
│   ├── infra/              # CI/CD and deployment docs
│   ├── issues/             # 20 feature issues (001-020): watchlist, portfolio, chart, settings, signals, CI, auth, etc.
│   ├── backtesting.md      # Backtest engine entry/exit rules
│   ├── yahoo-finance-api.md # Yahoo Finance unofficial API reference
│   └── status.md           # Project status tracker
│
├── .github/                # GitHub CI/CD workflows
│   └── workflows/          # ci.yml, deploy-main.yml, deploy-stage.yml
│
├── .hooks/                 # Git hooks (pre-commit, pre-push)
├── bin/                    # swingdev CLI wrapper (start/stop/status/logs/infra/wait/profile/dashboard/api)
└── dev-stack.sh            # Dev stack orchestration script
```

## Module Dependencies

```
api -> strategy, llm, broker, gpuhub, data, core
broker -> strategy, data, core
strategy -> data, llm, core
llm -> data, core
data -> core
gpuhub -> (none — standalone)
core -> (none — leaf module)
```

### Module Highlights

| Module | Package Root | Purpose |
|--------|-------------|---------|
| `core` | `com.swingtrade.domain` | Domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult, JobRun, NewsArticle, Order, StrategyParams, RiskCalculator, Exchange) |
| `data` | `com.swingtrade.data` | Data ingestion (Upstox/Yahoo/Fyers), JPA repositories, Flyway migrations |
| `strategy` | `com.swingtrade.strategy` | TA indicator computation with TA4j, signal generation, backtesting engine |
| `llm` | `com.swingtrade.llm` | vLLM/OpenAI-compatible LLM calls, sentiment analysis, news ingestion |
| `broker` | `com.swingtrade.broker` | Paper trading engine, order/position management, risk controls |
| `gpuhub` | `com.swingtrade.gpuhub` | Standalone GPU deployment client (elastic deployments, container templates, DTOs) |
| `api` | `com.swingtrade.api` | REST endpoints, scheduled jobs, Prometheus metrics, health checks, GPUHub controller, signal pipeline orchestration |

## Key Technologies

### Backend
- Java 21 (MUST use via sdkman — NOT Java 25/26)
- Spring Boot 4.1.1 (Jackson 3 / `tools.jackson` BOM 3.1.5, Hibernate 7)
- Gradle 9.6.1 (Kotlin DSL, multi-module build)
- LangChain4j 1.18.1 (LLM integration)
- PostgreSQL 16 + TimescaleDB (time-series)
- TA4j 0.16 (technical analysis)
- Flyway 12.4.0 (migrations consolidated — see DB Migrations)
- Lombok 1.18.34
- ArchUnit 1.4.1 (module boundary enforcement)
- GraalVM native image plugin 0.10.6 (api module, `infra/Dockerfile.native`)

### Frontend
- Vue 3.5 + TypeScript + Composition API
- Tailwind CSS v4
- Vite 6
- Pinia + Vue Router
- ESLint v10 (flat config) + Prettier
- Vitest 3.x unit tests + Playwright 1.60 E2E tests

## Build & Test Commands

### Backend
```bash
cd backend
./gradlew build                          # Build all modules + run tests
./gradlew test                           # Run all unit tests (unit + JaCoCo)
./gradlew :api:test                      # Specific module tests
./gradlew :api:bootRun --args='--spring.profiles.active=local,fyers'  # Run API locally
./run-local.sh                             # Run API locally (sources infra/env/.env, profile `local`)
./gradlew check                          # Tests + PMD + checkstyle + integration tests
./gradlew jacocoTestReport               # Coverage report
./gradlew jacocoTestCoverageVerification # 80% line coverage threshold check
```

### Frontend
```bash
cd dashboard
yarn                          # Install dependencies
yarn dev                      # Dev server (localhost:3003)
yarn build                    # Production build (lint + typecheck + build)
yarn typecheck                # TypeScript check
yarn test                     # Vitest unit tests (watch mode)
yarn test:run                 # Vitest unit tests (run once)
yarn playwright test          # E2E tests
yarn lint                     # ESLint with auto-fix
yarn format:write             # Prettier formatting
```

### Dev Stack
`bin/swingdev` is a CLI wrapper around `dev-stack.sh` (adds `env-check`, `wait`, `profile`, `full-start`, `--profile`/`--port` flags). Both work.

```bash
./dev-stack.sh start           # Start infra on pi-node + Spring Boot + Vue locally
./dev-stack.sh stop            # Stop all local services + infra on pi-node
./dev-stack.sh status          # Check status of infra, backend, frontend
./dev-stack.sh logs            # View infrastructure logs
./dev-stack.sh infra up -d     # Start infra only
./dev-stack.sh infra down      # Stop infra only
./dev-stack.sh frontend start  # Start Vue dev server only
```

## Infrastructure

Infrastructure runs on `piworm.local` via `docker context pi-node`:

| Service | Image | Local Port | Container | Notes |
|---------|-------|------------|-----------|-------|
| PostgreSQL 16 | `postgres:16` | `5435` | `swing_trade_postgres` | TimescaleDB, trust auth |
Network: `swingtrade-network` (bridge). Volumes: `postgres_data`.

Local services: Spring Boot API on `8080` (profile `local,fyers`), Vue Dashboard on `3003`.

## DB Migrations

Migrations in `backend/data/src/main/resources/db/migration/` were **consolidated**: the historical V1–V24 sequence was squashed into a single base schema. Version numbers intentionally skip V2–V24.

| Migration | Purpose |
|-----------|---------|
| V1 | Consolidated base schema — 22 tables (stocks, ohlcv_candles hypertable, signals, positions, trades, sentiment_results, watchlist, daily_loss_circuit_breaker_state, trade_labels, fyers_symbol_master, news_items, pdf_extractions, sentiment_accuracy, app_settings, kill_switch, nse_holidays, news_articles, job_runs, job_run_stages, paper_trading_portfolio[_snapshots], paper_trading_orders) |
| V25 | `version` integer column on 21 entity tables — `@Version` optimistic locking on all JPA entities |
| V26 | `positions.broker_position_id` — broker-enriched position tracking |

New migrations continue from V27. Existing databases that still carry the old V1–V24 history in `flyway_schema_history` must be recreated or repaired — the consolidated V1 checksum differs from the original.

## API Endpoints

### REST API (`/api/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health` | GET | System health (DB/Upstox connection states) |
| `/api/signals[/symbol]` | GET | Latest signals or for a specific stock |
| `/api/scan` | GET | Scan multiple stocks (`?days=30&marketCap=min`) |
| `/api/trade` | POST | Execute market order (paper mode) |
| `/api/positions[/symbol]` | GET | View positions |
| `/api/positions/{symbol}/close` | POST | Close a position |
| `/api/performance` | GET | P&L, win rate, trade stats |
| `/api/backtest/run` | POST | Backtest one symbol (`?symbol=X&exchange=NSE`) |
| `/api/backtest/run-all` | POST | Backtest active watchlist, saves report |
| `/api/backtest/reports[/{filename}]` | GET | List or fetch saved backtest reports |
| `/api/admin/*` | Various | Admin controller |
| `/api/analysis/*` | Various | Analysis orchestration |
| `/api/analysis/orchestration/*` | Various | Full analysis orchestrator |
| `/api/fyers/auth` | Various | Fyers OAuth endpoints |
| `/api/upstox/auth` | Various | Upstox OAuth endpoints |
| `/api/ingestion` | POST | Manual data ingestion trigger |
| `/api/sentiment` | Various | Sentiment API endpoints |
| `/api/settings` | Various | App settings management |
| `/api/job/runs` | Various | Job orchestrator runs |
| `/actuator/health` | GET | Spring Boot actuator |

### GPUHub API (`/gpuhub/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/gpuhub/deployment*` | Various | GPUHub elastic deployment management |

## Key Features

- **Job Orchestrator**: Unified 6-stage pipeline (replaces 8+ scattered `@Scheduled` jobs) with `JobRun`/`JobRunStage` tracking, `DailySignalOrchestrator`, and `JobOrchestratorService`
- **Signal Pipeline**: `SignalEngine` -> `SignalFilterService` -> `SignalPipeline` -> `SignalPersistenceService` with `SentimentGate` and `SignalFilterService` for multi-stage signal generation
- **Kill Switch**: Circuit breaker for stopping automated trading, persisted in DB (`kill_switch` table)
- **Intelligence System**: Full analysis orchestration (`AnalysisOrchestrationController`), news ingestion via `ArticleBrowser`, sentiment analysis with LLM, article storage (`NewsArticle`)
- **Analysis Pipeline**: `CompositeAnalysisService` -> `TechnicalAnalysisService` -> `FundamentalScorer` -> `SynthesisService` with `BacktestScorer`
- **Paper Trading**: Full order/position lifecycle (`Order`, `Position`) with risk controls (`RiskCalculator`), broker-agnostic, consolidated positions
- **Backtesting Engine**: Historical simulation with entry/exit rules per `docs/backtesting.md`, `BacktestController`, `BacktestScorer`
- **MonitoringView**: Real-time system health dashboard with `HealthStatus` component and `AppSettings`
- **OrchestratorView**: Job pipeline execution control, stage visualization
- **SentimentView**: LLM sentiment timeline with `SentimentBadge`, `SentimentTimeline`, accuracy metrics (`AccuracyMetricCard`)
- **Stage Analysis**: Stage detail components (`StageBacktestDetail`, `StageTechnicalDetail`, `StageFundamentalsDetail`, `StageCompositeDetail`, `StageSynthesisDetail`, `StageNewsDetail`)
- **Monthly Reports**: `MonthlyReportService` for PDF reports
- **Settings**: `SettingsController` + `SettingsView` with `appState` and `settings` Pinia stores

## Market Data Clients

| Client | Module | Auth | Endpoint | Status |
|--------|--------|------|----------|--------|
| `YahooFinanceClient` | data | None | Yahoo v8 chart (unofficial) | Active default |
| `UpstoxServiceClient` | data | OAuth2 token | Upstox v2 API | Active |
| `FyersServiceClient` | data | API key+secret | Fyers v3 API | Fyers profile |

## CI/CD

Three GitHub Actions workflows on self-hosted runners:

| Workflow | Trigger | Phases |
|----------|---------|--------|
| `ci.yml` | push/PR to main, manual | Static analysis -> Compile -> Tests (per module, parallel) + Frontend lint/typecheck/build/E2E |
| `deploy-main.yml` | push to main | Full pipeline to production stage |
| `deploy-stage.yml` | push to stage branch | Staged deployment with rollback |

## Test Conventions

- Unit tests: `*Test.java` — run via `./gradlew test`
- Integration tests: `*IntegrationTest.java` in `src/integrationTest/` — run via `./gradlew check`
- JaCoCo: 80% line coverage threshold, auto-finalized after `test`
- ArchUnit: module boundary enforcement (runs as unit test)
- Frontend E2E: Playwright tests in `dashboard/tests/e2e/` — `views/` (8: orchestrator-debug, positions-view, settings-view, signal-view-check, signals-selection, signals-view, stage-sanity, visual-check) + `behaviors/` (paper-trading-behavior)
- Frontend unit: Vitest tests in `dashboard/src/` (SettingsView, Toast)

## Spring Profiles

- `local` — dev config, verbose logging, paper trading only, connects to pi-node infra
- `dev` — development mode
- `fyers` — Fyers broker integration
- `test` — test configuration (H2 in-memory DB in some modules)
- `stage` — stage deployment configuration

## Environment

- `infra/env/.env` — loaded by `dev-stack.sh start`; contains DB, Upstox, LLM, broker credentials
- Spring Boot does NOT auto-load `.env`; the script sources it explicitly

## Documentation

All project documentation lives under `docs/`:

| Path | Contents |
|------|----------|
| `docs/plans/` | Implementation plans (handoff artifacts) — 12 active plans + `archive/` (9 archived) |
| `docs/specs/` | Design specs — 2 specs (Indian news sources, sentiment pipeline accordion) |
| `docs/analysis/` | Architecture audit, full analysis orchestration, LLM accuracy monitoring, sentiment analysis redesign |
| `docs/api-references/` | Fyers API v3, Yahoo Finance API, GPUHub elastic deployment |
| `docs/infra/` | CI/CD and deployment documentation |
| `docs/issues/` | 20 feature issues (001-020): watchlist, portfolio, chart, settings, signals, CI, auth, WebSocket, data quality, etc. |
| `docs/backtesting.md` | Backtest engine entry/exit rules |
| `docs/yahoo-finance-api.md` | Yahoo Finance unofficial API reference (v8/chart, v7/quote, v1/search) |
| `docs/status.md` | Project status tracker |

## Planning Artifacts

Multi-step implementation plans go to `docs/plans/<slug>.md`. Structure:
1. "What's already done"
2. "What needs to be done" (numbered steps with file paths and exact code)
3. "Style guide" (reference existing patterns)
4. "Verification" (commands to confirm)

Keep under 300 lines.

## Library Docs

Use the `context` skill to query version-specific docs for 17 installed libraries via the Context MCP server on the Pi. This covers Spring Boot, Spring AI, Spring Data, LangChain4j, Vue, and more. Use `get_docs` before web searching for library APIs.

## External JAR Dependencies

### Fyers SDK (`fyersjavasdk-1.9.0.jar`)

The Fyers v3 API SDK is a local JAR installed into the local Maven repository as a proper Maven dependency.

**Install to local Maven:**
```bash
cd backend
./gradlew installFyersSdk
```

This runs `mvn install:install-file` with coordinates `com.fyers:sdk:1.9.0` and places the artifact in `~/.m2/repository/com/fyers/sdk/1.9.0/`.

**Usage in build:**
```kotlin
// backend/data/build.gradle.kts
implementation("com.fyers:sdk:1.9.0")
```

**Source:** `backend/libs/fyersjavasdk-1.9.0.jar`

## Known Issues

- `LocalDateTime` needs custom Jackson serializer (not serializable by default)
- Remove explicit `hibernate.dialect` — auto-detected in Hibernate 6.6+
- Set `spring.jpa.open-in-view: false` to avoid lazy-loading warnings
- Broker module tests have pre-existing compilation errors (Position record constructor mismatch, missing RiskControlsService class)
- Native image build (GraalVM) in progress — plugin applied to api module, `infra/Dockerfile.native` exists; not yet deployed
