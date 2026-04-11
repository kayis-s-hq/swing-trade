# SwingTrade Project Roadmap

---

## Phase 01: Core Domain Implementation

**Goal:** Define domain models and core business entities
**Requirements:** CORE-01, CORE-02
**Depends on:** None
**Plans:** 4 plans
- [x] 01-01-PLAN.md — Domain model definitions (Stock, OhlcvCandle, Signal, Position)
- [x] 01-02-PLAN.md — Position lifecycle and trading engine
- [x] 01-03-PLAN.md — Position stats and analytics
- [x] 01-04-PLAN.md — Trade and position response DTOs

---

## Phase 02: Strategy Engine

**Goal:** Implement technical analysis and signal generation
**Requirements:** STRAT-01, STRAT-02, STRAT-03
**Depends on:** Phase 1
**Plans:** 3 plans
- [x] 02-01-PLAN.md — TA4J integration and indicator library
- [x] 02-02-PLAN.md — Signal generation engine
- [x] 02-03-PLAN.md — Strategy configuration and execution

---

## Phase 03: Data Pipeline

**Goal:** Build market data ingestion and storage
**Requirements:** DATA-01, DATA-02, DATA-03
**Depends on:** Phase 1
**Plans:** 3 plans
- [x] 03-01-PLAN.md — Market data API client (Upstox)
- [x] 03-02-PLAN.md — PostgreSQL + TimescaleDB setup
- [x] 03-03-PLAN.md — Scheduled data ingestion

---

## Phase 04: LLM Sentiment Layer

**Goal:** Integrate LLM-based sentiment analysis
**Requirements:** SENT-01, SENT-02, SENT-03
**Depends on:** Phase 1, Phase 3
**Plans:** 4 plans
- [x] 04-01-PLAN.md — LangChain4j integration
- [x] 04-02-PLAN.md — Sentiment prompt templates
- [x] 04-03-PLAN.md — Combined signal generation
- [x] 04-04-PLAN.md — Sentiment history and storage

---

## Phase 05: API Layer

**Goal:** Implement REST API endpoints for system interaction and monitoring
**Requirements:** API-01, API-02, API-03
**Depends on:** Phase 1, Phase 2, Phase 3, Phase 4
**Status:** COMPILATION VERIFIED
**Plans:** 17 plans
- [x] 05-01-PLAN.md — API module setup and configuration
- [x] 05-02-PLAN.md — Health check endpoints
- [x] 05-03-PLAN.md — Position management endpoints
- [x] 05-04-PLAN.md — Trade execution endpoints
- [x] 05-05-PLAN.md — Signal endpoints
- [x] 05-06-PLAN.md — Scan endpoints
- [x] 05-07-PLAN.md — Performance endpoints
- [x] 05-08-PLAN.md — Error handling and validation
- [x] 05-09-PLAN.md — Request/Response DTOs
- [x] 05-10-PLAN.md — Query parameter filtering
- [x] 05-11-PLAN.md — Admin endpoints
- [x] 05-12-PLAN.md — Kill switch integration
- [x] 05-13-PLAN.md — API documentation
- [x] 05-14-PLAN.md — Integration tests with TestContainers
- [x] 05-15-PLAN.md — API security layer
- [x] 05-16-PLAN.md — Performance optimization
- [x] 05-17-PLAN.md — API layer compilation verification

---

## Phase 06: Live Trading

**Goal:** Enable paper trading execution and position management
**Requirements:** TRADE-01, TRADE-02, TRADE-03
**Depends on:** Phase 5
**Status:** VERIFIED
**Plans:** 8 plans
- [x] 06-01-PLAN.md — Paper trading engine foundation
- [x] 06-02-PLAN.md — Position lifecycle management
- [x] 06-03-PLAN.md — Risk controls and limits
- [x] 06-04-PLAN.md — Order execution logic
- [x] 06-05-PLAN.md — Trade history and reporting
- [x] 06-06-PLAN.md — Performance tracking
- [x] 06-07-PLAN.md — Position close workflow
- [x] 06-08-PLAN.md — Live trading verification

---

## Phase 07: Observability

**Goal:** Implement comprehensive monitoring and alerting
**Requirements:** OBS-01, OBS-02, OBS-03
**Depends on:** Phase 6
**Status:** VERIFIED
**Plans:** 5 plans
- [x] 07-01-PLAN.md — Health check integration
- [x] 07-02-PLAN.md — Metrics export (Prometheus)
- [x] 07-03-PLAN.md — Logging and audit trails
- [x] 07-04-PLAN.md — Alert configuration
- [x] 07-05-PLAN.md — Observability verification

---

## Phase 08: Vue Dashboard + Monitoring UI

**Goal:** Build a Vue.js web dashboard for real-time monitoring of the trading system
**Requirements:** DASH-01, DASH-02, DASH-03, DASH-04, DASH-05, DASH-06, DASH-07
**Depends on:** Phase 7
**Status:** EXECUTING
**Plans:** 3/2 plans complete
- [x] 08-01-PLAN.md — Project Setup and Foundation
- [x] 08-02-PLAN.md — Core Dashboard Views
- [x] 08-03-PLAN.md — Polish and Production Build

### Plan 08-01: Project Setup and Foundation
**Objective:** Initialize Vue.js dashboard with Vite, API client, and base layout
**Tasks:**
1. Initialize Vue 3 project with Vite build tooling
2. Configure Tailwind CSS with TailAdmin patterns
3. Create API client with TypeScript types
4. Build base application layout with sidebar navigation
5. Create router configuration
**Files:**
- swing-trade-dashboard/package.json
- swing-trade-dashboard/vite.config.ts
- swing-trade-dashboard/tailwind.config.js
- swing-trade-dashboard/src/main.ts
- swing-trade-dashboard/src/App.vue
- swing-trade-dashboard/src/router/index.ts
- swing-trade-dashboard/src/api/client.ts
- swing-trade-dashboard/src/api/types.ts
**Dependencies:** None (Wave 1)
**Requirements:** DASH-01, DASH-02

### Plan 08-02: Core Dashboard Views
**Objective:** Build main views for monitoring positions, signals, and portfolio
**Tasks:**
1. Create DashboardView with system overview
2. Create PositionsView with filtering and search
3. Create SignalsView with signal generation
4. Create PortfolioView with performance metrics
5. Build reusable components (MetricCard, PositionCard, SignalCard, PerformanceMetrics)
**Files:**
- swing-trade-dashboard/src/views/DashboardView.vue
- swing-trade-dashboard/src/views/PositionsView.vue
- swing-trade-dashboard/src/views/SignalsView.vue
- swing-trade-dashboard/src/views/PortfolioView.vue
- swing-trade-dashboard/src/components/MetricCard.vue
- swing-trade-dashboard/src/components/PositionCard.vue
- swing-trade-dashboard/src/components/SignalCard.vue
- swing-trade-dashboard/src/components/PerformanceMetrics.vue
**Dependencies:** Plan 08-01 (Wave 1)
**Requirements:** DASH-03, DASH-04, DASH-05, DASH-06

### Plan 08-03: Polish and Production Build
**Objective:** Add error handling, responsive design, and production build configuration
**Tasks:**
1. Create LoadingSpinner and ErrorMessage components
2. Configure production build with proper routing
3. Add environment configuration for API endpoints
4. Implement responsive design for mobile/tablet
5. Create NotFound page and documentation
**Files:**
- swing-trade-dashboard/vite.config.ts
- swing-trade-dashboard/src/main.ts
- swing-trade-dashboard/src/views/NotFoundView.vue
- swing-trade-dashboard/src/components/LoadingSpinner.vue
- swing-trade-dashboard/src/components/ErrorMessage.vue
- swing-trade-dashboard/README.md
- swing-trade-dashboard/.env.example
**Dependencies:** Plan 08-01, Plan 08-02 (Wave 2)
**Requirements:** DASH-06, DASH-07

---

## Phase 09: Telegram to Signal Migration

**Goal:** Replace Telegram notifications with Signal/Signl4 webhook integration
**Requirements:** NOTIF-01, NOTIF-02
**Depends on:** Phase 8
**Status:** IN PROGRESS
**Plans:** 3 plans
- [x] 09-01-PLAN.md — Signal/Signl4 webhook setup
- [x] 09-02-PLAN.md — Notification service migration
- [x] 09-03-PLAN.md — Telegram deprecation and cleanup

---

# Requirements Index

## Phase 01: Core Domain (CORE)
- **CORE-01:** Domain models properly defined and accessible
- **CORE-02:** Position and trade lifecycle models complete

## Phase 02: Strategy Engine (STRAT)
- **STRAT-01:** Technical analysis with TA4J working
- **STRAT-02:** Signal generation with confidence scoring
- **STRAT-03:** Strategy configuration via external config

## Phase 03: Data Pipeline (DATA)
- **DATA-01:** Market data ingestion from Upstox
- **DATA-02:** PostgreSQL + TimescaleDB storage
- **DATA-03:** Scheduled data ingestion working

## Phase 04: LLM Sentiment (SENT)
- **SENT-01:** LLM sentiment analysis integrated
- **SENT-02:** Combined signal with technical + sentiment
- **SENT-03:** Sentiment history stored and queryable

## Phase 05: API Layer (API)
- **API-01:** REST API endpoints functional
- **API-02:** Request/Response DTOs properly defined
- **API-03:** Error handling with HTTP status codes

## Phase 06: Live Trading (TRADE)
- **TRADE-01:** Paper trading engine executes trades
- **TRADE-02:** Position lifecycle managed correctly
- **TRADE-03:** Risk controls enforced

## Phase 07: Observability (OBS)
- **OBS-01:** Health check endpoints working
- **OBS-02:** Metrics exported to monitoring system
- **OBS-03:** Logging and audit trails complete

## Phase 08: Vue Dashboard (DASH)
- **DASH-01:** Vue.js application scaffolding complete
- **DASH-02:** REST API client integration working
- **DASH-03:** Dashboard view with system overview
- **DASH-04:** Positions view with filtering and search
- **DASH-05:** Signals view with signal generation
- **DASH-06:** Portfolio view with performance metrics
- **DASH-07:** Production build and responsive design

## Phase 09: Notifications (NOTIF)
- **NOTIF-01:** Signal webhook integration for alerts
- **NOTIF-02:** Telegram notifications deprecated
