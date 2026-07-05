# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Dual Backend Architecture

This repo has **two** backend implementations — know which one you're working in:

1. **`src/` (root)** — Standalone Spring Boot 3.4.2 monolith. Simple app with basic stock/signal controllers. Use this for quick prototypes or simple changes. Built with `mvn` at repo root.
2. **`backend/`** — Multi-module Maven project (6 modules: core, data, strategy, llm, broker, api) with Spring Boot 3.3.1. Full-featured architecture with separate data ingestion, TA, LLM sentiment, paper trading, and REST API. Use this for real feature work.

When in doubt, `backend/` is the active development target.

## Project Structure

```
swing-trade/
├── src/                    # Root monolith (Spring Boot 3.4.2, standalone)
│   └── main/java/com/swingtrade/
│       ├── controller/     # StockController, TradeSignalController
│       ├── model/          # Stock, TradeSignal, TradeSignalRequest
│       ├── repository/     # JPA repositories
│       ├── service/        # Business logic
│       └── config/         # RedisConfig
│
├── backend/                # Multi-module Maven project (Spring Boot 3.3.1)
│   ├── pom.xml             # Parent POM (6 modules)
│   ├── core/               # Domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult)
│   ├── data/               # Data ingestion & storage (Upstox client, JPA entities, repositories, Flyway migrations)
│   ├── strategy/           # TA with TA4j, signal generation, backtesting engine
│   ├── llm/                # LLM client (vLLM/OpenAI-compatible), sentiment analysis, news ingestion
│   ├── broker/             # Paper trading engine, order/position management, risk controls
│   └── api/                # REST endpoints (Health, Signal, Trade, Position, Scan, Performance), scheduled jobs
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
└── dev-stack.sh            # Dev stack orchestration script
```

## Project Overview

Automated swing trading system for NSE/BSE Indian equities. Implements 1-4 week hold periods on Nifty 500 stocks using:
- **Technical Analysis**: EMA crossovers, RSI, MACD, ATR-based stops (TA4j)
- **LLM Sentiment**: vLLM-powered sentiment on financial news (LangChain4j)
- **Paper Trading**: Full order management with risk controls
- **Market Data**: Upstox API (OAuth2 token management)

## Backend Module Dependencies

```
api → strategy, llm, broker, data, core
broker → data, core
strategy → data, core
llm → core
data → core
core → (none)
```

## Key Technologies

### Backend
- Java 21 (MUST use Java 21, NOT Java 25 — Spring Boot 3.4.2 incompatible with Lombok 1.18.38)
- Spring Boot 3.3.1 (backend/) / 3.4.2 (root src/)
- Maven multi-module build
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

### Dev Stack (Recommended — runs infra on pi-node, app locally on Mac)
```bash
./dev-stack.sh start    # Start infra on pi-node + local Spring Boot (profiles: local,fyers)
./dev-stack.sh status   # Check infra + local API health
./dev-stack.sh stop     # Stop local app + infra on pi-node
./dev-stack.sh logs     # View infra logs
./dev-stack.sh infra <cmd>  # Pass any docker compose command to pi-node infra
```

Notes: `start` loads `backend/.env` automatically. Infra takes ~15s to become healthy after `up`.

### Backend (multi-module)
```bash
cd backend
mvn clean install              # Build all modules
mvn test                       # Run all tests
mvn test -Dtest=SomeTest       # Run specific test class

# Run API module locally (after starting infra)
cd api
mvn spring-boot:run -Dspring-boot.run.profiles=local,fyers
```

### Backend (root monolith)
```bash
mvn clean install              # Build
mvn test                       # Run tests
mvn spring-boot:run            # Run locally
```

### Frontend
```bash
cd dashboard
npm install
npm run dev                    # Start dev server (localhost:3003)
npm run build                  # Production build
npm run typecheck              # TypeScript type check
npm test                       # Vitest unit tests
npx playwright test            # E2E tests
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
Located at `backend/docker-compose.infra.yml`. Manage directly:
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
- `backend/.env` — loaded by `dev-stack.sh start`; contains DB, Redis, Upstox, LLM, and broker credentials
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
# Backend — all modules
cd backend && mvn test

# Backend — specific module
cd backend/data && mvn test

# Backend — with coverage
mvn clean test jacoco:report

# Frontend unit tests
cd dashboard && npm test

# Frontend E2E tests
cd dashboard && npx playwright test
```

Test resources include `application-test.properties`, `application-e2e.yml`, and test-specific Flyway schemas.

## Documentation

All project documentation goes in `docs/` as Markdown files.

### External API References

- [docs/yahoo-finance-api.md](docs/yahoo-finance-api.md) — Yahoo Finance unofficial API endpoints (v8/chart, v7/quote, v1/search), response formats, parameters, and reliability notes. Based on [yahoo-finance2](https://github.com/gadicc/yahoo-finance2) reverse-engineered docs.

### Backtesting

- [docs/backtesting.md](docs/backtesting.md) — `BacktestEngine` entry/exit rules, how to trigger a backtest via curl, and how to interpret `BacktestResult` fields.

## Planning Artifacts

When a multi-step implementation plan is decided (after research and clarification), write it to `claude/plans/<slug>.md`. This file serves as a handoff — paste its contents into a new Claude Code session to continue work after a context cutoff or session restart.

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

## Market Data Clients

| Client | Module | Auth | Endpoint | Status |
|--------|--------|------|----------|--------|
| `YahooFinanceClient` | data | None | Yahoo v8 chart (unofficial) | Active default |
| `UpstoxServiceClient` | data | OAuth2 token | Upstox v2 API | Active |
| `FyersServiceClient` | data | API key+secret | Fyers v3 API | Fyers profile |
