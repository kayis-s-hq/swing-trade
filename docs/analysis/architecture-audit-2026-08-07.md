# Architecture & Code Audit — 2026-08-07

Comprehensive analysis of the SwingTrade codebase from three perspectives:
1. **Senior Staff Engineer** — code correctness, bugs, test coverage, security
2. **Backend Code Review** — Java/Spring Boot module-level defects
3. **Frontend Code Review** — Vue 3/TypeScript issues, type safety, UX

---

## Executive Summary

This audit found **8 critical, 15 high, 30 medium, 11 low** (64 total findings) across backend, frontend, and architecture. The most urgent concerns are:

- **Position.createWithRisk() parameter order is wrong** — all open positions at risk of corrupted state
- **Trade.close() PnL incorrect for SHORT + fees** — all trade metrics wrong for short positions
- **Credentials tracked in git** (.env file committed) — security exposure
- **Missing @Transactional on closePosition** — engine state and DB save not atomic
- **Backtest engine converts TA4j Num to double** — precision loss across thousands of bars
- **Two strategy implementations with different indicator logic** — live signals won't match backtest results
- **DailyLossCircuitBreaker is in-memory only** — resets on restart, daily loss limit effectively disabled
- **Frontend appState uses reactive() instead of Pinia** — not DevTools-compatible, harder to test

---

## CRITICAL Findings

### C1: Position.createWithRisk() parameter order wrong
**Severity**: CRITICAL | **Module**: core | **Impact**: All open positions

The factory method `Position.createWithRisk()` has swapped parameters — risk allocation and initial capital are passed in wrong order. Since this is used by `PaperTradingEngine` to create positions on order fill, every open position has corrupted risk state.

**Recommended fix**: Audit the parameter order in `Position.createWithRisk()`, swap to correct order, add a unit test verifying the parameter mapping.

---

### C2: Trade.close() PnL incorrect for SHORT + fees
**Severity**: CRITICAL | **Module**: core | **Impact**: All short trade P&L

`Trade.close()` calculates PnL as `(closePrice - entryPrice) * quantity` which is correct for LONG but inverted for SHORT. Additionally, fees are not included in the realized PnL calculation. All short trade metrics and overall performance stats are wrong.

**Recommended fix**: In `Trade.close()`, use `(entryPrice - closePrice) * quantity` for SHORT direction, and add `fees` to the PnL calculation for both directions.

---

### C3: Credentials committed to git
**Severity**: CRITICAL | **Module**: infra/env | **Impact**: Security exposure

The `.env` file containing real database passwords, Redis credentials, and API keys is tracked in git. This exposes the entire infrastructure to anyone with repo access.

**Recommended fix**: Remove `.env` from git (`git rm --cached .env`), add `.env` to `.gitignore`, distribute `.env.example` with placeholder values only.

---

### C4: Missing @Transactional on closePosition
**Severity**: CRITICAL | **Module**: broker/api | **Impact**: Data inconsistency

`PositionService.closePosition()` and `PaperTradingServiceImpl.closePosition()` lack `@Transactional`. The engine state update (removing from in-memory portfolio) and DB save (`PositionRepository.save()`) are not atomic. A failure between the two leaves the engine and DB out of sync.

**Recommended fix**: Add `@Transactional` to both `closePosition` methods.

---

### C5: Backtest engine converts TA4j Num to double
**Severity**: CRITICAL | **Module**: strategy | **Impact**: Backtest accuracy

`BacktestEngine` converts TA4j `Num` values to `double` at intermediate calculation points. TA4j's `DecimalNum` provides arbitrary precision; converting to `double` introduces rounding errors that compound across thousands of bars, making backtest results unreliable.

**Recommended fix**: Use `DecimalNum` throughout the backtest pipeline. Convert to `double` only at the final result output stage. See plan `docs/plans/2026-08-07-backtest-precision-decimalnum.md`.

---

### C6: Two strategy implementations with different logic
**Severity**: CRITICAL | **Module**: strategy | **Impact**: Signal parity

Live trading uses `SwingTradingStrategy` while backtesting uses `PriceActionSignalEngine`. These two implementations compute indicators differently (different lookback periods, different crossover logic), meaning backtest results will not match live signal output.

**Recommended fix**: Consolidate to a single strategy implementation. The backtest engine should consume the same strategy class used in live trading, with historical data injected.

---

### C7: Frontend appState uses reactive() instead of Pinia
**Severity**: CRITICAL | **Module**: frontend | **Impact**: Maintainability, testability

`dashboard/src/stores/appState.ts` uses Vue `reactive()` instead of Pinia. This means:
- Not visible in Vue DevTools
- Harder to test in isolation
- No action history/time-travel debugging
- Inconsistent with the rest of the app (settings store already uses Pinia)

**Recommended fix**: Convert `appState` to a Pinia store.

---

### C8: Frontend no <ErrorBoundary> — any component crash kills the whole app
**Severity**: CRITICAL | **Module**: frontend | **Impact**: App reliability

No `<ErrorBoundary>` component exists. A single unhandled error in any view (e.g., Network error in DashboardView, parsing error in SignalsView) crashes the entire Vue app, showing a blank screen with no recovery path.

**Recommended fix**: Create an `<ErrorBoundary>` component that catches render errors and shows a recovery UI with retry button. Apply to all top-level views.

---

## HIGH Findings

### H1: DailyLossCircuitBreaker state is in-memory only
**Severity**: HIGH | **Module**: broker | **Impact**: Risk control bypassed

`DailyLossCircuitBreaker` tracks daily loss in an in-memory field. On application restart, the counter resets to zero, effectively disabling the daily loss limit until losses accumulate again. This is a risk control gap.

**Recommended fix**: Persist `DailyLossCircuitBreaker` state to a DB table. Load on startup, update on each trade close.

---

### H2: PerformanceService uses hardcoded capital
**Severity**: HIGH | **Module**: api | **Impact**: All performance metrics wrong

`PerformanceService` uses a hardcoded capital value of 100,000 for P&L percentage calculations, while `BrokerProperties` defaults to 1,000,000 and `PaperTradingProperties` defaults to 500,000. All win rate, P&L %, and trade stat percentages are calculated against the wrong base.

**Recommended fix**: Inject `PaperTradingProperties` into `PerformanceService` and use `getInitialCapital()`.

---

### H3: PositionService duplicates PnL logic, ignores SHORT direction
**Severity**: HIGH | **Module**: api/broker | **Impact**: Incorrect P&L for short positions

`PositionController` and `PerformanceService` duplicate PnL calculation logic inline. The inline calculation only handles LONG direction, ignoring SHORT. It also bypasses `Position.calculateUnrealizedPnL()` which has the correct logic.

**Recommended fix**: Remove inline PnL calculations. Use `Position.calculateUnrealizedPnL()` and `Position.calculateRealizedPnL()` consistently.

---

### H4: BrokerProperties vs PaperTradingProperties capital mismatch
**Severity**: HIGH | **Module**: broker | **Impact**: Risk checks use wrong capital

`BrokerProperties` defaults to 1,000,000 while `PaperTradingProperties` defaults to 500,000. Risk checks in the broker module use `BrokerProperties` capital, but the paper trading engine uses `PaperTradingProperties`. This means risk limits are checked against a different capital value than what's actually managed.

**Recommended fix**: Use a single capital configuration source. Inject `PaperTradingProperties` into risk checks.

---

### H5: TradeRequest.isValid() has inverted STOP_LIMIT logic
**Severity**: HIGH | **Module**: api | **Impact**: Accepts invalid orders

`TradeRequest.isValid()` accepts STOP_LIMIT orders with a null `stopPrice` (should be required for STOP_LIMIT) and rejects valid STOP_MARKET orders. The conditional logic for order type validation is inverted.

**Recommended fix**: Fix the conditional: STOP_LIMIT requires both `stopPrice` and `limitPrice`; STOP_MARKET requires only `stopPrice`.

---

### H6: PaperTradingServiceImpl.placeOrder() null direction falls through to SELL
**Severity**: HIGH | **Module**: broker | **Impact**: Silent wrong trade execution

When `placeOrder()` receives an order with null direction, the switch statement falls through to the SELL case instead of throwing an error. This silently executes a SHORT sell when the caller intended a LONG buy.

**Recommended fix**: Add a default case that throws `IllegalArgumentException("Order direction must be BUY or SELL")`.

---

### H7: PositionEntity.toDomain() NPE when status is null in DB
**Severity**: HIGH | **Module**: data | **Impact**: Crash on legacy data

`PositionEntity.toDomain()` calls `PositionStatus.valueOf(entity.getStatus())` without null check. Legacy rows or migration gaps where `status` is null in the DB will cause NPE on startup or position fetch.

**Recommended fix**: Add null check: if `entity.getStatus()` is null, default to `PositionStatus.OPEN`.

---

### H8: Every view duplicates loading/error/errorMessage boilerplate
**Severity**: HIGH | **Module**: frontend | **Impact**: ~10 lines duplicated per view

Every Vue view (`DashboardView`, `SignalsView`, `PositionsView`, etc.) duplicates the same `loading`, `error`, `errorMessage` state variables and try/catch pattern. Should be a `useAsyncData<T>()` composable.

**Recommended fix**: Create `dashboard/src/composables/useAsyncData.ts` with loading/error/state management.

---

### H9: 18+ API functions use `as any` for response unwrapping
**Severity**: HIGH | **Module**: frontend | **Impact**: No type safety on API contract

`dashboard/src/api/client.ts` has 18+ functions using `(raw.data as any).data` to unwrap API responses. The backend `ApiResult<T>` response wrapper type is not enforced, so any API contract change silently breaks at runtime.

**Recommended fix**: Define proper response types matching `ApiResult<T>` and use generic return types on all API functions.

---

### H10: SSE parser has no timeout/abort — infinite hang risk
**Severity**: HIGH | **Module**: frontend | **Impact**: App hangs on slow backend

`runFullAnalysis` in `SignalsView` opens an SSE connection to `/api/analysis/run-full` with no `AbortController` or timeout. If the backend hangs, the frontend hangs indefinitely with no recovery.

**Recommended fix**: Wrap the SSE `fetch` in an `AbortController` with a 60-second timeout. Show error UI on abort.

---

### H11: Position type missing `reason` field and STOPPED/TARGET_HIT statuses
**Severity**: HIGH | **Module**: frontend | **Impact**: UI renders undefined, loses backend status

The frontend `Position` type lacks the `reason` field (entry reason from backend) and is missing `STOPPED` and `TARGET_HIT` `PositionStatus` values. The UI renders `undefined` for entry reason and cannot display stopped-out or target-hit positions correctly.

**Recommended fix**: Add `reason?: string` to the `Position` type. Add `STOPPED` and `TARGET_HIT` to `PositionStatus`.

---

### H12: rawFetch has no retry for transient failures
**Severity**: HIGH | **Module**: frontend | **Impact**: Single network glitch loses the request

`rawFetch` in `client.ts` makes one fetch attempt with no retry. Transient failures (502, 503, network timeout) result in immediate error display instead of retrying.

**Recommended fix**: Add retry logic with exponential backoff (3 retries) for 5xx and network errors.

---

### H13: ScanService runs synchronously for all symbols
**Severity**: HIGH | **Module**: api | **Impact**: Blocks HTTP request for 500+ stocks

`ScanService.scanAll()` runs synchronously within the HTTP request thread. Scanning 500+ stocks with TA4j indicators blocks the request for potentially minutes.

**Recommended fix**: Make scan async: accept a pollable scan ID, run scanning in a thread pool, return scan status via a separate endpoint.

---

### H14: Portfolio summary returns totalValue: 0
**Severity**: HIGH | **Module**: frontend | **Impact**: Dashboard shows $0 for portfolio value

The portfolio summary endpoint returns `totalValue: 0`. A comment in the code acknowledges the bug but it remains unfixed. The dashboard shows an incorrect $0 value.

**Recommended fix**: Fix the portfolio summary calculation to sum position values + cash balance.

---

### H15: Hardcoded allocation in SignalsView
**Severity**: HIGH | **Module**: frontend | **Impact**: Magic number, not configurable

`dashboard/src/views/SignalsView.vue:299` hardcodes Rs. 100,000 per-position allocation. This should come from broker settings or user configuration.

**Recommended fix**: Read allocation from Pinia settings store or API settings endpoint.

---

## MEDIUM Findings

### M1: DailyLossCircuitBreaker uses LocalDate.now() without timezone
**Severity**: MEDIUM | **Module**: broker | **Impact**: Daily reset at wrong time

Uses `LocalDate.now()` (system default) instead of `LocalDate.now(ZoneId.of("Asia/Kolkata"))`. If the server runs in UTC, the daily reset happens at midnight UTC, not IST — misaligned with trading hours.

**Recommended fix**: Use `Asia/Kolkata` timezone explicitly.

---

### M2: Portfolio summary totalValue calculation missing
**Severity**: MEDIUM | **Module**: api | **Impact**: Dashboard shows zero

The `PortfolioSummary.totalValue` field is not calculated — it returns 0 because the sum of position values + cash is not computed. The comment in the code acknowledges this but it's a visible UI bug.

**Recommended fix**: Compute `totalValue` as sum of `unrealizedValue` for open positions + `cashBalance`.

---

### M3: NaN validation gap for price/value inputs
**Severity**: MEDIUM | **Module**: frontend | **Impact**: NaN displayed in UI

No NaN validation for price/value inputs in the frontend. If the backend returns `NaN` or `null` for a price field, the UI renders "NaN" in the display.

**Recommended fix**: Add `isFinite()` checks on all numeric API responses. Default to 0 if NaN.

---

### M4: `as unknown as FyersStatus` cast — API contract not enforced
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Silent type mismatch

`dashboard/src/api/client.ts` uses `as unknown as FyersStatus` to cast raw API response to typed value. This bypasses TypeScript's type checking entirely — any API contract change is caught only at runtime.

**Recommended fix**: Use proper discriminated union types or validation functions instead of double casting.

---

### M5: Settings store silently swallows all errors on load
**Severity**: MEDIUM | **Module**: frontend | **Impact**: User sees no error when settings fail to load

`dashboard/src/stores/settings.ts` catches all errors in `loadSettings()` and silently ignores them. If the settings API is down, the user sees no indication and the app runs with stale defaults.

**Recommended fix**: Log the error and set an `error` flag in the store. Show a non-blocking notification.

---

### M6: Duplicate loading/error boilerplate across views
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Code duplication

Every view has the same `loading/error/errorMessage` pattern. This is the same issue as H8 but from a different angle — it's a code smell for missing abstraction.

**Recommended fix**: Extract to `useAsyncData` composable (see H8).

---

### M7: Hardcoded LLM URL in source code
**Severity**: MEDIUM | **Module**: backend/llm | **Impact**: Environment-specific value in source

The vLLM client URL is hardcoded in source rather than loaded from environment properties. This means local/dev/prod all use the same URL unless the source is changed.

**Recommended fix**: Move LLM URL to `application-{profile}.properties` and inject via `@Value` or `@ConfigurationProperties`.

---

### M8: ScanService sequential execution
**Severity**: MEDIUM | **Module**: api | **Impact**: Slow scan for large watchlists

`ScanService` processes symbols sequentially. For a watchlist of 100+ stocks, each requiring TA4j indicator computation, the scan takes linearly long.

**Recommended fix**: Use `CompletableFuture.supplyAsync()` with a bounded thread pool for parallel symbol scanning.

---

### M9: Position record is 23 fields — god object
**Severity**: MEDIUM | **Module**: core | **Impact**: Hard to maintain, hard to test

`Position` has 23 fields covering entry data, current data, PnL, risk, status, timestamps, and metadata. It violates Single Responsibility Principle.

**Recommended fix**: Split into `PositionSummary` (for listing) and `PositionDetails` (for detail view). Use composition.

---

### M10: VARCHAR(10) for symbols too restrictive
**Severity**: MEDIUM | **Module**: data | **Impact**: Schema inconsistency

Stock symbols use `VARCHAR(10)` in some tables and `VARCHAR(16)` in others (positions). No UNIQUE constraint on symbols. Inconsistent with NSE derivative symbols like `NIFTY01JAN25C21000` which exceed 10 chars.

**Recommended fix**: Standardize on `VARCHAR(20)` across all symbol columns. Add UNIQUE constraint on stock symbols.

---

### M11: Frontend appState uses reactive() instead of Pinia
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Not DevTools-compatible

Already covered in C7. Listed here as an architecture-level concern about state management consistency.

---

### M12: API client is 921 lines, no separation of concerns
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Hard to maintain

`dashboard/src/api/client.ts` is 921 lines handling all API calls — health, signals, trades, positions, scan, performance, backtest, settings, Fyers auth, analysis. Violates SRP.

**Recommended fix**: Split into domain-specific modules: `signalApi.ts`, `positionApi.ts`, `tradeApi.ts`, etc.

---

### M13: No API rate limiting
**Severity**: MEDIUM | **Module**: api | **Impact**: Signal generation and full analysis can be triggered repeatedly

No rate limiting on `POST /api/analysis/run-full` or `GET /api/scan`. A user (or bug) can trigger repeated full analyses, consuming CPU and LLM tokens.

**Recommended fix**: Add `@RateLimiter` annotation or Spring Cloud Gateway rate limiting.

---

### M14: Dashboard uses $ currency symbol while other views use Rs.
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Inconsistent currency display

`DashboardView.vue` uses `$` prefix for values while other views use `Rs.`. Inconsistent with an Indian swing trading system.

**Recommended fix**: Standardize on `Rs.` or make currency configurable via settings.

---

### M15: PositionCard reason field never mapped
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Entry reason always undefined in UI

`dashboard/src/components/PositionCard.vue:16` references `BackendPosition.entryReason` but the API mapper (`mapPosition`) does not include this field. The UI shows empty/undefined for entry reason.

**Recommended fix**: Add `reason` to the API response type and include in `mapPosition`.

---

### M16: No retry logic for transient API failures
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Same as H12 — listed as architectural concern

---

### M17: No request deduplication for rapid clicks
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Duplicate API calls on fast clicks

No deduplication of in-flight requests. If a user double-clicks "Analyze", two parallel SSE connections are opened, both running full analysis.

**Recommended fix**: Track in-flight request promises by endpoint. Return existing promise if one is already in progress.

---

### M18: Missing ARIA attributes on interactive elements
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Accessibility gap

Several interactive elements (buttons, tabs, accordions) lack `aria-label`, `aria-expanded`, or `role` attributes.

**Recommended fix**: Add ARIA attributes to all interactive components.

---

### M19: Unit tests testing non-existent behavior
**Severity**: MEDIUM | **Module**: frontend | **Impact**: False confidence in test coverage

Some Vitest unit tests assert on behavior that doesn't exist in the component (e.g., testing a method that was removed but the test wasn't updated).

**Recommended fix**: Review all unit test assertions against actual component methods.

---

### M20: Flaky E2E waits
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Intermittent test failures

Playwright E2E tests use fixed `waitForTimeout()` instead of waiting for specific elements/conditions. Tests fail intermittently based on load timing.

**Recommended fix**: Replace all `waitForTimeout` with `waitForSelector` or `waitForResponse` assertions.

---

### M21: SwingTradingStrategy null close prices NPE
**Severity**: MEDIUM | **Module**: strategy | **Impact**: Crash on incomplete OHLCV data

`SwingTradingStrategy` does not check for null close prices before computing indicators. Incomplete candle data (partial day) causes NPE.

**Recommended fix**: Filter out candles with null close prices before indicator computation.

---

### M22: Order class lacks equals/hashCode with ConcurrentHashMap risk
**Severity**: MEDIUM | **Module**: broker | **Impact**: Map lookup failures

The `Order` record is used as a key in `ConcurrentHashMap` but lacks proper `equals`/`hashCode`. Default record equality uses reference equality, causing lookup failures.

**Recommended fix**: Verify record `equals`/`hashCode` behavior matches usage. If Order is used as a map key, ensure field-based equality.

---

### M23: Console.log in production code
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Noise in browser console, potential info leak

Several Vue views contain `console.log` statements that should be removed or replaced with a proper logging framework.

**Recommended fix**: Remove all `console.log` from production code. Use a structured logger if needed.

---

### M24: No modal focus trapping
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Accessibility gap — keyboard users lose focus

Signal detail modals and confirmation dialogs do not trap focus. Tabbing out of a modal moves focus to background elements.

**Recommended fix**: Implement focus trap in modal component. Return focus to trigger element on close.

---

### M25: Raw ThreadPoolExecutor in LLM module
**Severity**: MEDIUM | **Module**: llm | **Impact**: Unbounded thread creation

`LlmService` uses a raw `ThreadPoolExecutor` with unbounded queue. Under heavy load, this can create many threads and consume excessive memory.

**Recommended fix**: Use `ThreadPoolExecutor` with bounded queue and `CallerRunsPolicy` rejection handler. Or use Spring's `@Async` with a configured `TaskExecutor`.

---

### M26: BrokerProperties setter injection
**Severity**: MEDIUM | **Module**: broker | **Impact**: Harder to test, harder to reason about

`BrokerProperties` uses setter injection for some fields instead of constructor injection. Makes the class partially mutable and harder to test.

**Recommended fix**: Use constructor injection for all configuration classes.

---

### M27: ScanService no caching of indicator results
**Severity**: MEDIUM | **Module**: strategy | **Impact**: Repeated computation for same symbol

Scanning re-computes indicators for the same symbol on every scan, even if the result hasn't changed (same OHLCV data, same date).

**Recommended fix**: Cache indicator results keyed by (symbol, date, indicator_type) with TTL.

---

### M28: PerformanceService no pagination
**Severity**: MEDIUM | **Module**: api | **Impact**: Slow response for large trade histories

`GET /api/performance` returns all trades at once. For accounts with 10,000+ trades, this is a large response.

**Recommended fix**: Add pagination to performance endpoint. Return summary stats separately from trade list.

---

### M29: getEquityCurve() ignores _range parameter
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Chart shows wrong time range

`dashboard/src/api/client.ts` defines `getEquityCurve(range?: string)` but the implementation ignores the `_range` parameter and always fetches all data.

**Recommended fix**: Pass `_range` as a query parameter to the API.

---

### M30: generateAllSignals result cast with as unknown as
**Severity**: MEDIUM | **Module**: frontend | **Impact**: Type safety bypass

`generateAllSignals` casts the API response with `as unknown as SignalResponse[]` bypassing type checking.

**Recommended fix**: Define proper response type and use generic function.

---

## LOW Findings

### L1: Only 4 unit tests for 23 components
**Severity**: LOW | **Module**: frontend | **Impact**: Low frontend test coverage

Only 4 Vue components have unit tests out of 23. The rest rely solely on E2E tests, which are slower and less precise for component-level bugs.

**Recommended fix**: Add Vitest unit tests for key components: `SignalCard`, `PositionCard`, `MetricCard`, `PerformanceMetrics`.

---

### L2: No API client tests
**Severity**: LOW | **Module**: frontend | **Impact**: API contract changes not caught by tests

`dashboard/src/api/client.ts` (921 lines) has zero tests. API contract changes are only caught by E2E test failures.

**Recommended fix**: Add unit tests for `rawFetch` error handling and response unwrapping with mocked fetch.

---

### L3: Hardcoded LLM URL in source
**Severity**: LOW | **Module**: backend/llm | **Impact**: Environment-specific value in source

Already covered in H7/M7. Listed here as a lower-priority configuration concern.

---

### L4: Hardcoded user initials in Sidebar
**Severity**: LOW | **Module**: frontend | **Impact**: Cosmetic inconsistency

User initials are hardcoded in `Sidebar.vue` instead of being loaded from a user settings endpoint.

**Recommended fix**: Fetch user profile from API and display initials.

---

### L5: 7 critical classes have no tests
**Severity**: LOW | **Module**: backend | **Impact**: Low confidence in critical code

`SignalPipeline`, `DailySignalOrchestrator`, `PaperTradingEngine`, `RiskCalculator`, `BacktestEngine`, `CapitalTracker`, `SignalPersistenceService` have zero unit tests.

**Recommended fix**: Add Mockito unit tests for the most critical classes first (PaperTradingEngine, BacktestEngine).

---

### L6: No <ErrorBoundary> in frontend
**Severity**: LOW | **Module**: frontend | **Impact**: Same as C8 — listed as architectural concern

---

### L7: No API rate limiting
**Severity**: LOW | **Module**: api | **Impact**: Same as H13 — listed as architectural concern

---

### L8: No request deduplication
**Severity**: LOW | **Module**: frontend | **Impact**: Same as M17

---

### L9: Missing ARIA attributes
**Severity**: LOW | **Module**: frontend | **Impact**: Same as M18

---

### L10: No modal focus trapping
**Severity**: LOW | **Module**: frontend | **Impact**: Same as M24

---

### L11: Flaky E2E waits with fixed timeouts
**Severity**: LOW | **Module**: frontend | **Impact**: Same as M20

---

## Code Quality Scores

| Module | Score | Key Issue |
|--------|-------|-----------|
| **core** | 7.0 | Position factory param shift, Trade PnL bugs |
| **data** | 7.0 | Good repository/store pattern, VARCHAR(10) restriction |
| **strategy** | 6.0 | Lowest test density, two strategy implementations, Num precision |
| **llm** | 7.5 | Raw ThreadPoolExecutor, good news sources |
| **broker** | 7.0 | Setter injection, in-memory circuit breaker, null direction bug |
| **api** | 7.5 | Sequential pipeline, duplicate controllers, no rate limiting |
| **frontend** | 6.0 | 921-line API client, no Pinia for appState, no ErrorBoundary |

---

## Recommended Fix Order

### P0 — Immediate (data integrity / security)
| Priority | Finding | Fix |
|----------|---------|-----|
| P0-1 | C1 | Fix `Position.createWithRisk()` parameter order |
| P0-2 | C2 | Fix `Trade.close()` PnL for SHORT + fees |
| P0-3 | C3 | Remove `.env` from git, add to `.gitignore` |
| P0-4 | C4 | Add `@Transactional` to `closePosition` methods |

### P1 — Critical bugs (this sprint)
| Priority | Finding | Fix |
|----------|---------|-----|
| P1-1 | C5 | Migrate backtest engine to `DecimalNum` (plan exists) |
| P1-2 | C6 | Consolidate strategy implementations |
| P1-3 | C7 | Migrate appState to Pinia |
| P1-4 | C8 | Add `<ErrorBoundary>` component |
| P1-5 | H1 | Persist `DailyLossCircuitBreaker` to DB |
| P1-6 | H2 | Fix `PerformanceService` hardcoded capital |
| P1-7 | H3 | Remove inline PnL, use `Position.calculateUnrealizedPnL()` |
| P1-8 | H4 | Unify capital configuration source |

### P2 — High severity (next sprint)
| Priority | Finding | Fix |
|----------|---------|-----|
| P2-1 | H5 | Fix `TradeRequest.isValid()` STOP_LIMIT logic |
| P2-2 | H6 | Add default case to `placeOrder()` direction switch |
| P2-3 | H7 | Add null check in `PositionEntity.toDomain()` |
| P2-4 | H8-H12 | Frontend: composable, types, SSE timeout, retry, type fix |
| P2-5 | H13 | Make ScanService async with pollable ID |
| P2-6 | H14 | Fix portfolio summary totalValue calculation |
| P2-7 | H15 | Replace hardcoded allocation with settings value |

### P3 — Medium severity (backlog)
| Priority | Finding | Fix |
|----------|---------|-----|
| P3-1 | M1 | Fix `DailyLossCircuitBreaker` timezone to Asia/Kolkata |
| P3-2 | M9 | Split Position into Summary/Details |
| P3-3 | M10 | Standardize VARCHAR(20) for symbols |
| P3-4 | M12 | Split API client into domain modules |
| P3-5 | M13 | Add rate limiting to analysis/scan endpoints |
| P3-6 | M21-M22 | Null checks in strategy, Order equals/hashCode |
| P3-7 | M25-M27 | ThreadPoolExecutor bounds, setter injection, scan caching |

### P4 — Low severity (ongoing)
| Priority | Finding | Fix |
|----------|---------|-----|
| P4-1 | L1 | Add Vitest tests for key components |
| P4-2 | L2 | Add API client unit tests |
| P4-3 | L5 | Add Mockito tests for 7 untested critical classes |
| P4-4 | L4 | Fetch user initials from API |

---

## Analysis Methodology

This audit was produced by three parallel analysis agents:
1. **Architecture Agent** — System-level design review (module boundaries, data flow, scalability)
2. **Backend Code Review Agent** — Java/Spring Boot module-level defect scan
3. **Frontend Code Review Agent** — Vue 3/TypeScript type safety and UX review

Each agent scanned the codebase independently. Findings were deduplicated and merged by severity. File paths and line numbers reference the codebase state at time of analysis.

---

## Audit Fix Progress (2026-08-08)

### Completed (8 phases)

| Phase | Finding(s) | Status | Commit |
|-------|-----------|--------|--------|
| 1 | H5: TradeRequest.isValid() | ✅ Done | `d464e12c` |
| 2 | H6: Null direction guards | ✅ Done | `d464e12c` |
| 3 | H7: PositionEntity NPE | ✅ Done | `d464e12c` |
| 4 | C4: @Transactional on closePosition | ✅ Done | `d464e12c` |
| 5 | H4: Capital config mismatch | ✅ Done | `d464e12c` |
| 6 | C2: Trade.close() SHORT PnL + fees | ✅ Done | `d464e12c` |
| 7 | H1: DailyLossCircuitBreaker persistence | ✅ Done | `7e4a2b68` |
| 8 | C6: Strategy consolidation | ✅ Done | `2f72eacf` |
| 9 | H9: as any API unwraps | ✅ Done | `660e19c6` |
| 10 | H10: SSE timeout | ✅ Done | `e8950aa9` |
| 11 | H11: Position type sync | ✅ Done | `e8950aa9` |
| 12 | H12: rawFetch retry | ✅ Done | `e8950aa9` |
| 13 | C8: ErrorBoundary | ✅ Done | `e712857d` |
| 14 | H8: useAsyncData composable | ✅ Done | `e712857d` |
| 15 | M14: Currency standardization | ✅ Done | `e712857d` |
| 16 | H15: Allocation from settings | ✅ Done | `e712857d` |
| 17 | View integration (useAsyncData + ErrorBoundary) | ✅ Done | `2d9b06c5` |

### Dismissed (not bugs)

| Finding | Reason |
|---------|--------|
| C1: Position.createWithRisk() param order | Compact constructor fallback makes it safe |
| C3: Credentials in git | infra/env/.env is NOT tracked (.gitignore matches it) |
| C5: Backtest precision | Uses safe numToBigDecimal() helper; one doubleValue() is on BigDecimal not Num |

### Remaining — Backend

| Finding | Priority | What's needed |
|---------|----------|---------------|
| H2: PerformanceService hardcoded capital | P1 | Inject PaperTradingProperties, use getInitialCapital() |
| H3: Inline PnL ignores SHORT | P1 | Remove inline calc, use Position.calculateUnrealizedPnL() |
| H14: Portfolio summary totalValue: 0 | P2 | Sum position values + cash balance |
| M1: DailyLossCircuitBreaker timezone | P3 | Use Asia/Kolkata explicitly |
| M9: Position god object (23 fields) | P3 | Split into PositionSummary/PositionDetails |
| M10: VARCHAR(10) symbols | P3 | Standardize VARCHAR(20) |
| M13: No rate limiting | P3 | Add @RateLimiter on analysis/scan endpoints |
| M28: No pagination on performance | P3 | Add pagination to /api/performance |

### Remaining — Frontend

| Finding | Priority | What's needed |
|---------|----------|---------------|
| C7: appState not Pinia | P1 | Convert to Pinia store |
| M3: NaN validation gap | P3 | isFinite() checks on numeric responses |
| M12: API client 921 lines | P3 | Split into separate modules |
| M20: Flaky E2E waits | P3 | Replace fixed waits with stable selectors |
| L1: Component unit tests | P3 | Add Vitest tests for views |
| M12: 921-line API client | P3 | Split into domain modules |
| M20: Flaky E2E waits | P3 | Replace waitForTimeout with waitForSelector |
| L1: Only 4 component unit tests | P4 | Add Vitest for SignalCard, PositionCard, etc. |

### Next Recommended Sprint

1. **H2: PerformanceService hardcoded capital** — quick injection fix, same pattern as Phase 5
2. **C7: appState → Pinia** — frontend migration, low risk
3. **C8: ErrorBoundary** — frontend resilience
4. **H14: Portfolio summary totalValue** — visible dashboard bug
