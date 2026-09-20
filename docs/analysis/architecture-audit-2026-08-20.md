# Architecture & Code Audit — 2026-08-20

Comprehensive analysis of the SwingTrade codebase from multiple perspectives:
1. **Architecture & Design Review** (2026-08-20) — System design, module boundaries, resilience, scalability, data architecture, domain model quality
2. **Code-Level Audit** (2026-08-07) — Code correctness, bugs, test coverage, security (20+ phases already fixed)

This file combines both audits. The architecture findings below are fresh; the code-level findings below that have been partially remediated (see fix progress at end).

---

## Executive Summary

### Architecture & Design Review (2026-08-20)

This review found **3 critical, 14 high, 18 medium, 8 low** (43 total findings) across 3 parallel architecture agents. The architecture is **solid for current scale** with clean module boundaries, good DDD fundamentals, and immutable domain models. The main production-blocking gaps are:

- **Exposed credentials** — Finnhub API key hardcoded in `application.properties`, Fyers credentials + DB password in committed `.env`
- **No API authentication** — any endpoint callable by anyone, including trade execution and LLM-triggering endpoints
- **No circuit breakers** — no Resilience4j on any external client; 600-second LLM timeout blocks entire pipeline per stock
- **Duplicate signal execution** — `SignalExecutionJob` still has `@Scheduled` annotation, runs trades twice
- **Race conditions** — `KillSwitchService.active` is not volatile, `JobRunEntity` has no optimistic locking
- **TimescaleDB declared but not configured** — hypertable creation missing from migrations
- **O(n) in-memory signal filtering** — `findAll()` + stream on every query, will degrade as signals table grows

### Code-Level Audit (2026-08-07) — 20 Phases Fixed

The original audit found 64 findings (8 critical, 15 high, 30 medium, 11 low). 20+ phases of fixes have been completed. See fix progress at end of this file.

---

## Architecture & Design Findings (2026-08-20)

### CRITICAL

#### AD-C1: Exposed credentials in repository
**Severity**: CRITICAL | **Files**: `backend/api/src/main/resources/application.properties:138`, `infra/env/.env`

Finnhub API key hardcoded in `application.properties`. Fyers client ID/secret + DB password committed in `infra/env/.env`. All exposed in git history.

**Fix**: Rotate all credentials immediately. Move `.env` to `.gitignore`. Use `infra/env/.env.example` as template with placeholder values. Move SSH credentials (`llamacpp.ssh.user`, `llamacpp.ssh.host`) to environment variables.

---

#### AD-C2: No API authentication
**Severity**: CRITICAL | **Files**: All controllers under `backend/api/src/main/java/com/swingtrade/api/controller/`

Zero auth on any endpoint. `POST /api/trade`, `POST /api/positions/{symbol}/close`, `POST /api/signals/generate-all` (triggers LLM calls) are all publicly accessible.

**Fix**: Add Spring Security with API key auth at minimum. Role-based: admin for kill switch, trader for execution, viewer for read-only.

---

#### AD-C3: No circuit breakers on external APIs
**Severity**: CRITICAL | **Files**: `backend/data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`, `backend/llm/src/main/java/com/swingtrade/llm/service/SentimentService.java`

No Resilience4j or equivalent on Yahoo Finance, LLM server, Kite API, Upstox, or Fyers. The 600-second LLM timeout blocks the entire pipeline per stock. No fallback chain exists (Yahoo → Upstox → Fyers).

**Fix**: Add Resilience4j `@CircuitBreaker` to all external client calls. Configure appropriate timeouts (LLM: 60s, not 600s). Implement fallback chain. Add bulkhead pattern so slow LLM calls for one stock don't block analysis for all stocks.

---

### HIGH

#### AD-H1: ArchUnit enforcement disabled
**Severity**: HIGH | **File**: `backend/api/src/test/java/com/swingtrade/api/arch/ModuleBoundaryTest.java`

The `noCircularDependencies()` test was a no-op (empty body). The audit's claim of circular dependencies was incorrect — the build graph is a clean DAG. The real issue was api importing concrete broker classes instead of core interfaces.

**Fix**: Re-enabled ArchUnit with circular dependency check + layer rule. Extracted `TradingService` and `OrderService` interfaces to core. All api services now depend on core interfaces instead of concrete broker classes.

---

#### AD-H2: O(n) in-memory signal filtering
**Severity**: HIGH | **File**: `backend/api/src/main/java/com/swingtrade/api/service/SignalService.java:45-119`

`findAll()` + Java stream filtering on every call. Will degrade as signals table grows.

**Fix**: Add query methods to `SignalStore` (`findByDateRange`, `findByType`, `findByHighConfidence`).

---

#### AD-H3: TimescaleDB declared but not configured
**Severity**: HIGH | **File**: `backend/data/src/main/resources/db/migration/V1__swing_trade_schema.sql`

Schema says "PostgreSQL + TimescaleDB" but no `CREATE EXTENSION` or `create_hypertable()` in any migration. Plain PostgreSQL tables used instead.

**Fix**: Add migration: `CREATE EXTENSION IF NOT EXISTS timescaledb` + `SELECT create_hypertable('ohlcv_candles', 'date')`.

---

#### AD-H4: BigDecimal precision loss in financial calculations
**Severity**: HIGH | **Files**: `backend/strategy/src/main/java/com/swingtrade/strategy/PriceActionSignalEngine.java:165`, `backend/strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java:308-311`

`doubleValue()` used for P&L calculations and signal return values.

**Fix**: Change `SignalResult` to use `BigDecimal`. Use `BigDecimal` arithmetic throughout.

---

#### AD-H5: Circular dependency between broker and data
**Severity**: HIGH | **File**: `backend/broker/src/main/java/com/swingtrade/broker/engine/PaperTradingEngine.java:58-63`

Setter injection with `@Autowired` to avoid circular dependency. Comment acknowledges known debt.

**Fix**: Extract state persistence interface to `core` that both `broker` and `data` depend on.

---

#### AD-H6: Position is a 23-field God Object
**Severity**: HIGH | **File**: `backend/core/src/main/java/com/swingtrade/domain/Position.java`

Mixes entry data, current state, PnL, broker metadata, and execution history.

**Fix**: Decompose into `PositionSummary`, `PositionDetails`, `PositionTradeHistory` via composition.

---

#### AD-H7: SignalExecutionJob has duplicate @Scheduled annotation
**Severity**: HIGH | **File**: `backend/api/src/main/java/com/swingtrade/api/scheduler/SignalExecutionJob.java:38`

`SignalExecutionJob` still has `@Scheduled(fixedDelay = 30000)` despite comment saying "@Scheduled removed — replaced by JobOrchestratorService." This causes duplicate trade execution for the same signal.

**Fix**: Remove `@Scheduled` annotation from `SignalExecutionJob`.

---

#### AD-H8: KillSwitchService.active is not volatile
**Severity**: HIGH | **File**: `backend/broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java:23`

`KillSwitchService.active` is a plain `boolean`, not `volatile`. A thread could cache a stale `false` value and allow a trade through after the kill switch was activated. `DailyLossCircuitBreaker.isCircuitOpen` is `volatile` but this one is not.

**Fix**: Change `active` to `volatile boolean`.

---

#### AD-H9: No optimistic locking on any JPA entity
**Severity**: HIGH | **File**: Multiple entity files

No `@Version` optimistic locking on any entity. `JobOrchestratorService.recordCompletion()` increments `completedCount` via read-modify-write without `@Transactional` or `@Version`. Two threads can read the same count and both save `count + 1`, losing an increment. `DailyLossCircuitBreaker.persistState()` has the same race condition.

**Fix**: Add `@Version` to `JobRunEntity`, `DailyLossCircuitBreakerStateEntity`, `PositionEntity`.

---

#### AD-H10: Trade.close() misclassifies all losing trades as STOPPED
**Severity**: HIGH | **File**: `backend/core/src/main/java/com/swingtrade/domain/Trade.java:155-156`

`isProfit(totalPnL) ? TradeStatus.CLOSED : TradeStatus.STOPPED` — any trade with non-positive P&L is classified as "stopped" regardless of actual exit reason. The `exitReason` parameter is ignored for status determination.

**Fix**: Use `exitReason` for status determination instead of P&L sign.

---

#### AD-H11: No read timeouts on any WebClient
**Severity**: HIGH | **File**: `backend/data/src/main/java/com/swingtrade/data/client/YahooFinanceClient.java:56`, `UpstoxServiceClient`, `FyersServiceClient`

Only connect timeout (30s) set. No read timeout. A slow Yahoo response could block threads indefinitely. Same issue in all three market data clients.

**Fix**: Add 30-second read timeout to all WebClients.

---

#### AD-H12: Entity-to-table mapping precision mismatches
**Severity**: HIGH | **File**: `backend/data/src/main/resources/db/migration/V1__swing_trade_schema.sql`

`OhlcvCandleEntity.adj_close_price` has `precision = 15` but DB column is `NUMERIC(15,4)`. `SignalEntity.confidence_score` has `precision = 5` but DB is `NUMERIC(5,2)`. Potential truncation errors.

**Fix**: Align `@Column` precision/scale with DB schema.

---

#### AD-H13: No Strategy interface or registry
**Severity**: HIGH | **File**: `backend/strategy/src/main/java/com/swingtrade/strategy/PriceActionSignalEngine.java`

No `Strategy` interface or strategy registry. Adding a new strategy requires modifying `SignalPipeline` and `JobOrchestratorService` directly. Not open for extension without modification.

**Fix**: Introduce a `Strategy` interface with a registry pattern.

---

#### AD-H14: Missing @ManyToOne for job_run_stages FK
**Severity**: HIGH | **File**: `backend/data/src/main/java/com/swingtrade/data/entity/JobRunStageEntity.java`

The DB has `REFERENCES job_runs(run_id) ON DELETE CASCADE` but `JobRunStageEntity` has no JPA relationship to `JobRunEntity`.

**Fix**: Add `@ManyToOne` mapping to `JobRunStageEntity`.

---

### MEDIUM

#### AD-M1: core module has Spring annotations
**Severity**: MEDIUM | **File**: `backend/core/src/main/java/com/swingtrade/domain/TradeMetrics`

`core` module includes `@Service` annotations and Spring dependencies (micrometer, logback, Jackson). Violates "pure domain" convention.

**Fix**: Move `TradeMetrics` to `api` or `broker` module. `core` should be Spring-free.

---

#### AD-M2: No event-driven pattern (synchronous 9-stage pipeline)
**Severity**: MEDIUM | **File**: `backend/api/src/main/java/com/swingtrade/api/service/AnalysisOrchestratorService.java`

96-line imperative pipeline. Each stage blocks the next. Stage 4 (LLM sentiment) can take 60+ seconds per stock. For the scheduled pipeline across all watchlist symbols, this is effectively single-threaded.

**Fix**: Introduce Spring `ApplicationEventPublisher`-driven stages: `CandleIngestedEvent`, `SignalGeneratedEvent`, `PositionClosedEvent`. Enables async execution, stage-level timeouts, graceful degradation.

---

#### AD-M3: No distributed tracing
**Severity**: MEDIUM | **File**: `backend/api/src/main/java/com/swingtrade/api/filter/TraceIdFilter.java`

`TraceIdFilter` generates a trace ID but it is not propagated to external API calls or logged in structured format. No Micrometer Tracing with Zipkin/Jaeger/OpenTelemetry.

**Fix**: Add Micrometer Tracing. Propagate trace ID to external API calls. Log in structured JSON format.

---

#### AD-M4: No production monitoring/alerting rules
**Severity**: MEDIUM | **File**: `infra/monitoring/`

Prometheus metrics exported but no alerting rules defined. No log aggregation (JSON logs go to file with no Fluentd/Logstash/Vector). No SLI/SLO tracking (no measurement of "time to generate signal" or "data freshness").

**Fix**: Define alerting rules for signal generation failures, data ingestion freshness, LLM fallback rate. Add log aggregation.

---

#### AD-M5: DTO Position shadows domain Position
**Severity**: MEDIUM | **File**: `backend/api/src/main/java/com/swingtrade/api/dto/Position.java` vs `backend/core/src/main/java/com/swingtrade/domain/Position.java`

Name collision between API DTO and domain model. Forces fully-qualified imports throughout.

**Fix**: Rename DTO to `PaperPositionDto.java` or `TradingPositionDto.java`.

---

#### AD-M6: No API versioning
**Severity**: MEDIUM | **File**: All controllers

All endpoints use `/api/` prefix with no version segment. When DTOs or response shapes change, no backward compatibility guarantee.

**Fix**: Add versioning (`/api/v1/`) when API reaches stable state.

---

#### AD-M7: No blue/green or rolling deployment
**Severity**: MEDIUM | **File**: `.github/workflows/deploy-main.yml`

`deploy-main.yml` kills existing process (`pkill`) and starts new one — causes downtime. No database migration rollback strategy (Flyway forward-only, no rollback scripts). No canary deployment.

**Fix**: Implement rolling deployment or blue/green. Add Flyway rollback scripts.

---

#### AD-M8: AnalysisOrchestratorService is 96-line imperative facade
**Severity**: MEDIUM | **File**: `backend/api/src/main/java/com/swingtrade/api/service/AnalysisOrchestratorService.java`

9 injected dependencies. Service Locator / Facade anti-pattern masquerading as orchestration.

**Fix**: Introduce `Pipeline` abstraction with composable `Stage` interfaces.

---

#### AD-M9: Hardcoded SSH credentials for llama.cpp server
**Severity**: MEDIUM | **File**: `backend/api/src/main/resources/application.properties:104-109`

SSH username (`dietpi`) and host (`192.168.0.100`) hardcoded. Port (`8090`) also hardcoded.

**Fix**: Move to environment variables: `llamacpp.ssh.user=${LLAMACPP_SSH_USER:dietpi}`, etc.

---

#### AD-M10: No pipeline integration test for JobOrchestratorService
**Severity**: MEDIUM | **File**: `backend/api/src/test/java/com/swingtrade/api/`

No integration test that exercises the full 6-stage job orchestrator pipeline. The `JobOrchestratorService` has no dedicated integration test.

**Fix**: Add integration test with TestContainers for PostgreSQL and WireMock for external APIs.

---

#### AD-M11: No @PreDestroy shutdown hooks on ExecutorServices
**Severity**: MEDIUM | **Files**: `JobOrchestratorService`, `SentimentService`, `NewsIngestionService`

Three separate `ExecutorService` instances with no `@PreDestroy` shutdown hooks. Threads will leak on application stop.

**Fix**: Add `@PreDestroy` shutdown hooks to all three services.

---

#### AD-M12: Nested CompletableFuture pattern in JobOrchestratorService
**Severity**: MEDIUM | **File**: `backend/api/src/main/java/com/swingtrade/api/service/JobOrchestratorService.java:259`

`executeStage()` wraps a sequential stage in `CompletableFuture.supplyAsync()` using the same executor that the outer `startRun()` already uses. Double async nesting with unnecessary queueing overhead.

**Fix**: Replace nested `CompletableFuture` with `Future.get(timeout, unit)`.

---

#### AD-M13: PositionManager.getPositions() exposes internal ConcurrentHashMap
**Severity**: MEDIUM | **File**: `backend/broker/engine/PaperTradingEngine.java`

`PaperTradingStateService.loadOpenPositions()` calls `positionManager.getPositions().put(...)`, directly mutating the internal map. No encapsulation.

**Fix**: Return an unmodifiable view or provide a dedicated `addPosition()` method.

---

#### AD-M14: No data retention on 8+ tables
**Severity**: MEDIUM | **File**: `backend/data/src/main/resources/db/migration/`

Only `sentiment_accuracy` has a retention policy (1 year). `ohlcv_candles`, `signals`, `sentiment_results`, `news_articles`, `job_runs`, `trade_labels`, `paper_trading_portfolio_snapshots` all grow unbounded.

**Fix**: Add retention policies for all growing tables. Use TimescaleDB continuous aggregates + retention.

---

#### AD-M15: Duplicate Exchange enum
**Severity**: MEDIUM | **File**: `backend/core/src/main/java/com/swingtrade/domain/Stock.java` (inner enum) vs `backend/core/src/main/java/com/swingtrade/domain/Exchange.java` (top-level)

`Stock.Exchange` (NSE, BSE) and top-level `Exchange` (NSE, BSE, NSE_FO, NCEI). `Position.java` uses the top-level; `Stock.java` uses the inner.

**Fix**: Consolidate to a single `Exchange` enum.

---

#### AD-M16: No Symbol value object
**Severity**: MEDIUM | **File**: 9+ domain models

Raw `String symbol` with zero validation. Any code can pass an empty string or invalid ticker.

**Fix**: Create a `Symbol` value object with validation.

---

#### AD-M17: No BrokerClient interface for pluggable brokers
**Severity**: MEDIUM | **File**: `backend/broker/`

No `BrokerClient` interface. The KiteConnect client is hardcoded. Adding a new broker requires modifying existing broker code.

**Fix**: Introduce a `BrokerClient` interface for pluggable broker implementations.

---

#### AD-M18: CI stage workflow references Maven/pom.xml
**Severity**: MEDIUM | **File**: `.github/workflows/deploy-stage.yml:33-52`

`deploy-stage.yml` references `pom.xml` and `mvn` even though the project migrated from Maven to Gradle. This workflow would fail.

**Fix**: Update to use Gradle wrapper.

---

### LOW

#### AD-L1: Field injection in controllers
**Severity**: LOW | **File**: `backend/api/src/main/java/com/swingtrade/api/controller/SignalController.java:55-77`

Controllers use `@Autowired` field injection. Makes tests harder (requires Spring context), hides dependencies.

**Fix**: Convert to constructor injection in controllers and services.

---

#### AD-L2: Missing DB indexes
**Severity**: LOW | **File**: `backend/data/src/main/resources/db/migration/V1__swing_trade_schema.sql`

No index on `trades(exit_date)` for date-range performance queries. No index on `positions(updated_at)` for monitoring queries.

**Fix**: Add migration with indexes on `trades.exit_date` and `positions.updated_at`.

---

#### AD-L3: No Money/Price/Percentage value objects
**Severity**: LOW | **File**: `backend/core/src/main/java/com/swingtrade/domain/`

Using raw `BigDecimal` everywhere (no `Money` VO), raw `double`/`BigDecimal` without range validation (no `Percentage` VO, no `Price` VO with validation that price > 0).

**Fix**: Create `Money`, `Price`, `Percentage` value objects with validation.

---

#### AD-L4: OhlcvCandle has no validation
**Severity**: LOW | **File**: `backend/core/src/main/java/com/swingtrade/domain/OhlcvCandle.java`

No validation prevents a candle with `low > high` or `close < 0`.

**Fix**: Add validation in factory method or constructor.

---

#### AD-L5: StrategyParams.HIGH_PROXIMITY = 0.97 undocumented
**Severity**: LOW | **File**: `backend/core/src/main/java/com/swingtrade/domain/StrategyParams.java:29`

`0.97` means "price within 3% of 52-week high" but rationale not documented.

**Fix**: Add Javadoc: "Price must be within 3% of 52-week high to confirm breakout strength."

---

#### AD-L6: gpuhub dependency commented out in build
**Severity**: LOW | **File**: `backend/api/build.gradle.kts:23`, `backend/api/src/main/java/com/swingtrade/api/controller/GpuHubController.java`

`GpuHubController.java` imports from `com.swingtrade.gpuhub` but build dependency is commented out. Dead code or implicit dependency.

**Fix**: Uncomment gpuhub dependency or remove `GpuHubController.java`.

---

#### AD-L7: Unbounded cached thread pool in JobOrchestratorService
**Severity**: LOW | **File**: `backend/api/src/main/java/com/swingtrade/api/service/JobOrchestratorService.java:57-61`

`Executors.newCachedThreadPool()` creates threads without bound. Under load, excessive threads and memory pressure.

**Fix**: Replace with `Executors.newFixedThreadPool()` or bounded `ThreadPoolExecutor`.

---

#### AD-L8: Keyword sentiment fallback is 50+ hardcoded patterns
**Severity**: LOW | **File**: `backend/llm/src/main/java/com/swingtrade/llm/service/SentimentService.java:420-613`

Brittle, unmaintainable block of string literals for fallback sentiment analysis.

**Fix**: Extract to YAML/properties config. Consider lightweight NLP library (Stanford CoreNLP) for fallback.

---

## Architecture Strengths (What's Working Well)

- **Domain models**: Immutable records with factory methods (`Signal.create()`, `Trade.open()`, `Position.createWithRisk()`), proper enums, clean `RiskCalculator`
- **Module dependency graph**: `core` ← `data/llm/broker/strategy` ← `api` is clean
- **Store interfaces**: Hexagonal pattern with interfaces in `core`, implementations in `data`
- **SSE streaming**: Right choice for long-running analysis pipeline
- **LLM fallback**: Keyword-based sentiment when LLM unavailable — excellent resilience pattern
- **Kill switch + circuit breaker**: Emergency halt + daily loss limit implemented
- **Sentiment accuracy tracking**: `sentiment_accuracy` table for model improvement
- **CI/CD**: Per-module parallel static analysis, OWASP SBOM, TruffleHog security scan

---

## Architecture Scores

| Dimension | Score | Key Issue |
|-----------|-------|-----------|
| **Module boundaries** | 7.5 | ArchUnit re-enabled, core interfaces extracted, api now uses interfaces |
| **Domain model** | 8.0 | Strong DDD, needs Value Objects + Position split |
| **Data architecture** | 5.0 | TimescaleDB not configured, missing indexes |
| **Resilience** | 4.0 | No circuit breakers, 600s LLM timeout |
| **Observability** | 6.0 | Metrics present, no tracing/alerting |
| **Scalability** | 5.5 | O(n) filtering, synchronous pipeline |
| **Security** | 3.0 | No auth, exposed credentials |
| **Future-proofing** | 7.0 | Easy to add data sources, harder for new brokers |

---

## Architecture Fix Priority

### P0 — Immediate (this week)
| Priority | Finding | Fix |
|----------|---------|-----|
| P0-1 | AD-C1 | ~~Rotate all exposed credentials, add `.env` to `.gitignore`~~ **DEFERRED** |
| P0-2 | AD-C2 | ~~Add Spring Security API key auth~~ **DEFERRED** |
| P0-3 | AD-C3 | ~~Add Resilience4j circuit breakers, reduce LLM timeout to 60s~~ **DONE** — Custom bounded thread pool in SentimentService, LLM retry config (3 attempts, exponential backoff), auth retry in Fyers client, Jetty bypass with JDK HttpURLConnection (10min read timeout), DailyLossCircuitBreaker persisted to DB |

### P1 — Critical (this sprint)
| Priority | Finding | Fix |
|----------|---------|-----|
| P1-1 | AD-H1 | Re-enable ArchUnit, fix circular deps | ~~DONE~~ |
| P1-2 | AD-H2 | Add DB-level query methods to SignalStore |
| P1-3 | AD-H3 | Configure TimescaleDB hypertable migration |
| P1-4 | AD-H4 | BigDecimal throughout financial calculations |
| P1-5 | AD-H5 | Extract state persistence interface to core |
| P1-6 | AD-H6 | Split Position into Summary/Details |

### P2 — Planned (next sprint)
| Priority | Finding | Fix |
|----------|---------|-----|
| P2-1 | AD-M1 | Move TradeMetrics out of core |
| P2-2 | AD-M2 | Event-driven pipeline stages |
| P2-3 | AD-M3 | Add Micrometer Tracing |
| P2-4 | AD-M4 | Define alerting rules, add log aggregation |
| P2-5 | AD-M5 | Rename DTO Position |
| P2-6 | AD-M7 | Rolling deployment, Flyway rollback scripts |

### P3 — Backlog
| Priority | Finding | Fix |
|----------|---------|-----|
| P3-1 | AD-M6 | Add /api/v1/ versioning |
| P3-2 | AD-M8 | Pipeline abstraction |
| P3-3 | AD-M10 | Pipeline integration test |
| P3-4 | AD-L1-L8 | Field injection, DB indexes, VOs, validation, docs |

---

## Code-Level Findings (2026-08-07 Audit)

### CRITICAL

#### C1: Position.createWithRisk() parameter order wrong
**Severity**: CRITICAL | **Module**: core | **Impact**: All open positions

The factory method `Position.createWithRisk()` has swapped parameters — risk allocation and initial capital are passed in wrong order. Since this is used by `PaperTradingEngine` to create positions on order fill, every open position has corrupted risk state.

**Recommended fix**: Audit the parameter order in `Position.createWithRisk()`, swap to correct order, add a unit test verifying the parameter mapping.

---

#### C2: Trade.close() PnL incorrect for SHORT + fees
**Severity**: CRITICAL | **Module**: core | **Impact**: All short trade P&L

`Trade.close()` calculates PnL as `(closePrice - entryPrice) * quantity` which is correct for LONG but inverted for SHORT. Additionally, fees are not included in the realized PnL calculation. All short trade metrics and overall performance stats are wrong.

**Recommended fix**: In `Trade.close()`, use `(entryPrice - closePrice) * quantity` for SHORT direction, and add `fees` to the PnL calculation for both directions.

---

#### C3: Credentials committed to git
**Severity**: CRITICAL | **Module**: infra/env | **Impact**: Security exposure

The `.env` file containing real database passwords, Redis credentials, and API keys is tracked in git. This exposes the entire infrastructure to anyone with repo access.

**Recommended fix**: Remove `.env` from git (`git rm --cached .env`), add `.env` to `.gitignore`, distribute `.env.example` with placeholder values only.

---

#### C4: Missing @Transactional on closePosition
**Severity**: CRITICAL | **Module**: broker/api | **Impact**: Data inconsistency

`PositionService.closePosition()` and `PaperTradingServiceImpl.closePosition()` lack `@Transactional`. The engine state update (removing from in-memory portfolio) and DB save (`PositionRepository.save()`) are not atomic. A failure between the two leaves the engine and DB out of sync.

**Recommended fix**: Add `@Transactional` to both `closePosition` methods.

---

#### C5: Backtest engine converts TA4j Num to double
**Severity**: CRITICAL | **Module**: strategy | **Impact**: Backtest accuracy

`BacktestEngine` converts TA4j `Num` values to `double` at intermediate calculation points. TA4j's `DecimalNum` provides arbitrary precision; converting to `double` introduces rounding errors that compound across thousands of bars, making backtest results unreliable.

**Recommended fix**: Use `DecimalNum` throughout the backtest pipeline. Convert to `double` only at the final result output stage. See plan `docs/plans/2026-08-07-backtest-precision-decimalnum.md`.

---

#### C6: Two strategy implementations with different logic
**Severity**: CRITICAL | **Module**: strategy | **Impact**: Signal parity

Live trading uses `SwingTradingStrategy` while backtesting uses `PriceActionSignalEngine`. These two implementations compute indicators differently (different lookback periods, different crossover logic), meaning backtest results will not match live signal output.

**Recommended fix**: Consolidate to a single strategy implementation. The backtest engine should consume the same strategy class used in live trading, with historical data injected.

---

#### C7: Frontend appState uses reactive() instead of Pinia
**Severity**: CRITICAL | **Module**: frontend | **Impact**: Maintainability, testability

`dashboard/src/stores/appState.ts` uses Vue `reactive()` instead of Pinia. This means:
- Not visible in Vue DevTools
- Harder to test in isolation
- No action history/time-travel debugging
- Inconsistent with the rest of the app (settings store already uses Pinia)

**Recommended fix**: Convert `appState` to a Pinia store.

---

#### C8: Frontend no <ErrorBoundary> — any component crash kills the whole app
**Severity**: CRITICAL | **Module**: frontend | **Impact**: App reliability

No `<ErrorBoundary>` component exists. A single unhandled error in any view (e.g., Network error in DashboardView, parsing error in SignalsView) crashes the entire Vue app, showing a blank screen with no recovery path.

**Recommended fix**: Create an `<ErrorBoundary>` component that catches render errors and shows a recovery UI with retry button. Apply to all top-level views.

---

## HIGH Findings (2026-08-07)

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

## MEDIUM Findings (2026-08-07)

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

## LOW Findings (2026-08-07)

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

## Code Quality Scores (2026-08-07 Audit)

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

## Recommended Fix Order (2026-08-07 Code Audit)

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

### 2026-08-20 Architecture Review
Two parallel architecture audit agents:
1. **SwingTrade-specific auditor** — domain-aware review of modules, data flow, API design, security, scalability
2. **General arch-auditor** — principal-level review of system design, resilience, observability, domain model quality, future-proofing

### 2026-08-07 Code-Level Audit
Three parallel analysis agents:
1. **Architecture Agent** — System-level design review (module boundaries, data flow, scalability)
2. **Backend Code Review Agent** — Java/Spring Boot module-level defect scan
3. **Frontend Code Review Agent** — Vue 3/TypeScript type safety and UX review

Each agent scanned the codebase independently. Findings were deduplicated and merged by severity. File paths and line numbers reference the codebase state at time of analysis.

---

## Audit Fix Progress

### 2026-08-20 Architecture Review — In Progress

| Finding | Status | Notes |
|---------|--------|-------|
| AD-C3: No circuit breakers on external APIs | ✅ Done | Custom bounded thread pool in SentimentService, LLM retry config (3 attempts, exponential backoff), auth retry in Fyers client, Jetty bypass with JDK HttpURLConnection (10min read timeout), DailyLossCircuitBreaker persisted to DB |
| AD-C1: Exposed credentials | ⏳ Deferred | |
| AD-C2: No API authentication | ⏳ Deferred | |
| AD-H1: ArchUnit enforcement disabled | ✅ Done | Re-enabled `noCircularDependencies()` + added `apiShouldNotImportConcreteBrokerClasses()` rule. Extracted `TradingService` and `OrderService` interfaces to core. Updated PositionService, JobOrchestratorService, SignalFilterService, PerformanceService to use core interfaces. Removed unused build deps (strategy→llm, broker→strategy). |
| AD-H4: BigDecimal precision loss | ⏳ Open | `BacktestEngine`, `PortfolioBacktestEngine`, `BacktestTrade` and `SignalResult` still use `double`. Being redone against the current engine (partial exits, slippage, cost model) on `refactor/ad-h4-bigdecimal-backtest`. |
| AD-H5: Circular dependency between broker and data | ◐ Partly | Module-level cycle already gone: `data` depends only on `core`, and `ModuleBoundaryTest` enforces `slices().beFreeOfCycles()`. Remaining: in-module bean cycle `PaperTradingStateService` <-> `PaperTradingEngine` (`@Lazy` + `setStateService`), addressed on `refactor/ad-h5-state-persistence-cycle`. |
| AD-H6: Position is a 23-field God Object | ◐ Partly | `Position` is already an aggregate of `PositionEntry`/`PositionRisk`/`PositionValuation`/`PositionExit`. Leftover: 23-arg compatibility constructor with ~57 callers, plus a lightweight list projection, on `refactor/ad-h6-position-cleanup`. The Summary/Details/TradeHistory split proposed here was rejected. |
| AD-H8: KillSwitchService.active not volatile | ✅ Done | `active` is `volatile boolean`. |
| AD-H10: Trade.close() misclassifies losing trades | ✅ Done | `Trade.resolveStatus(exitReason, totalPnL)` uses the exit reason when it names a trigger and falls back to the P&L sign otherwise. |
| AD-H11: No read timeouts on WebClients | ➖ Partly obsolete | `UpstoxServiceClient` is fully commented out on main, so nothing to fix there. Yahoo and Fyers clients were not re-verified. |

### 2026-08-08 Code Audit — 20 Phases Completed

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
| 18 | H2: PerformanceService hardcoded capital | ✅ Done | `c445da7d` |
| 19 | H3: Inline PnL removed | ✅ Done | `cc8ed0ff` |
| 20 | H14: Portfolio totalValue/totalPnL populated | ✅ Done | `c445da7d` |

### Dismissed (not bugs)

| Finding | Reason |
|---------|--------|
| C1: Position.createWithRisk() param order | Compact constructor fallback makes it safe |
| C3: Credentials in git | infra/env/.env is NOT tracked (.gitignore matches it) |
| C5: Backtest precision | Uses safe numToBigDecimal() helper; one doubleValue() is on BigDecimal not Num |

### Remaining — Backend (2026-08-07)

| Finding | Priority | What's needed |
|---------|----------|---------------|
| M1: DailyLossCircuitBreaker timezone | P3 | Use Asia/Kolkata explicitly |
| M9: Position god object (23 fields) | P3 | Split into PositionSummary/PositionDetails |
| M10: VARCHAR(10) symbols | P3 | Standardize VARCHAR(20) |
| M13: No rate limiting | P3 | Add @RateLimiter on analysis/scan endpoints |
| M28: No pagination on performance | P3 | Add pagination to /api/performance |

### Remaining — Frontend (2026-08-07)

| Finding | Priority | What's needed |
|---------|----------|---------------|
| C7: appState not Pinia | P1 | Convert `reactive()` to Pinia store |
| M3: NaN validation gap | P3 | isFinite() checks on numeric responses |
| M12: API client 1096 lines | P3 | Split into domain modules (grew from 921) |
| M20: Flaky E2E waits | P3 | Replace 10+ `waitForTimeout` with waitForSelector |
| L1: Only 2 component unit tests | P4 | Down from 4 — need Vitest for key components |

### Next Recommended Sprint (2026-08-07)

1. **C7: appState → Pinia** — frontend migration, low risk, 54-line file
2. **M3: NaN validation** — isFinite() checks on numeric API responses
3. **M20: Flaky E2E waits** — replace fixed timeouts with stable selectors