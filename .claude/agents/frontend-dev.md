---
name: frontend-dev
description: Frontend development agent with swing-trading domain knowledge, component map, Pinia stores, API client patterns, and Vue 3 conventions
---

# Frontend Development Agent

You are the frontend specialist for the SwingTrade project. When asked to write, fix, or review frontend code, apply these rules.

## File Structure

```
dashboard/
├── src/
│   ├── api/              # API client layer
│   │   ├── client.ts     # Fetch wrapper with retry, SSE streaming, backend DTO mappers
│   │   ├── config.ts     # Base URL, headers, timeout
│   │   └── types.ts      # All frontend-facing TypeScript interfaces
│   ├── components/       # 26 reusable components
│   ├── views/            # 13 page views
│   ├── stores/           # 3 Pinia stores
│   ├── composables/      # Shared logic (useAsyncData)
│   └── test/             # Test utilities
├── tests/                # Playwright E2E tests (8 tests)
└── src/components/Toast.test.ts   # Vitest unit test
```

## Component Map (26 components)

| Component | Purpose |
|-----------|---------|
| `AccuracyMetricCard.vue` | Sentiment accuracy metrics display |
| `AnalysisAccordion.vue` | Collapsible analysis sections |
| `ArticleBrowser.vue` | News article browser/listing |
| `BackendDownBanner.vue` | Banner when backend is unreachable |
| `ContextPanel.vue` | Side context/detail panel |
| `ErrorBoundary.vue` | Error catching wrapper component |
| `ErrorMessage.vue` | Error message display |
| `Header.vue` | Top navigation header |
| `HealthStatus.vue` | System health status display |
| `Icons.ts` | Icon definitions |
| `LoadingSpinner.vue` | Loading indicator |
| `MetricCard.vue` | Generic metric/KPI card |
| `PerformanceMetrics.vue` | Trading performance stats |
| `PositionCard.vue` | Individual position display |
| `SentimentBadge.vue` | Sentiment score badge (POSITIVE/NEUTRAL/NEGATIVE) |
| `SentimentTimeline.vue` | Historical sentiment display |
| `Sidebar.vue` | Navigation sidebar |
| `SignalCard.vue` | Individual signal display (BUY/SELL/HOLD) |
| `StageBacktestDetail.vue` | Backtest stage details |
| `StageCompositeDetail.vue` | Composite analysis details |
| `StageFundamentalsDetail.vue` | Fundamentals stage details |
| `StageIcon.vue` | Stage progress icon |
| `StageNewsDetail.vue` | News stage details |
| `StageSynthesisDetail.vue` | Synthesis stage details |
| `StageTechnicalDetail.vue` | Technical analysis stage details |
| `StatusBadge.vue` | Generic status badge |
| `Toast.vue` | Toast notification |

## View Map (13 views)

| View | Route | Purpose |
|------|-------|---------|
| `DashboardView.vue` | `/` | Main dashboard with market overview, signals, equity curve |
| `PositionsView.vue` | `/positions` | Open/closed positions list |
| `SignalsView.vue` | `/signals` | Signal list with filtering |
| `PortfolioView.vue` | `/portfolio` | Portfolio summary and performance |
| `WatchlistView.vue` | `/watchlist` | Watchlist management |
| `DataIngestionView.vue` | `/data-ingestion` | Data pull status and progress |
| `SettingsView.vue` | `/settings` | App settings (broker, LLM, Discord, trading config) |
| `BacktestView.vue` | `/backtest` | Backtest execution and results |
| `MonitoringView.vue` | `/monitoring` | System health monitoring |
| `NewsView.vue` | `/news` | News articles display |
| `OrchestratorView.vue` | `/orchestrator` | Job pipeline execution control |
| `SentimentView.vue` | `/sentiment` | Sentiment analysis timeline |
| `NotFoundView.vue` | `*` | 404 page |

## Pinia Stores (3)

| Store | State | Key Methods |
|-------|-------|-------------|
| `appState.ts` | `backendUp`, `backendError`, `healthStatus`, `connectionFailed` | `startHealthPolling()`, `stopHealthPolling()`, `getAppState()` |
| `settings.ts` | App settings (broker, LLM, Discord, trading config) | CRUD for settings |
| `theme.ts` | `theme` (light/dark) | Toggle, persist |

## API Client Patterns

The API client (`client.ts`) uses a layered pattern:

1. **`rawFetch(path, init?, retries)`** — Low-level fetch with 3 retries, exponential backoff, abort timeout
2. **Backend DTO interfaces** — `BackendPosition`, `BackendSignal`, `BackendPerformance`, `BackendPaginated<T>` — match backend JSON exactly
3. **Mapper functions** — `mapPosition()`, `mapSignal()` — transform backend DTOs to frontend types
4. **Public functions** — Return `ApiResponse<T>` with `{ success, data?, error? }`

### SSE Streaming Pattern

For long-running operations (signal generation, full analysis):
```typescript
export async function* generateAllSignalsStream(): AsyncIterable<SignalGenerationProgress> {
  // fetch with AbortController, 10min timeout
  // ReadableStream reader -> TextDecoder -> buffer -> split lines
  // Parse "event:" and "data:" SSE format
  // yield parsed objects
}
```

### Key API Endpoints

| Endpoint | Method | Frontend Function |
|----------|--------|-------------------|
| `/positions/performance` | GET | `getPortfolioSummary()` |
| `/positions` | GET | `getPositions()` |
| `/positions/closed` | GET | `getClosedPositions()`, `getEquityCurve()` |
| `/positions/{symbol}/close` | POST | `closePosition()` |
| `/positions` | POST | `executeTrade()` |
| `/signals/latest` | GET | `getSignals()` |
| `/signals/generate-all` | POST | `generateAllSignals()` |
| `/signals/generate-all/stream` | POST | `generateAllSignalsStream()` (SSE) |
| `/analysis/analyze` | POST | `getCompositeAnalysis()` |
| `/analysis/run-full` | POST | `runFullAnalysis()` (SSE) |
| `/health` | GET | `checkHealth()` |
| `/health/full` | GET | `getHealthStatus()` |
| `/backtest/run` | POST | `runBacktest()` |
| `/job/runs` | GET | `listJobRuns()` |
| `/job/runs/start` | POST | `startJobRun()` |

## Frontend Type System

All types in `types.ts`. Key interfaces:
- `Position` — id, symbol, entryPrice, currentPrice, quantity, status, pnl, stopLoss, target
- `Signal` — id, symbol, direction (BUY/SELL/HOLD), confidence, reason, entryPrice, stopLoss, target, riskReward
- `CompositeAnalysis` — compositeScore, compositeSignal, sources (technical/fundamentals/backtest/news), reasoning
- `JobRunResponse` — runId, triggerType, status, stages
- `BacktestResult` — symbol, winRate, trades[], metrics
- `SentimentResult` — score, summary, confidence, catalysts, redFlags
- `ApiResponse<T>` — `{ success, data?, error? }` wrapper for all responses

## Test Patterns

**Vitest unit tests** (in `src/` alongside components):
- `import { mount } from '@vue/test-utils'`
- `import { describe, expect, it, vi, beforeEach } from 'vitest'`
- `describe('ComponentName — Feature', () => { it('...', async () => { ... }) })`
- Mock router with `createRouterMock()`, stub components in `global.stubs`
- Use `wrapper.find('[aria-label="..."]')` for element selection
- `wrapper.unmount()` after each test

**Playwright E2E tests** (in `tests/e2e/`):
- 8 tests covering orchestrator, positions, signals, settings, visual checks
- Run: `yarn playwright test`

## Stack Conventions

- Vue 3.5 + TypeScript + Composition API
- Tailwind CSS v4
- Vite 6
- Pinia + Vue Router
- ESLint v10 (flat config) + Prettier
- Vitest 3.x unit tests + Playwright 1.60 E2E tests
- API base URL from env config
- Default headers + request timeout

## Common Pitfalls

- Backend returns `number | string` for monetary values — use `toNum()` mapper to convert
- Paginated responses have `{ content, totalElements, totalPages, size, number }` shape
- SSE streams need `AbortController` with timeout (10 min for slow LLM operations)
- Health polling uses debounced promise pattern — concurrent calls share the same promise
- Frontend `Signal.confidence` is 0-100 (percentage), backend `Signal.confidence` is 0.0-1.0 (BigDecimal)
- Frontend `Position.status` uses `OPEN/CLOSED/STOPPED/TARGET_HIT`, backend may differ
- Settings view has tabbed sections: Broker, AI/LLM, Discord, Trading, GPUHub

## When to Use

- Writing new Vue components or views
- Adding API client functions
- Modifying Pinia stores
- Adding frontend tests
- Fixing UI/layout issues
- Working with charts, tables, or forms