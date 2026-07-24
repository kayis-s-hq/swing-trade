# Architecture & Code Audit — 2026-07-22

Comprehensive analysis of the SwingTrade codebase from two perspectives: Senior Staff Engineer (code quality, correctness, technical debt) and Senior Software Architect (system design, architecture, strategic concerns).

---

## Executive Summary

This audit found **3 critical, 11 high, and 11 medium-severity issues** across code quality, architecture, security, data integrity, and maintainability. The most urgent concerns are:

- **Zero security** on all REST endpoints
- **Hardcoded credentials** in source-tracked files
- **3 active bugs in paper trading engine** that corrupt portfolio state
- **3 coexisting Position/Signal models** creating inevitable data drift
- **SignalEngine as a god class** (384 lines) that is untestable and unextendable
- **Module dependency tangle** with no clean layering

The git history shows a **fix cascade pattern** — each feature merge is immediately followed by a fix commit, indicating features are being merged in a broken state.

---

## Critical Findings

### C1: Zero Spring Security — All REST Endpoints Publicly Accessible

**Scope**: All 20+ controllers across the API module.

No `spring-boot-starter-security` dependency exists anywhere in the project. All endpoints are completely unprotected:

- `POST /api/admin/kill-switch` — anyone can disable the trading system
- `POST /api/trade` — anyone can execute trades
- `PUT /api/settings/broker` — anyone can change broker configuration
- `POST /api/backtest/run` — anyone can trigger backtests

**Impact**: Any network-accessible caller can execute trades, disable the system, or change configuration.

**Fix**: Add Spring Security with role-based access control. At minimum, require authentication for all write endpoints.

---

### C2: Hardcoded Credentials in Source-Tracked Files

**Files**:
- `backend/docker-compose.yml` lines 100-101, 135-136 — Fyers API key/secret
- `backend/docker-compose.infra-dev.yml` lines 18-19 — Fyers credentials
- `backend/docker-compose.infra-stage.yml` lines 19-20, 54-55 — Fyers credentials
- `backend/docker-compose.monitoring-stage.yml` line 14 — Grafana default password `swingtrade`
- `.env` and `.env.dev` — real Upstox API credentials including JWT access token
- `backend/broker/src/main/resources/application.properties` lines 189-194 — Zerodha placeholder credentials

PostgreSQL uses `trust` authentication in all docker-compose files.

**Impact**: Full credential exposure to anyone with repo access.

**Fix**: Move all credentials to environment variables or a secrets manager. Never commit `.env` files.

---

### C3: LiveTradingService Marks Order CANCELLED When Broker May Have Already Accepted It

**File**: `backend/broker/src/main/java/com/swingtrade/broker/factory/LiveTradingService.java` lines 87-91

```java
} catch (Exception e) {
    order.setStatus(OrderStatus.CANCELLED);
    return order;
}
```

**Problem**: A network timeout or broker API error causes the local system to mark the order CANCELLED while the broker may have already assigned a broker order ID. This creates orphaned live orders.

**Impact**: Financial risk — orders exist in the broker system but are not tracked locally.

**Fix**: On failure, mark the order as `UNKNOWN` and trigger a reconciliation process with the broker API.

---

### C4: PaperTradingEngine.partialExitPosition() — Mathematically Wrong P&L Calculation

**File**: `backend/broker/src/main/java/com/swingtrade/broker/engine/PaperTradingEngine.java` lines 396-402

```java
BigDecimal exitValue = position.getQuantity()
    .add(position.getProfitLoss().divide(exitPrice, 0, RoundingMode.FLOOR));
portfolio.setCurrentCapital(portfolio.getCurrentCapital().add(exitValue));
```

**Problem**: Adds `quantity` (a share count) to `profitLoss / exitPrice` (a currency value divided by price). This produces a nonsensical result. The correct formula is `(originalQuantity - remainingQuantity) * exitPrice`.

**Impact**: Every partial exit corrupts the portfolio capital. Errors compound over time.

**Fix**: Use `(originalQuantity - remainingQuantity) * exitPrice` for the exit value calculation.

---

### C5: PaperTradingStateService.loadClosedPositions() — Double-Counts Realized P&L on Startup

**File**: `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java` lines 98-107

After loading `currentCapital` from the DB (which already includes realized P&L), the code adds `totalRealized` again:

```java
engine.getPortfolio().setCurrentCapital(
    engine.getPortfolio().getCurrentCapital().add(totalRealized));
```

**Impact**: Every application restart inflates the portfolio balance by the sum of all historical realized P&L.

**Fix**: Remove the `add(totalRealized)` — `currentCapital` from the DB already reflects it.

---

### C6: PaperTradingStateService.loadOpenPositions() — Positions Never Added to PositionManager

**File**: `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java` lines 83-96

Positions are added to `portfolio.positions` but never to `positionManager`. This means `positionManager.hasReachedPositionLimit()` always returns false after restart.

**Impact**: Position limits are silently bypassed after every restart. Unlimited concurrent positions can be opened.

**Fix**: After loading positions from DB, also register each one in `positionManager`.

---

### C7: PaperTradingStateService — All Save Methods Silently Swallow Exceptions

**File**: `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java` lines 125-199

Every save method uses `try { ... } catch (Exception e) { logger.warn(...) }`. Database failures (connection pool exhaustion, constraint violations, deadlocks) are logged at WARN level and the caller proceeds as if the save succeeded.

**Impact**: In-memory state silently diverges from database state. State loss on failure with no alert.

**Fix**: Re-throw as a runtime exception or use a dedicated `StatePersistenceException`. At minimum, trigger an alert on save failure.

---

## High Findings

### H1: Three Coexisting Position/Signal Models (Data Model Duplication)

**Models in play**:

| Model | Location | Type |
|-------|----------|------|
| `Position` (domain record) | `backend/core/src/main/java/com/swingtrade/domain/` | Immutable record |
| `PositionEntity` (JPA) | `backend/data/src/main/java/com/swingtrade/data/entity/` | Mutable class with `fromDomain`/`toDomain` |
| `Position` (broker model) | `backend/core/src/main/java/com/swingtrade/broker/model/` | Mutable, different field set |
| `PaperTradingPositionEntity` | `backend/broker/src/main/java/com/swingtrade/broker/entity/` | JPA, mapped to `paper_trading_positions` table |

The broker `Position` model has fields like `positionId`, `brokerPositionId`, `averagePrice`, `unrealizedPnL`, `realizedPnL`, `slPrice`, `targetPrice` — none of which exist on the domain `Position` record.

`PaperTradingStateService` saves broker-model positions to `paper_trading_positions` while the domain `Position` record is saved to `positions` — two separate tables with overlapping data.

**Impact**: Any change to position semantics requires updating 3+ models. Data drift between them is inevitable.

**Fix**: Define a single `Position` aggregate in `core`. Use the same type everywhere. JPA entities should be thin wrappers, not parallel models.

---

### H2: SignalEngine Is a God Class (384 Lines)

**File**: `backend/api/src/main/java/com/swingtrade/api/service/SignalEngine.java`

Issues:
- Depends on `OhlcvCandleRepository` and `SignalRepository` (data layer leakage) — the API module should not directly access repositories
- Performs ATR calculation inline (lines 322-337) using a crude high-low range instead of proper ATR, duplicating logic that `TechnicalIndicators` already computes
- Hardcodes entry/stop/target/risk-reward calculations (lines 183-197) that should live in the strategy or broker module
- Mixes caching annotations (`@Cacheable`, `@CacheEvict`) with `@Transactional` — Spring's proxy-based caching does not work correctly with self-invocation inside transactions
- The scheduled method `generateDailySignals()` catches all exceptions per-symbol and continues, meaning a single failed symbol silently degrades the entire scan with no alerting

**Impact**: Single point of failure for signal generation. Untestable in isolation. Impossible to extend without modification.

**Fix**: Extract into a strategy registry pattern. Create separate `SignalGenerator`, `StopLossCalculator`, and `RiskCalculator` classes. Move repository access to the data module.

---

### H3: Module Dependency Graph Violates Layered Architecture

**Current dependencies**:

```
api -> core, data, strategy, llm, broker
strategy -> core, data, llm
broker -> core, strategy, data
llm -> core, data
data -> core
core -> (standalone)
```

**Problems**:
- `api` depends on ALL other modules — it is a fat controller that directly injects repositories from the data module, bypassing the service layer
- `strategy` depends on `data` — the strategy module directly depends on `OhlcvCandleRepository`, coupling strategy logic to JPA persistence
- `broker` depends on `strategy` — order execution should be decoupled from signal generation
- `llm` depends on `data` — LLM inference coupled to data access
- **Two Spring Boot entry points** — `data.Application` and `broker.BrokerApplication` are separate `@SpringBootApplication` classes. Unclear which one is the actual deployment target.

**Impact**: Tight coupling prevents independent scaling, testing, and deployment of modules.

**Fix**: Define a clean dependency direction: `api -> broker -> strategy -> data`. `llm` should be an optional module. Consolidate to a single entry point.

---

### H4: In-Memory State With Weak Persistence Bridge

**Files**: `backend/broker/src/main/java/com/swingtrade/broker/manager/` (OrderManager, PositionManager)

`PaperTradingEngine` manages all portfolio state in-memory via `ConcurrentHashMap`. `PaperTradingStateService` is setter-injected via `@Autowired` to avoid circular dependencies. Persistence only happens when `stateService != null` checks pass.

**Problems**:
- Process restart loses all position/order/portfolio state
- In-memory `Portfolio.currentCapital` can diverge from the database `paper_trading_portfolio` row
- `PaperTradingEngine.clearAll()` resets in-memory state without clearing the database

**Impact**: Paper trading state is effectively ephemeral. The persistence layer is an afterthought, not a source of truth.

**Fix**: Make the database the source of truth. On startup, load all state from DB and populate in-memory caches.

---

### H5: Single Transaction for Entire Symbol Batch in Signal Generation

**File**: `backend/api/src/main/java/com/swingtrade/api/service/SignalEngine.java` lines 63-105

The entire `generateDailySignals()` method is wrapped in one `@Transactional`. If one symbol's signal generation throws (e.g., corrupted candle data), the entire batch is rolled back, losing all signals generated for previous symbols. The inner catch only logs — the outer transaction still rolls back.

**Impact**: One bad symbol can wipe out all signals for the day.

**Fix**: Use `@Transactional(propagation = REQUIRES_NEW)` per-symbol, or batch-commit signals incrementally.

---

### H6: PaperTradingServiceImpl.placeOrder() — Two Separate Order Stores, Execution Fails

**File**: `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingServiceImpl.java` lines 44-52

The method creates an order via `OrderManager.createBuyOrder()` (which stores it in `OrderManager`'s map), then calls `paperTradingEngine.executePendingOrder()` (which looks in `PaperTradingEngine`'s separate order map). The order will never be found for execution.

**Impact**: Paper trading order execution silently fails.

**Fix**: Use a single order store. Both the creation and execution paths should reference the same map.

---

### H7: PaperTradingMonitorService — Entire Class Is Dead Code

**File**: `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingMonitorService.java`

This `@Service` class is never imported or injected by any other class. Spring creates a bean for it via component scanning, but it is never called. Its functionality overlaps with `PortfolioSnapshotScheduler` but is never wired into the application flow.

**Impact**: Wasted maintenance burden. Confusing for new developers.

**Fix**: Delete the class unless it is actively used.

---

### H8: Strategy Parameter Drift

**Parameters defined in three places**:

| Location | File | Key Values |
|----------|------|------------|
| `StrategyParams` record | `backend/core/src/main/java/com/swingtrade/domain/StrategyParams.java` | `EMA_FAST=20`, `RSI_PERIOD=14` |
| `SwingTradingStrategy` class | `backend/strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java` lines 27-37 | `emaFastPeriod=20`, `macdFastPeriod=12` (different from StrategyParams) |
| `application-local.properties` | `backend/api/src/main/resources/application-local.properties` lines 167-176 | `strategy.ta.ema.period.fast=12`, `strategy.ta.macd.fast=12` |

`SwingTradingStrategy` does NOT reference `StrategyParams` — it uses its own hardcoded constants. `PriceActionSignalEngine` DOES reference `StrategyParams`. These are two different strategies with different parameter sets, but there is no strategy selection mechanism — both run every day.

**Impact**: Parameters are scattered across code and config with no single source of truth. Two signal engines produce signals with different indicator logic.

**Fix**: Single source of truth via Spring `@ConfigurationProperties`. Both engines should read from the same config class.

---

### H9: Missing Concurrency Control on Scheduled Jobs

**File**: `backend/data/src/main/java/com/swingtrade/data/config/SchedulingConfig.java`

`@EnableScheduling` is configured with no `@Scheduled` annotation attributes for concurrency control. No `@SchedulerLock` or distributed locking mechanism (Redisson, Spring Cloud Lock) is configured.

**Impact**: Duplicate signal generation, duplicate data ingestion, and potential data corruption in a multi-instance deployment.

**Fix**: Add `@SchedulerLock` with `lockAtMostFor` and `lockAtLeastFor` attributes. Use Redis-based distributed locking.

---

### H10: No Retry, Rate Limiting, or Circuit Breaker on Data Ingestion

**File**: `backend/data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`

- `pullDataFromUpstox` (lines 134-163) loops day-by-day with no batch fetching, no rate limiting between calls, and no retry on failure
- `processStockData` (lines 74-96) uses a single `fetchCandles` call with no error handling
- No circuit breaker pattern (Resilience4j, Spring Retry) configured
- No timeout or retry configuration on the HTTP client

**Impact**: API rate limits will be hit during backfill. Transient failures cause silent data gaps.

**Fix**: Add Resilience4j circuit breakers and rate limiters. Use batch fetching where available.

---

## Medium Findings

### M1: 6-Factor Voting vs 3-of-4 Signal Logic

- `SwingTradingStrategy` uses 6 factors with 4-of-6 threshold (`buyScore >= 4`) at line 199
- `PriceActionSignalEngine` uses 3-of-4 threshold (`rulesPassed >= 3`) at line 164

The commit `ec57227` claims "3-of-4 signal logic" but `SwingTradingStrategy` uses a different threshold. These two engines produce signals with different sensitivity.

---

### M2: BacktestScorer Has Hardcoded `true` for hasEnoughData

**File**: `backend/api/src/main/java/com/swingtrade/api/service/BacktestScorer.java` line 42

```java
true  // <-- HARDCODED
```

Always returns `true` regardless of whether the backtest produced sufficient data (e.g., 0 trades).

---

### M3: Missing Pagination on 9+ List Endpoints

- `SignalController.java` lines 48, 65, 79, 94 — no pagination on latest, symbol, date-range, type endpoints
- `TradingController.java` line 54 — no pagination on trades list
- `PositionController.java` line 89 — no pagination on status endpoint

---

### M4: Missing Validation on 7+ Settings Endpoints

**File**: `backend/api/src/main/java/com/swingtrade/api/controller/SettingsController.java` lines 38, 62, 78, 105, 116

All use raw `Map<String, String>` or `Map<String, Object>` with no `@Valid`, no field validation.

---

### M5: PMD Skipped on 4 of 5 Modules (80% of Codebase)

- `backend/data/pom.xml` line 195: `<skip>true</skip>`
- `backend/strategy/pom.xml` line 131: `<skip>true</skip>`
- `backend/llm/pom.xml` line 197: `<skip>true</skip>`
- `backend/broker/pom.xml` line 225: `<skip>true</skip>`

Only `core` and `api` modules have PMD enabled.

---

### M6: Five Test Classes `@Disabled` and Never Run

- `TradingControllerTest.java` line 33
- `PositionControllerTest.java` line 24
- `SignalControllerTest.java` line 26
- `ErrorHandlingTest.java` line 29
- `WeeklyDigestE2ETest.java` line 44

---

### M7: Duplicated ATR/Stop-Loss/Target Logic Across 4 Files

The same formula (`stopLoss = entry - 2*ATR`, `target = entry + 2.5*risk`) appears in:
- `SignalEngine.java` lines 183-196, 243-256
- `PaperTradingEngine.java` lines 290-334
- `PositionManager.java` lines 102-143

---

### M8: Database Schema Issues

- **No `UNIQUE` constraint on `symbol + date`** in `ohlcv_candles` table (V1 migration) — duplicate candles can be inserted
- **Foreign keys reference `stocks(symbol)`** — bad practice. `symbol` used as FK target in `signals`, `positions`, and `trades` tables
- **`VARCHAR(10)` for symbols** — Indian equity symbols can exceed 10 characters
- **`NUMERIC(15,4)` for prices** — overly precise for Indian equities (typically 2 decimal places)
- **No partial index on `processed` flag** in `signals` table
- **`out-of-order=true` in broker Flyway config** — dangerous, can cause schema corruption

---

### M9: No Observability Beyond Prometheus Metrics

- No distributed tracing (no OpenTelemetry, Zipkin, or Jaeger)
- No structured logging (logback uses basic patterns, not JSON)
- No custom health indicators for market data providers, LLM endpoints, or broker connections
- No alerting configuration (Prometheus present but no Alertmanager or notification rules)

---

### M10: Configuration Fragmentation

Configuration split across 6+ files (`application.properties`, `application-local.properties`, `application.yml` across api, broker, data, llm, strategy modules). Each module has its own datasource, Redis, and Flyway config. This means:
- Duplicate connection pools if all modules run in the same JVM
- Configuration drift between modules
- Placeholder credentials checked into the repo

---

## Git History Analysis

The last 9 commits show a **fix cascade pattern**:

| Commit | Type | Message |
|--------|------|---------|
| `259471b` | feat | Add sentiment accuracy migrations, dashboard components |
| `27c7f62` | fix | Fix build and runtime issues from sentiment accuracy overhaul |
| `ec57227` | feat | Overhaul sentiment accuracy tracking, auto-data-pull, 3-of-4 signal logic |
| `3d3b5b5` | fix | Dashboard filter HOLD signals |
| `4a80e2b` | fix | Replace stub performance metrics, wire real services |
| `92dc494` | fix | Resolve circular dependency, ClassCastException, duplicate endpoint, PMD ruleset |

Each feature merge is immediately followed by a fix commit. This indicates features are being merged in a broken state due to insufficient test coverage and the disabled test suite.

---

## Recommended Priority Actions

| Priority | Action | Effort | Impact |
|----------|--------|--------|--------|
| **P0** | Add Spring Security + move credentials to env/secrets manager | Low | Security |
| **P0** | Fix paper trading P&L bugs (C4, C5, C6) | Low | Data integrity |
| **P0** | Fix LiveTradingService CANCELLED-on-timeout bug | Low | Financial risk |
| **P1** | Unify Position/Signal models to single source of truth | Medium | Data integrity |
| **P1** | Extract SignalEngine into strategy registry | Medium | Extensibility |
| **P1** | Centralize configuration, enable PMD on all modules | Low | Maintainability |
| **P1** | Re-enable disabled tests, add coverage for critical paths | Medium | Correctness |
| **P2** | Add circuit breakers for external APIs | Medium | Reliability |
| **P2** | Add distributed locking for scheduled jobs | Low | Concurrency safety |
| **P2** | Clean up module dependencies (break api->data direct access) | Medium | Architecture |
| **P2** | Add unique constraint on (symbol, date) for candles | Low | Data integrity |
| **P3** | Add distributed tracing and structured logging | Medium | Observability |
| **P3** | Standardize strategy parameter management | Low | Consistency |

---

## Appendix: Module Dependency Map

```
api ──> core, data, strategy, llm, broker    (fat controller — depends on everything)
strategy ──> core, data, llm                 (strategy coupled to JPA repos)
broker ──> core, strategy, data              (broker depends on strategy)
llm ──> core, data                           (LLM coupled to data access)
data ──> core                                (clean)
core ──> (standalone)                        (clean)
```

**Ideal dependency direction**: `api -> broker -> strategy -> data <- llm`

---

## Appendix: File Reference — All Finding Locations

### Critical
| ID | File | Lines |
|----|------|-------|
| C1 | (all controllers) | — |
| C2 | `backend/docker-compose.yml` | 100-101, 135-136 |
| C2 | `backend/docker-compose.infra-dev.yml` | 18-19 |
| C2 | `backend/docker-compose.infra-stage.yml` | 19-20, 54-55 |
| C2 | `backend/docker-compose.monitoring-stage.yml` | 14 |
| C3 | `backend/broker/src/main/java/com/swingtrade/broker/factory/LiveTradingService.java` | 87-91 |
| C4 | `backend/broker/src/main/java/com/swingtrade/broker/engine/PaperTradingEngine.java` | 396-402 |
| C5 | `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java` | 98-107 |
| C6 | `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java` | 83-96 |
| C7 | `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java` | 125-199 |

### High
| ID | File | Lines |
|----|------|-------|
| H2 | `backend/api/src/main/java/com/swingtrade/api/service/SignalEngine.java` | 1-384 (entire file) |
| H4 | `backend/broker/src/main/java/com/swingtrade/broker/manager/` | — |
| H5 | `backend/api/src/main/java/com/swingtrade/api/service/SignalEngine.java` | 63-105 |
| H6 | `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingServiceImpl.java` | 44-52 |
| H7 | `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingMonitorService.java` | — |
| H8 | `backend/core/.../StrategyParams.java`, `backend/strategy/.../SwingTradingStrategy.java`, `backend/api/.../application-local.properties` | 167-176 |
| H10 | `backend/data/src/main/java/com/swingtrade/data/service/DataIngestionService.java` | 74-96, 134-163 |

### Medium
| ID | File | Lines |
|----|------|-------|
| M2 | `backend/api/src/main/java/com/swingtrade/api/service/BacktestScorer.java` | 42 |
| M3 | `backend/api/src/main/java/com/swingtrade/api/controller/SignalController.java` | 48, 65, 79, 94 |
| M3 | `backend/api/src/main/java/com/swingtrade/api/controller/TradingController.java` | 54 |
| M3 | `backend/api/src/main/java/com/swingtrade/api/controller/PositionController.java` | 89 |
| M4 | `backend/api/src/main/java/com/swingtrade/api/controller/SettingsController.java` | 38, 62, 78, 105, 116 |
| M7 | `backend/api/.../SignalEngine.java` | 183-196, 243-256 |
| M7 | `backend/broker/.../PaperTradingEngine.java` | 290-334 |
| M7 | `backend/broker/.../PositionManager.java` | 102-143 |