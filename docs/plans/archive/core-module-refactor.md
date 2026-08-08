# Core Module Refactor — Dependency Cleanup Plan

## Goal

Fix the module dependency tangle by adding service interfaces to `core`, then rewiring all modules to depend on interfaces instead of the `data` module directly. Result: clean DAG, no circular deps, swappable data backends.

## Current State (Broken)

```
api ──┬── core
      ├── data  (20 files import data.repository directly)
      ├── strategy (6 files)
      ├── llm     (5 files)
      └── broker  (9 files)

strategy ──┬── core
           ├── data  (BacktestEngine, PriceActionSignalEngine import OhlcvCandleRepository)
           └── llm

broker ──┬── core
         ├── strategy  (should not depend on strategy)
         └── data

llm ──┬── core
      └── data  (SentimentService, PdfExtractionService import entities/repos)

data ── core
core ── (clean, standalone)
```

## Target State (Clean)

```
api ──> core (interfaces) ──┐
        │                    │
        ├── data (implements)─┤
        ├── strategy          │
        ├── broker            │
        └── llm               │
                               │
strategy ──> core ──> data     │
llm ──> core ──> data ────────┘

broker ──> core ──> data
broker ──> broker (own paper trading repos, no data, no strategy)

data ──> core
core ── (clean, standalone)
```

## Phase 0: Move broker/model out of core

**Why**: `core` holds `broker/model/` (Order, Portfolio, Position, etc.) which is broker-specific. It doesn't belong in the domain module.

**Steps**:
1. Move `backend/core/src/main/java/com/swingtrade/broker/model/` → `backend/broker/src/main/java/com/swingtrade/broker/model/`
2. Update package declaration: `com.swingtrade.broker.model` (no change needed — package stays the same)
3. Update all imports that reference `com.swingtrade.broker.model` from `core` to `broker`:
   - `backend/api/` — ~9 files import broker.model
   - `backend/broker/` — internal references
   - `backend/strategy/` — if any
4. Update `core/pom.xml` — remove any dependency on broker (there shouldn't be one, but verify)
5. Verify `broker/pom.xml` already has the broker module's own dependencies for the model

**Verification**: `mvn clean compile` passes. No broken imports.

## Phase 1: Define *Store interfaces in core

**Location**: `backend/core/src/main/java/com/swingtrade/domain/store/`

**Interfaces to create** (~8 total):

### CandleStore
```java
public interface CandleStore {
    Optional<OhlcvCandle> findBySymbolAndDate(String symbol, LocalDate date);
    List<OhlcvCandle> findBySymbol(String symbol);
    List<OhlcvCandle> findBySymbolAndDateRange(String symbol, LocalDate from, LocalDate to);
    List<OhlcvCandle> findTopBySymbolOrderByDateDesc(String symbol, int limit);
    Optional<OhlcvCandle> findLatestBySymbol(String symbol);
    void save(OhlcvCandle candle);
    boolean existsBySymbolAndDate(String symbol, LocalDate date);
}
```

### StockStore
```java
public interface StockStore {
    List<Stock> findAllActive();
    Optional<Stock> findBySymbol(String symbol);
    void save(Stock stock);
}
```

### SignalStore
```java
public interface SignalStore {
    List<Signal> findAll();
    List<Signal> findBySymbol(String symbol);
    List<Signal> findByDate(LocalDate date);
    List<Signal> findByType(Signal.SignalType type);
    List<Signal> findUnprocessed();
    Signal save(Signal signal);
    void markProcessed(Long signalId);
}
```

### PositionStore
```java
public interface PositionStore {
    List<Position> findAllOpen();
    Optional<Position> findById(Long id);
    Optional<Position> findBySymbol(String symbol);
    List<Position> findByStatus(Position.PositionStatus status);
    Position save(Position position);
}
```

### TradeStore
```java
public interface TradeStore {
    List<Trade> findBySymbol(String symbol);
    List<Trade> findByStatus(Trade.TradeStatus status);
    List<Trade> findAll();
    Trade save(Trade trade);
}
```

### SentimentStore
```java
public interface SentimentStore {
    List<SentimentResult> findBySymbol(String symbol);
    Optional<SentimentResult> findBySymbolAndDate(String symbol, LocalDate date);
    SentimentResult save(SentimentResult result);
}
```

### WatchlistStore
```java
public interface WatchlistStore {
    List<Stock> getWatchlist();
    void addToWatchlist(String symbol);
    void removeFromWatchlist(String symbol);
}
```

### AppSettingsStore
```java
public interface AppSettingsStore {
    Optional<String> get(String key);
    void set(String key, String value);
}
```

**Rules for interfaces**:
- No Spring annotations on interfaces
- No JPA types, no repository types
- Domain records as parameter/return types only
- Methods mirror the most common query patterns (not every possible query)
- If a module needs a query not covered by an interface, add it to the interface (the "two-module rule")

## Phase 2: Wire data module to implement interfaces

**Steps**:
1. Add `core` dependency to `data/pom.xml` (already present — verify)
2. Create `data` module implementations of each `*Store` interface:
   - `CandleStoreImpl` — wraps `OhlcvCandleRepository`
   - `StockStoreImpl` — wraps `StockRepository`
   - `SignalStoreImpl` — wraps `SignalRepository`
   - `PositionStoreImpl` — wraps `PositionRepository`
   - `TradeStoreImpl` — wraps `TradeRepository`
   - `SentimentStoreImpl` — wraps `SentimentResultRepository`
   - `WatchlistStoreImpl` — wraps `WatchlistRepository`
   - `AppSettingsStoreImpl` — wraps `AppSettingRepository`
3. Each implementation is a `@Service` annotated class
4. Each implementation converts between Entity ↔ Domain record using existing `fromDomain()`/`toDomain()` methods
5. Register each as a Spring bean (already done via `@Service`)

**Verification**: `data` module compiles. Each `*Store` implementation is a valid Spring bean.

## Phase 3: Rewire api module

**Files affected** (~20 files):

| File | Current import | New import |
|------|---------------|------------|
| SignalEngine.java | `data.repository.OhlcvCandleRepository` | `domain.store.CandleStore` |
| SignalEngine.java | `data.repository.SignalRepository` | `domain.store.SignalStore` |
| SignalController.java | `data.repository.SentimentResultRepository` | `domain.store.SentimentStore` |
| SentimentApiController.java | `data.repository.PdfExtractionRepository` | (keep — PdfExtraction is data-module-specific) |
| SentimentApiController.java | `data.repository.SentimentResultRepository` | `domain.store.SentimentStore` |
| SignalService.java | `data.repository.SentimentResultRepository` | `domain.store.SentimentStore` |
| SignalService.java | `data.repository.SignalRepository` | `domain.store.SignalStore` |
| ScanService.java | `data.repository.SignalRepository` | `domain.store.SignalStore` |
| ScanService.java | `data.repository.StockRepository` | `domain.store.StockStore` |
| SignalExecutionJob.java | `data.repository.OhlcvCandleRepository` | `domain.store.CandleStore` |
| SignalExecutionJob.java | `data.repository.SignalRepository` | `domain.store.SignalStore` |
| SentimentEvaluationJob.java | `data.repository.OhlcvCandleRepository` | `domain.store.CandleStore` |
| SentimentEvaluationJob.java | `data.repository.SentimentAccuracyRepository` | (keep — specific to data module) |
| SentimentEvaluationJob.java | `data.repository.SentimentResultRepository` | `domain.store.SentimentStore` |
| CompositeAnalysisService.java | `data.repository.OhlcvCandleRepository` | `domain.store.CandleStore` |
| FundamentalScorer.java | `data.repository.OhlcvCandleRepository` | `domain.store.CandleStore` |

**Steps**:
1. In `api/pom.xml`, remove `core` dependency if not present (should already be there)
2. In `api/pom.xml`, remove `data` dependency
3. Replace all `data.repository.*` imports with `domain.store.*` interface types
4. Replace constructor injection of repositories with injection of `*Store` interfaces
5. Update method calls: `repository.findByX()` → `candleStore.findByX()` (may need minor signature adjustments)
6. Keep `data.repository` imports for data-module-specific queries (PdfExtractionRepository, SentimentAccuracyRepository) — these are only used by data-module-specific controllers

**Verification**: `api` module compiles. No `data.repository` imports remain (except data-module-specific ones).

## Phase 4: Rewire strategy module

**Files affected** (~2 files):

| File | Current import | New import |
|------|---------------|------------|
| BacktestEngine.java | `data.repository.OhlcvCandleRepository` | `domain.store.CandleStore` |
| BacktestEngine.java | `data.repository.WatchlistRepository` | `domain.store.WatchlistStore` |
| PriceActionSignalEngine.java | `data.repository.OhlcvCandleRepository` | `domain.store.CandleStore` |

**Steps**:
1. In `strategy/pom.xml`, remove `data` dependency
2. Replace `data.repository.*` imports with `domain.store.*` interface types
3. Update constructor injection
4. Update method calls if signatures differ

**Verification**: `strategy` module compiles. No `data.repository` imports remain.

## Phase 5: Rewire llm module

**Files affected** (~3 files):

| File | Current import | New import |
|------|---------------|------------|
| SentimentService.java | `data.entity.SentimentResultEntity` | (keep — entity used for persistence) |
| SentimentService.java | `data.entity.StockEntity` | (keep — entity used for persistence) |
| SentimentService.java | `data.repository.SentimentResultRepository` | `domain.store.SentimentStore` |
| SentimentService.java | `data.repository.StockRepository` | `domain.store.StockStore` |
| PdfExtractionService.java | `data.entity.PdfExtractionEntity` | (keep — entity used for persistence) |
| PdfExtractionService.java | `data.repository.PdfExtractionRepository` | (keep — data-module-specific) |
| VLLMClient.java | `data.service.AppSettingsService` | `domain.store.AppSettingsStore` |

**Steps**:
1. In `llm/pom.xml`, remove `data` dependency
2. Replace repository injections with `*Store` interface injections
3. Keep entity imports where the LLM module directly creates/persists entities (SentimentService, PdfExtractionService) — these are internal to the data module but the LLM module needs to construct entities
4. Replace `AppSettingsService` with `AppSettingsStore` in VLLMClient

**Verification**: `llm` module compiles.

## Phase 6: Verify and test

1. `mvn clean compile` — all modules
2. `mvn test` — all modules
3. `mvn dependency:analyze` — verify no unused dependencies
4. `mvn dependency:tree` — verify clean dependency graph
5. Run the application end-to-end

## What stays in data module (unchanged)

- `DataIngestionService` — ingestion logic (calls market data clients, uses CandleStore for persistence)
- `MarketDataClient` / `UpstoxServiceClient` / `FyersServiceClient` — external API clients
- `CandleValidator`, `CandleData`, `QuoteData` — data transfer types
- `WatchlistService` — watchlist business logic (uses WatchlistStore)
- `SentimentAccuracyService` — accuracy tracking
- `FyersAuthService`, `UpstoxAuthService` — auth clients
- `Application.java` — Spring Boot entry point
- All entity classes — JPA persistence types
- All repositories — Spring Data JPA interfaces
- Flyway migrations

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Interface signatures don't match existing repo methods | Audit each repo's most-used methods before writing interfaces |
| Entity↔domain conversion overhead | Reuse existing `fromDomain()`/`toDomain()` converters |
| Broker module still has its own repos + data module repos | Broker uses core interfaces; paper trading repos stay in broker (separate concern) |
| Circular dependency if core depends on data | Core must NEVER depend on data. Core only has domain records + interfaces. |
| Breaking changes during refactoring | One module at a time. Compile after each phase. |

## Order of Operations

Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6

Each phase must compile before moving to the next. Use `mvn clean compile -pl <module> -am` to test incrementally.