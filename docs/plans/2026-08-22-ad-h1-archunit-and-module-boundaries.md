# Plan: Re-enable ArchUnit + Fix Module Boundary Violations

## What's Already Done

- Research completed: no cross-module circular dependencies exist (the audit claim was false — it confused intra-module packages with modules)
- Core module already defines 10 `*Store` interfaces in `com.swingtrade.domain.store`
- Core module already has domain models, order/position types, risk calculator
- Build dependency graph is a clean DAG: core ← data/llm/strategy ← broker ← api

## What Needs to Be Done

### Step 1: Re-enable ArchUnit circular dependency check

**File**: `backend/api/src/test/java/com/swingtrade/api/arch/ModuleBoundaryTest.java`

Replace the empty `noCircularDependencies()` test body with an actual ArchUnit rule:

```java
@Test
void noCircularDependencies() {
    slices()
        .matching("com.swingtrade.[[fin*]]")
        .check(CLASSES, slice -> slice.shouldNotCircularlyDependOn(slice));
}
```

This validates the build-level DAG using ArchUnit's slice-based dependency checker. It will pass since the graph is acyclic.

Also add a layer rule to enforce that api cannot import concrete broker classes:

```java
@Test
void apiShouldNotImportConcreteBrokerClasses() {
    ArchRule rule = classes()
        .that().resideInAnyPackage("..api..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "..core..",
            "..api..",
            "..broker.service..",  // interfaces only
            "..broker.config..",
            "..broker.risk..",
            "..data..",
            "..strategy..",
            "..llm..",
            "..gpuhub.."
        )
        .andShould().not().dependOnClassesThat().haveSimpleNameStartingWith("PaperTrading");
    rule.check(CLASSES);
}
```

**Exact changes to `ModuleBoundaryTest.java`:**
- Add `import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;`
- Add `import com.tngtech.archunit.lang.ArchRule;`
- Replace the empty test body with the two rules above
- Remove the stale comment about disabled cycles

### Step 2: Extract `TradingService` interface to core

**New file**: `backend/core/src/main/java/com/swingtrade/domain/service/TradingService.java`

```java
package com.swingtrade.domain.service;

import com.swingtrade.domain.Order;
import com.swingtrade.domain.Position;
import java.math.BigDecimal;
import java.util.List;

public interface TradingService {
    List<Position> getOpenPositions();
    Position findOpenPositionBySymbol(String symbol);
    List<Position> getClosedPositions();
    Position closePosition(Long positionId, BigDecimal exitPrice, String reason);
    Position createPositionFromOrder(Order order);
    Order executePendingOrder(String orderId, BigDecimal executionPrice);
    BigDecimal getTotalUnrealizedPnL();
    BigDecimal getTotalRealizedPnL();
    BigDecimal getCurrentCash();
    BigDecimal getInitialCapital();
    int getOpenPositionCount();
    boolean canOpenPosition(BigDecimal entryPrice, int quantity);
    List<Order> getPendingOrders();
}
```

**Implementation**: In `PaperTradingEngine`, implement `TradingService`. The interface methods map directly to existing methods (no logic change needed, just the interface declaration).

### Step 3: Extract `OrderService` interface to core

**New file**: `backend/core/src/main/java/com/swingtrade/domain/service/OrderService.java`

```java
package com.swingtrade.domain.service;

import com.swingtrade.domain.Order;
import java.util.List;

public interface OrderService {
    Order createBuyOrder(String symbol, int quantity, BigDecimal price);
    Order createSellOrder(String symbol, int quantity, BigDecimal price);
    boolean cancelOrder(String orderId);
    List<Order> getPendingOrders();
}
```

**Implementation**: `OrderManager` already has these methods. Create `OrderServiceImpl` implementing `OrderService`, delegate to `OrderManager`. Or have `OrderManager` implement `OrderService` directly.

### Step 4: Fix PositionService — route through core interfaces

**File**: `backend/api/src/main/java/com/swingtrade/api/service/PositionService.java`

Replace constructor dependencies:
- Remove `PaperTradingEngine` → inject `TradingService` instead
- Remove `OrderManager` → inject `OrderService` instead
- Remove `PositionManager` → remove (no longer needed)
- Keep `PositionRepository` for now (used in `closePosition` for entity-level save) — but route the engine operations through `TradingService`

**Specific changes in `PositionService`:**
- Line 48: `PaperTradingEngine paperTradingEngine` → `TradingService tradingService`
- Line 49: `OrderManager orderManager` → `OrderService orderService`
- Line 50: Remove `PositionManager positionManager`
- Line 69: `paperTradingEngine.getOpenPositions()` → `tradingService.getOpenPositions()`
- Line 89: `paperTradingEngine.findOpenPositionBySymbol(symbol)` → `tradingService.findOpenPositionBySymbol(symbol)`
- Line 165: `paperTradingEngine.getTotalUnrealizedPnL()` → `tradingService.getTotalUnrealizedPnL()`
- Line 166: `paperTradingEngine.getTotalRealizedPnL()` → `tradingService.getTotalRealizedPnL()`
- Line 223-225: engine close → `tradingService.closePosition(...)`
- Line 274-279: order creation → `orderService.createBuyOrder(...)` / `orderService.createSellOrder(...)`
- Line 330: `paperTradingEngine.getOpenPositions()` → `tradingService.getOpenPositions()`
- Line 340: `paperTradingEngine.getCurrentCash()` → `tradingService.getCurrentCash()`
- Line 382-384: Remove `getPositionManager()` method

### Step 5: Fix other api services importing concrete broker classes

**Files to update:**

| File | Import | Fix |
|------|--------|-----|
| `api/service/JobOrchestratorService.java` | `PaperTradingEngine` | Inject `TradingService` |
| `api/service/SignalFilterService.java` | `PaperTradingEngine` | Inject `TradingService` |
| `api/service/PerformanceService.java` | `PaperTradingEngine` | Inject `TradingService` |
| `api/scheduler/WeeklySectorDigestScheduler.java` | `DiscordNotificationService` | Keep (this is a notification service, not a core domain service) |
| `api/controller/AdminController.java` | `KillSwitchService` | Extract `KillSwitchService` as a core interface or keep as api-level concern |

### Step 6: Defer gpuhub module (already commented out)

**File**: `backend/api/build.gradle.kts` line 22-23, `backend/api/src/main/java/com/swingtrade/api/controller/GpuHubController.java`

Both the build dependency and the entire `GpuHubController.java` file are already commented out. The TODO says "restore when GpuHubDeploymentService component scan is fixed." Do NOT uncomment — leave deferred. Remove from the plan scope entirely.

### Step 7: Remove unused build dependencies

| File | Remove | Reason |
|------|--------|--------|
| `backend/strategy/build.gradle.kts` | `implementation(project(":llm"))` | No source imports from llm found |
| `backend/broker/build.gradle.kts` | `implementation(project(":strategy"))` | No source imports from strategy found |

### Step 8: Update audit doc

**File**: `docs/analysis/architecture-audit-2026-08-20.md`

- Mark AD-H1 as `✅ Done` — ArchUnit re-enabled with actual rules, module boundary violations eliminated
- Add the real issue as a new finding: "api module imports concrete broker classes and JPA entities/repos instead of core interfaces" — mark as P2

## Verification

1. `cd backend && ./gradlew :api:test` — ArchUnit test runs and passes
2. `cd backend && ./gradlew build` — all modules compile and tests pass
3. `cd backend && ./gradlew checkstyleMain` — no checkstyle violations
4. Verify `PositionService` compiles with `TradingService` and `OrderService` interfaces
5. Verify no api file imports `PaperTradingEngine`, `OrderManager`, or `PositionManager` directly

## Risk Assessment

- **Low risk**: `TradingService` interface is a thin wrapper over existing `PaperTradingEngine` methods. No behavior changes.
- **Low risk**: ArchUnit rules are additive — they validate the existing DAG, they don't change code.
- **Medium risk**: `PositionService` constructor changes require updating the Spring bean wiring. Since it uses constructor injection, Spring will fail fast if wiring is wrong.
- **Medium risk**: `PerformanceService` and `JobOrchestratorService` also use `PaperTradingEngine` — must update all at once.

## Estimated Effort

- Step 1 (ArchUnit): 30 min
- Step 2-3 (core interfaces): 1 hour
- Step 4-5 (api service updates): 2 hours
- Step 6-7 (build cleanup): 15 min
- Step 8 (doc update): 10 min
- Verification: 30 min
- **Total: ~4 hours**