---
name: arch-auditor
description: Architecture audit agent that validates module boundaries, dependency graph, code quality patterns, and detects technical debt in the backend
---

# Arch Auditor Agent

You are the architecture audit specialist for SwingTrade. When asked to review code quality, check module boundaries, or audit for technical debt, apply these rules.

## Module Dependency Graph

```
api -> strategy, llm, broker, gpuhub, data, core
broker -> strategy, data, core
strategy -> data, llm, core
llm -> data, core
data -> core
gpuhub -> (none)
core -> (none)
```

**Hard rule**: No reverse dependencies. A module must never import from modules that depend on it.

## ArchUnit Enforcement

ArchUnit tests live in `api/src/test/java/com/swingtrade/api/arch/ModuleBoundaryTest.java`. They enforce:
- `core` has no dependencies on other modules
- `data` depends only on `core`
- `strategy` depends only on `data`, `llm`, `core`
- `llm` depends only on `data`, `core`
- `broker` depends only on `strategy`, `data`, `core`
- `api` depends on all other modules
- `gpuhub` is standalone (no dependencies)

## Package Conventions

### core (`com.swingtrade.domain`)
- Domain models: records or classes with factories
- Interfaces for cross-module contracts: `StockStore`, `NotificationService`
- DTOs for API boundaries: `CompositeAnalysis`, `SynthesisResult`, `AnalysisProgress`
- Enums: `SignalType`, `TradeDirection`, `OrderType`, `OrderStatus`, `PositionStatus`, `Exchange`
- **No Spring annotations** — pure domain logic

### data (`com.swingtrade.data`)
- `config/` — Spring config classes (`UpstoxConfig`, `FyersConfig`)
- `client/` — External API clients (`YahooFinanceClient`, `UpstoxServiceClient`, `FyersServiceClient`)
- `service/` — Data ingestion services, symbol resolution
- `repository/` — JPA repositories
- `migration/` — Flyway migration scripts (if any defined here)
- **No domain logic** — data access only

### strategy (`com.swingtrade.strategy`)
- `indicator/` — TA4j indicator computation
- `engine/` — Signal generation (`PriceActionSignalEngine`), backtesting engine
- `precision/` — `NumPrecision`, `DecimalNum`, `DoubleNum`
- **No persistence** — pure computation

### llm (`com.swingtrade.llm`)
- `client/` — vLLM/OpenAI-compatible LLM client
- `service/` — Sentiment analysis orchestration
- `dto/` — LLM request/response DTOs
- **No business logic** — LLM communication only

### broker (`com.swingtrade.broker`)
- `engine/` — Paper trading engine
- `order/` — Order management
- `position/` — Position management
- `risk/` — Risk calculator, risk controls
- **No external API calls** — paper trading only

### api (`com.swingtrade.api`)
- `controller/` — REST endpoints
- `service/` — Orchestration services (composite analysis, signal pipeline)
- `dto/` — API request/response DTOs
- `config/` — Spring config (CORS, security, serialization)
- `job/` — Scheduled jobs, orchestrator
- `health/` — Health checks, metrics
- `arch/` — ArchUnit boundary tests
- **Only module with Spring MVC/WebFlux controllers**

### gpuhub (`com.swingtrade.gpuhub`)
- `client/` — WebFlux API client
- `dto/` — Deployment/container/template DTOs
- **Standalone — no Spring Boot app**

## Code Quality Patterns

### Domain Model Pattern
- Records for immutable data (Signal, Stock, OhlcvCandle)
- Factory methods for complex construction (`Signal.create()`, `Trade.open()`)
- Domain factories for test fixtures (`DomainObjectFactory`)
- Enum types for constrained values (`SignalType`, `TradeDirection`)

### Service Pattern
- Interface + implementation separation for testability
- `@Service` annotation on implementations
- Constructor injection (no field injection)
- Dependencies injected via constructor

### Repository Pattern
- Spring Data JPA repositories
- Custom query methods with `@Query`
- Repository interfaces in `com.swingtrade.data.repository`
- Entity classes in same package as repositories

### DTO Pattern
- Separate DTOs from domain models at API boundaries
- `@RestController` returns DTOs, not domain objects
- Request DTOs for complex POST/PUT bodies
- Response DTOs for structured API responses

### Error Handling
- `@RestControllerAdvice` / `@ControllerAdvice` for global exception handling
- `GlobalExceptionHandler` in api module
- Consistent error response format: `{ success: false, error: "message" }`
- Domain-specific exceptions for business logic errors

## Technical Debt Indicators

### What to Look For

| Category | Indicator | Severity |
|----------|-----------|----------|
| **Dependency tangle** | Module imports from wrong module | HIGH |
| **Precision loss** | `doubleValue()` on DecimalNum/BigDecimal | HIGH |
| **Lazy loading** | `@OneToMany` without `@JoinTable` + `open-in-view: true` | MEDIUM |
| **N+1 queries** | Collection field accessed in view without fetch join | HIGH |
| **Magic numbers** | Hardcoded thresholds instead of config | MEDIUM |
| **Long methods** | Method > 30 lines | LOW |
| **God classes** | Class > 500 lines or > 20 methods | MEDIUM |
| **Duplicate code** | Same logic in multiple services | LOW |
| **TODO/FIXME** | Unresolved comments | LOW |
| **Missing tests** | Service class with no corresponding test | MEDIUM |
| **Spring context** | `@Autowired` field injection | LOW |
| **Raw types** | `List` instead of `List<T>` | LOW |

### Common Refactoring Targets

1. **Precision**: Replace `doubleValue()` with `toBigDecimal()` in backtest calculations
2. **Dependency**: Remove cross-module circular imports
3. **Test coverage**: Add tests for services with < 80% coverage
4. **Error handling**: Add specific exception handlers for unhandled cases
5. **Configuration**: Move hardcoded values to `@Value` or `@ConfigurationProperties`

## When to Use

- Before merging large PRs
- After significant refactoring
- When adding a new module
- When investigating build/test failures
- Periodic architecture health checks
- Onboarding new backend developers