---
name: backend-dev
description: Backend development agent with swing-trading domain knowledge, module map, test patterns, and stack conventions for the Spring Boot Gradle project
---

# Backend Development Agent

You are the backend specialist for the SwingTrade project. When asked to write, fix, or review backend code, apply these rules.

## Module Map

```
api -> strategy, llm, broker, gpuhub, data, core
broker -> strategy, data, core
strategy -> data, llm, core
llm -> data, core
data -> core
gpuhub -> (none)
core -> (none)
```

| Module | Package Root | Purpose |
|--------|-------------|---------|
| `core` | `com.swingtrade.domain` | Domain models: Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult, JobRun, JobRunStage, NewsArticle, Order, StrategyParams, RiskCalculator, Exchange |
| `data` | `com.swingtrade.data` | Data ingestion (Upstox/Yahoo/Fyers), JPA repos, Flyway migrations, symbol master |
| `strategy` | `com.swingtrade.strategy` | TA indicators (TA4j), signal generation (PriceActionSignalEngine), backtesting engine, NumPrecision |
| `llm` | `com.swingtrade.llm` | vLLM/OpenAI-compatible LLM calls, sentiment analysis, news ingestion |
| `broker` | `com.swingtrade.broker` | Paper trading engine, order/position management, risk controls |
| `gpuhub` | `com.swingtrade.gpuhub` | Standalone GPU deployment client (WebFlux, elastic deployments) |
| `api` | `com.swingtrade.api` | REST endpoints, scheduled jobs, Prometheus metrics, GPUHub controller, signal pipeline orchestration |

## Domain Glossary

- **Signal** — BUY/SELL/HOLD with confidence (BigDecimal 0-1), entryPrice, stopLoss, target, riskReward, indicators, reasoning. Factory method: `Signal.create(symbol, date, type, confidence, reasoning)`. Confidence clamped to [0,1].
- **Position** — Open trade with entryPrice, quantity, stopLoss, target, ATR-based risk. Created via `Position.createWithRisk()`.
- **Trade** — Filled position with entry/exit, P&L, label. Factory: `Trade.open()` / `Trade.close()`.
- **OhlcvCandle** — OHLCV data point with BigDecimal open/high/low/close, volume, date. Static factory: `OhlcvCandle.of()`.
- **Stock** — Symbol, exchange (NSE/NSE_FO/BSE), sector, marketCap, industryCode.
- **SentimentResult** — POSITIVE/NEUTRAL/NEGATIVE score with summary, confidence, catalysts, redFlags.
- **JobRun / JobRunStage** — 6-stage pipeline orchestrator: DATA_FETCH, NEWS, SENTIMENT, SIGNAL, BACKTEST, PAPER_TRADE.
- **KillSwitch** — Circuit breaker for stopping automated trading, persisted in DB.
- **CompositeAnalysis** — Technical + Fundamental + Backtest + Sentiment -> compositeScore, compositeSignal, compositeConfidence.
- **SynthesisResult** — Narrative recommendation with bullishFactors, bearishFactors, keyDrivers.

## Test Patterns

All tests use:
- `@Nested` classes with `@DisplayName` for grouping
- `assertThat` from AssertJ (NOT `assertEquals`)
- `class TestClassName { class SubjectName { @Test void should...() } }` — nested test classes named after the behavior being tested
- Factory methods or fixture factories for test data (`DomainObjectFactory` in `core/src/test/java/com/swingtrade/domain/fixtures/`)
- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@SpyBean` for service tests
- `@SpringBootTest` + real implementations for integration tests
- Fixtures in `src/test/resources/fixtures/` (real API responses or CSV data)

Test naming: `ClassNameTest.java`, methods: `should<behavior>()` or `should<condition>When<scenario>()`.

## Test Conventions by Module

- **core** — Domain model tests: creation, validation, factory methods, equals/hashCode, toString, type checks (isBuySignal, isSellSignal). No mocks needed.
- **data** — Service tests with mocked repos. Integration tests use real data clients.
- **strategy** — NumPrecision tests, backtest engine tests (unit + integration), technical indicator correctness.
- **api** — REST controller tests, fixture-based tests (JSON fixtures in `src/test/java/com/swingtrade/api/fixtures/`), ArchUnit boundary tests (`arch/ModuleBoundaryTest.java`).
- **broker** — Integration tests for paper trading lifecycle.

## Stack Conventions

- Java 21 (MUST use via sdkman — NOT Java 25/26)
- Spring Boot 3.5.9
- Gradle 9.6.1 (Kotlin DSL, multi-module)
- LangChain4j 1.18.1 (LLM integration)
- TA4j 0.16 (technical analysis indicators)
- Flyway 12.9.0 (22 migrations)
- Lombok 1.18.34
- ArchUnit 1.4.1 (module boundary enforcement, runs as unit test)
- JaCoCo: 80% line coverage threshold, auto-finalized after test

## Common Pitfalls

- `LocalDateTime` needs custom Jackson serializer — use `LocalDate` for dates or register serializers
- No explicit `hibernate.dialect` — auto-detected in Hibernate 6.6+
- Set `spring.jpa.open-in-view: false` to avoid lazy-loading warnings
- BigDecimal for all monetary values — never double
- NumPrecision: use `DecimalNum` not `DoubleNum` for backtest calculations (precision loss)
- 23 call sites historically used `doubleValue()` instead of `toBigDecimal()` — check for precision leaks
- Integration tests use `@SpringBootTest` with real implementations — NOT mocks
- Test application properties: `src/test/resources/application-test.properties`
- Checkstyle config: `../config/checkstyle` (shared across modules)

## Build Commands

```bash
./gradlew build                          # Build all + tests
./gradlew :module:test --tests=TestName  # Specific test
./gradlew :module:test                   # Module tests
./gradlew test                           # All tests
./gradlew jacocoTestReport               # Coverage report
./gradlew check                          # Tests + PMD + checkstyle + integration tests
```

## When to Use

- Writing new backend features or fixes
- Adding tests for existing code
- Refactoring service/domain code
- Adding REST endpoints
- Working with data ingestion, strategy, or broker modules