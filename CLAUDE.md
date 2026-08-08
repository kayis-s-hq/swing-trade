# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Dual Backend Architecture

This repo has **one** active backend:

1. **`backend/`** — Multi-module Gradle project (6 modules: core, data, strategy, llm, broker, api) with Spring Boot 3.3.1. Full-featured architecture with separate data ingestion, TA, LLM sentiment, paper trading, and REST API. Built with Gradle 9.6.1.

## Java Version

**MUST use Java 21 for Gradle builds.** The system JDK may be Java 25/26, which causes PMD 7.14.0 to crash. Use sdkman to switch:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
java -version  # should show openjdk 21.0.2
./gradlew help  # now works
```

Verify before running Gradle:
```bash
java -version  # must be Java 21, NOT 25/26
```

## Git Hooks

**STRICT: NEVER modify, override, or replace `.git/hooks/` files directly.** Git hooks are tracked in `.hooks/` and managed via `git config core.hooksPath`. All hook changes MUST go through `.hooks/` — never edit `.git/hooks/` directly.

## Project Structure

```
swing-trade/
├── backend/                # Multi-module Gradle project (Spring Boot 3.3.1)
│   ├── build.gradle.kts    # Root build + dependency management
│   ├── settings.gradle.kts # Project settings + repository config
│   ├── gradle.properties   # Gradle config (cache, JVM args)
│   ├── config/             # Shared checkstyle + PMD config
│   ├── core/               # Domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult)
│   ├── data/               # Data ingestion & storage (Upstox client, JPA entities, repositories, Flyway migrations)
│   ├── strategy/           # TA with TA4j, signal generation, backtesting engine
│   ├── llm/                # LLM client (vLLM/OpenAI-compatible), sentiment analysis, news ingestion
│   ├── broker/             # Paper trading engine, order/position management, risk controls
│   └── api/                # REST endpoints (Health, Signal, Trade, Position, Scan, Performance), scheduled jobs
│
├── infra/                  # Infrastructure (Docker, env, monitoring, nginx)
│   ├── env/                # Environment files (.env, .env.dev, .env.stage, .env.example)
│   ├── docker-compose.yml  # Default compose (dashboard + backend)
│   ├── docker-compose.infra-dev.yml   # Dev PostgreSQL + Redis on pi-node
│   ├── docker-compose.infra-stage.yml # Stage PostgreSQL + Redis on pi-node
│   ├── docker-compose.monitoring-stage.yml # Stage monitoring
│   ├── docker-compose.reports.yml     # Backtest reports service
│   ├── Dockerfile          # Backend Dockerfile
│   ├── deploy.sh           # Stage deployment script
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
│   │   ├── components/     # PositionCard, SignalCard, MetricCard, HealthStatus, PerformanceMetrics
│   │   ├── views/          # Dashboard, Positions, Signals, Portfolio, Watchlist, DataIngestion, Settings
│   │   ├── router/         # Vue Router
│   │   └── stores/         # Pinia stores (theme)
│   └── tests/              # Vitest unit tests + Playwright E2E tests
│
├── docs/                   # Project documentation
│   ├── plans/              # Implementation plans (handoff artifacts)
│   ├── specs/              # Design specs
│   ├── analysis/           # Architecture audits, analysis reports
│   ├── api-references/     # External API docs (Fyers, Yahoo Finance)
│   └── infra/              # CI/CD and deployment docs
│
├── .github/                # GitHub CI/CD workflows
│   └── workflows/
│
├── .github/issues/         # Feature issue descriptions
├── bin/                    # Shell scripts
├── dev-stack.sh            # Dev stack orchestration script
└── README.md
```

## Key Technologies

### Backend
- Java 21 (MUST use Java 21 via sdkman — `source "$HOME/.sdkman/bin/sdkman-init.sh"` before Gradle; NOT Java 25/26)
- Spring Boot 3.3.1
- Gradle 9.6.1 (Kotlin DSL, multi-module build)
- PostgreSQL + TimescaleDB (time-series)
- Redis (caching)
- TA4j 0.16 (technical analysis)
- LangChain4j 0.34.0 (LLM integration)
- Flyway (DB migrations)
- Lombok 1.18.34/1.18.38

### Frontend
- Vue 3.5 + TypeScript + Composition API
- Tailwind CSS v4
- Vite 6
- Pinia + Vue Router
- Vitest + Playwright for testing

## Development Commands

### Dev Stack (pi-node infra + local app)
The dev stack runs infrastructure on a Raspberry Pi (pi-node) via SSH, with Spring Boot and the dashboard running locally on Mac.

**Infrastructure containers** (on `piworm.local` via `docker context pi-node`):
| Service | Image | Local Port | Container | Notes |
|---------|-------|------------|-----------|-------|
| PostgreSQL 16 | `postgres:16` | `5435` | `swing_trade_postgres` | TimescaleDB, trust auth |
| Redis | `redis:alpine` | `6379` | `swing_trade_redis` | AOF enabled |

Network: `swingtrade-network` (bridge). Volumes: `postgres_data`, `redis_data`.

**Local services**:
| Service | Port | Profile |
|---------|------|---------|
| Spring Boot API | `8080` | `local,fyers` |
| Vue Dashboard | `3003` | — |

### Backend (multi-module)
```bash
cd backend
./gradlew build                # Build all modules + run tests
./gradlew test                 # Run all tests (unit + integration)
./gradlew :api:test            # Run specific module tests

# Run API module locally (after starting infra)
cd backend
./gradlew :api:bootRun --args='--spring.profiles.active=local,fyers'
```

## Build Configuration

Gradle 9.6.1 with Kotlin DSL. All build phases configured in `backend/build.gradle.kts`:
- JaCoCo: 80% line coverage threshold, XML reports
- Checkstyle: `backend/config/checkstyle/checkstyle.xml`
- PMD: `backend/config/pmd/pmd-ruleset.xml`
- Integration tests: `src/integrationTest/` source set
- Javadoc + source JAR generation

## Test Phase Rules

**All tests run via `./gradlew test`.** Gradle's JVM Test Suite plugin runs ALL unit tests in the `test` phase.

**Naming convention:**
- Unit tests: `*Test.java` (e.g., `BacktestEngineTest.java`)
- Integration tests: `*IntegrationTest.java` (e.g., `BacktestEngineIntegrationTest.java`) — placed in `src/integrationTest/`

**Verification:**
- `./gradlew test` — runs unit tests + JaCoCo report
- `./gradlew check` — runs tests + PMD + checkstyle + integration tests
- `./gradlew jacocoTestCoverageVerification` — checks 80% line coverage threshold

### Frontend
```bash
cd dashboard
yarn                           # Install dependencies
yarn dev                       # Start dev server (localhost:3003)
yarn build                     # Production build
yarn typecheck                 # TypeScript type check
yarn test                      # Vitest unit tests
yarn playwright test           # E2E tests
```

### Dev Stack Script (`dev-stack.sh`)

Manages the full dev environment — infrastructure on pi-node + local services.

```bash
./dev-stack.sh start           # Start infra on pi-node + Spring Boot + Vue locally
./dev-stack.sh stop            # Stop all local services + infra on pi-node
./dev-stack.sh status          # Check status of infra, backend, frontend
./dev-stack.sh logs            # View infrastructure logs

# Frontend management
./dev-stack.sh frontend start  # Start Vue dev server only
./dev-stack.sh frontend stop   # Stop Vue dev server only
./dev-stack.sh frontend-logs   # Tail Vue dev server logs

# Infrastructure
./dev-stack.sh infra up -d     # Start infra only
./dev-stack.sh infra down      # Stop infra only
```

## Infrastructure & Configuration

### Dev Stack (pi-node infra + local app)
The dev stack runs infrastructure on a Raspberry Pi (pi-node) via SSH, with Spring Boot and the dashboard running locally on Mac.

**Infrastructure containers** (on `piworm.local` via `docker context pi-node`):
| Service | Image | Local Port | Container | Notes |
|---------|-------|------------|-----------|-------|
| PostgreSQL 16 | `postgres:16` | `5435` | `swing_trade_postgres` | TimescaleDB, trust auth |
| Redis | `redis:alpine` | `6379` | `swing_trade_redis` | AOF enabled |

Network: `swingtrade-network` (bridge). Volumes: `postgres_data`, `redis_data`.

**Local services**:
| Service | Port | Profile |
|---------|------|---------|
| Spring Boot API | `8080` | `local,fyers` |
| Vue Dashboard | `3003` | — |

### Docker Compose Infra
Located at `infra/`. Manage directly:
```bash
./dev-stack.sh infra up -d      # Start
./dev-stack.sh infra down       # Stop
./dev-stack.sh infra restart    # Restart
./dev-stack.sh logs             # View logs
```

### Spring Profiles
- `local` — dev config, verbose logging, paper trading only, no Telegram, connects to pi-node infra
- `dev` — development mode
- `fyers` — Fyers broker integration
- `test` — test configuration (H2 in-memory DB in some modules)

### Environment
- `infra/env/.env` — loaded by `dev-stack.sh start`; contains DB, Redis, Upstox, LLM, and broker credentials
- Spring Boot does NOT auto-load `.env`; the script sources it explicitly

### DB Migrations
Located in `backend/data/src/main/resources/db/migration/`:
- `V1__swing_trade_schema.sql` — base schema (TimescaleDB hypertable, stocks, signals, positions, trades, sentiment)
- `V2__add_ohlcv_adj_close.sql` — adjusted close column
- `V3__add_watchlist.sql` — watchlist table

### Known Issues
- `LocalDateTime` needs custom Jackson serializer (not serializable by default)
- Remove explicit `hibernate.dialect` — auto-detected in Hibernate 6.6+
- Set `spring.jpa.open-in-view: false` to avoid lazy-loading warnings
- Set `spring.data.redis.host=piworm.local` for remote Redis
- Broker module tests have pre-existing compilation errors (Position record constructor mismatch, missing RiskControlsService class)
- Native image build (GraalVM) not implemented — would need `org.graalvm.buildtools.native` Gradle plugin

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/health` | System health (DB/Redis/Upstox connection states) |
| `GET /api/signals[/symbol]` | Latest signals or for a specific stock |
| `GET /api/scan` | Scan multiple stocks (`?days=30&marketCap=min`) |
| `POST /api/trade` | Execute market order (paper mode) |
| `GET /api/positions[/symbol]` | View positions |
| `POST /api/positions/{symbol}/close` | Close a position |
| `GET /api/performance` | P&L, win rate, trade stats |
| `GET /actuator/health` | Spring Boot actuator |
| `POST /api/backtest/run` | Backtest one symbol (`?symbol=X&exchange=NSE`) |
| `POST /api/backtest/run-all` | Backtest the active watchlist, saves a report |
| `GET /api/backtest/reports[/{filename}]` | List or fetch saved backtest reports |

## Testing

```bash
# Backend — all modules (unit tests)
cd backend && ./gradlew test

# Backend — specific module
cd backend && ./gradlew :data:test

# Backend — all verification (tests + PMD + checkstyle + integration tests)
cd backend && ./gradlew check

# Backend — coverage report
cd backend && ./gradlew jacocoTestReport

# Backend — coverage threshold check (80% line coverage)
cd backend && ./gradlew jacocoTestCoverageVerification

# Frontend unit tests
cd dashboard && yarn test

# Frontend E2E tests
cd dashboard && yarn playwright test
```

Test resources include `application-test.properties`, `application-e2e.yml`, and test-specific Flyway schemas.

## Documentation

All project documentation goes in `docs/` as Markdown files.

### External API References

- [docs/api-references/fyers-api-v3.md](docs/api-references/fyers-api-v3.md) — Fyers v3 API documentation
- [docs/yahoo-finance-api.md](docs/yahoo-finance-api.md) — Yahoo Finance unofficial API endpoints (v8/chart, v7/quote, v1/search), response formats, parameters, and reliability notes. Based on [yahoo-finance2](https://github.com/gadicc/yahoo-finance2) reverse-engineered docs.

### Analysis & Audits

- [docs/analysis/architecture-audit-2026-07-22.md](docs/analysis/architecture-audit-2026-07-22.md) — Architecture audit findings
- [docs/analysis/llm-accuracy-monitoring.md](docs/analysis/llm-accuracy-monitoring.md) — LLM accuracy monitoring analysis

### Backtesting

- [docs/backtesting.md](docs/backtesting.md) — `BacktestEngine` entry/exit rules, how to trigger a backtest via curl, and how to interpret `BacktestResult` fields.

## Planning Artifacts

When a multi-step implementation plan is decided (after research and clarification), write it to `docs/plans/<slug>.md`. This file serves as a handoff — paste its contents into a new Claude Code session to continue work after a context cutoff or session restart.

**Structure:**
- Section 1: "What's already done" — list completed backend renames, refactors, or changes
- Section 2: "What needs to be done" — numbered steps with file paths and exact code to add/modify
- Section 3: "Style guide" — patterns to follow (existing components, API client pattern, empty/loading states)
- Section 4: "Verification" — commands to run to confirm the work

**Rules:**
- Include exact file paths for every change
- Include the full code to add (not "add X to Y")
- Reference existing patterns from the codebase (don't invent new patterns)
- Keep the file under 300 lines so it fits in a new session's context

## Interaction Rules

### Decision Rule — Ask Before Deciding
**STRICT:** Before making ANY non-obvious, non-trivial, or irreversible decision, you MUST use the `AskUserQuestion` tool to present options and get user approval. This includes but is not limited to:
- Architecture choices (framework, library, pattern, module structure)
- UI/UX changes (layout, styling, component design, state management)
- API changes (endpoints, DTOs, request/response shapes)
- Data model changes (schema, entities, migrations)
- Bug fix approach (quick fix vs proper fix, refactor vs patch)
- Anything with more than one valid implementation path

**How to apply:** Call `AskUserQuestion` with clear options. The first option MUST be your recommended choice. Do NOT proceed until the user selects.

**Exception:** Trivial, reversible changes with a single obvious answer (typos, single-argument fixes, formatting) do not require asking.

### LLM Ambiguity Rule
**STRICT:** When LLM output is ambiguous, contradictory, incomplete, or potentially wrong, you MUST use the `AskUserQuestion` tool to clarify with the user before acting on it. Do NOT guess, assume, or silently correct LLM output.

### Documentation Organization Rule
**STRICT:** All project documentation, plans, and specs live exclusively under `docs/`. No other location is permitted:
- `docs/` — knowledge docs (API refs, architecture, backtesting, etc.)
- `docs/plans/` — implementation plans (handoff artifacts)
- `docs/specs/` — design specs
- `docs/analysis/` — architecture audits, analysis reports
- `docs/api-references/` — external API documentation
- `docs/infra/` — CI/CD and deployment documentation

**Forbidden locations:** `claude/plans/`, `.claude/plans/`, `docs/superpowers/`, `.claude/worktrees/`, or any other ad-hoc directory.

**How to apply:** When creating plans or specs, write to `docs/plans/` or `docs/specs/`. When moving files from forbidden locations to `docs/`, do it immediately. Never create new plan/doc directories outside `docs/`. Never leave stale copies in worktrees or session directories.

## Market Data Clients

| Client | Module | Auth | Endpoint | Status |
|--------|--------|------|----------|--------|
| `YahooFinanceClient` | data | None | Yahoo v8 chart (unofficial) | Active default |
| `UpstoxServiceClient` | data | OAuth2 token | Upstox v2 API | Active |
| `FyersServiceClient` | data | API key+secret | Fyers v3 API | Fyers profile |