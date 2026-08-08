# TDD Plan: Audit Bug Fixes

Fix 7 confirmed bugs from the architecture audit (docs/analysis/architecture-audit-2026-08-07.md).

---

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: TradeRequest.isValid() | [ ] PENDING | — | — |
| 2: Null direction guards | [ ] PENDING | — | — |
| 3: PositionEntity NPE | [ ] PENDING | — | — |
| 4: @Transactional on closePosition | [ ] PENDING | — | — |
| 5: Capital config mismatch | [ ] PENDING | — | — |
| 6: Trade.close() PnL + fees | [ ] PENDING | — | — |
| 7: DailyLossCircuitBreaker persistence | [ ] PENDING | — | — |

---

## Phase 1: TradeRequest.isValid() operator precedence fix

### RED — Write failing test

**File**: `backend/api/src/test/java/com/swingtrade/api/dto/TradeRequestTest.java` (create new)

**Test 1**: `shouldRejectStopOrderWithNullStopPrice`
- Create TradeRequest with `OrderType.STOP`, `stopPrice = null`
- Assert `isValid()` returns `false`
- Currently fails: returns `true` due to operator precedence bug

**Test 2**: `shouldRejectStopLimitOrderWithNullStopPrice`
- Create TradeRequest with `OrderType.STOP_LIMIT`, `stopPrice = null`
- Assert `isValid()` returns `false`
- Currently fails: returns `true`

**Test 3**: `shouldRejectStopOrderWithNullLimitPrice`
- Create TradeRequest with `OrderType.STOP`, `limitPrice = null`, `stopPrice = 100`
- Assert `isValid()` returns `true` (stopPrice is present, limitPrice not required for STOP)
- Currently passes (no regression)

**Test 4**: `shouldRejectStopLimitOrderWithNullLimitPrice`
- Create TradeRequest with `OrderType.STOP_LIMIT`, `stopPrice = 100`, `limitPrice = null`
- Assert `isValid()` returns `false`
- Currently fails: returns `true`

### GREEN — Fix the code

**File**: `backend/api/src/main/java/com/swingtrade/api/dto/TradeRequest.java` (line 160)

Replace:
```java
return orderType != OrderType.STOP && orderType != OrderType.STOP_LIMIT || stopPrice != null;
```

With:
```java
if ((orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT) && stopPrice == null) {
    return false;
}
if (orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT) {
    return limitPrice != null;
}
return true;
```

### Verification
```bash
./gradlew :api:test --tests=TradeRequestTest  # all pass
```

---

## Phase 2: Null direction guards in 3 places

### RED — Write failing tests

**File**: `backend/broker/src/test/java/com/swingtrade/broker/service/PaperTradingServiceImplTest.java`

**Test 1**: `shouldThrowOnNullDirection`
- Mock OrderManager, PaperTradingEngine, PaperTradingStateService
- Create Order with `direction = null`
- Call `placeOrder(order)`
- Assert `IllegalArgumentException` thrown with message containing "direction"
- Currently fails: falls through to SELL order

**File**: `backend/broker/src/test/java/com/swingtrade/broker/factory/LiveTradingServiceTest.java`

**Test 2**: `shouldThrowOnNullDirection`
- Create Order with `direction = null`
- Call `placeOrder(order)`
- Assert `IllegalArgumentException` thrown
- Currently fails: null propagates to broker client

**File**: `backend/api/src/test/java/com/swingtrade/api/service/PositionServiceTest.java`

**Test 3**: `shouldThrowOnNullDirectionInCreatePosition`
- Mock OrderManager, PaperTradingEngine
- Create TradeRequest with `direction = null`
- Call `createPosition(request)`
- Assert `IllegalArgumentException` thrown
- Currently fails: NullPointerException from switch expression

### GREEN — Fix the code

**File 1**: `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingServiceImpl.java` (lines 44-50)
```java
// Before the if-else, add:
if (order.getDirection() == null) {
    throw new IllegalArgumentException("Order direction cannot be null");
}
// Change else to else if:
} else if (order.getDirection() == TradeDirection.SHORT) {
```

**File 2**: `backend/broker/src/main/java/com/swingtrade/broker/factory/LiveTradingService.java` (line 47-97)
```java
// Before direction usage, add:
if (order.getDirection() == null) {
    throw new IllegalArgumentException("Order direction cannot be null");
}
```

**File 3**: `backend/api/src/main/java/com/swingtrade/api/service/PositionService.java` (lines 271-276)
```java
Order order = switch (request.getDirection()) {
    case LONG -> orderManager.createBuyOrder(
        request.getSymbol(), request.getQuantity(), entryPrice);
    case SHORT -> orderManager.createSellOrder(
        request.getSymbol(), request.getQuantity(), entryPrice);
    default -> throw new IllegalArgumentException("Order direction must be LONG or SHORT");
};
```

### Verification
```bash
./gradlew :broker:test --tests=PaperTradingServiceImplTest
./gradlew :broker:test --tests=LiveTradingServiceTest
./gradlew :api:test --tests=PositionServiceTest
```

---

## Phase 3: PositionEntity.toDomain() NPE on null status

### RED — Write failing test

**File**: `backend/data/src/test/java/com/swingtrade/data/entity/PositionEntityTest.java` (create new)

**Test 1**: `shouldHandleNullStatusByDefaultingToOpen`
- Create PositionEntity with `status = null`, valid symbol/price/quantity
- Call `toDomain()`
- Assert returned Position has `status == PositionStatus.OPEN`
- Currently fails: throws `IllegalArgumentException("No enum constant com.swingtrade.domain.PositionStatus.null")`

**Test 2**: `shouldConvertValidStatusCorrectly`
- Create PositionEntity with `status = "CLOSED"`
- Call `toDomain()`
- Assert `position.status() == PositionStatus.CLOSED`
- Currently passes (no regression)

### GREEN — Fix the code

**File**: `backend/data/src/main/java/com/swingtrade/data/entity/PositionEntity.java` (line 165)

Replace:
```java
PositionStatus.valueOf(status),
```

With:
```java
status != null ? PositionStatus.valueOf(status) : PositionStatus.OPEN,
```

### Verification
```bash
./gradlew :data:test --tests=PositionEntityTest
```

---

## Phase 4: @Transactional on closePosition methods

### RED — Write failing test

**File**: `backend/api/src/test/java/com/swingtrade/api/service/PositionServiceTest.java`

**Test 1**: `shouldRollbackOnDbFailureDuringClosePosition` (integration test)
- Use `@SpringBootTest` + H2
- Mock PaperTradingEngine to throw RuntimeException after closePosition is called
- Call `positionService.closePosition(symbol, reason)`
- Assert position is NOT persisted to DB
- Currently fails: position IS persisted because no @Transactional

### GREEN — Fix the code

**Files** (add `@Transactional` import + annotation):

1. `backend/api/src/main/java/com/swingtrade/api/service/PositionService.java` — line 203
2. `backend/broker/src/main/java/com/swingtrade/broker/manager/PositionManager.java` — lines 363, 375, 386
3. `backend/broker/src/main/java/com/swingtrade/broker/engine/PaperTradingEngine.java` — lines 435, 461
4. `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java` — line 166

### Verification
```bash
./gradlew :api:test --tests=PositionServiceTest
./gradlew :broker:test --tests=PaperTradingEngineTest
./gradlew :broker:test --tests=PaperTradingStateServiceTest
```

---

## Phase 5: Capital config mismatch fix

### RED — Write failing test

**File**: `backend/broker/src/test/java/com/swingtrade/broker/risk/CapitalTrackerTest.java`

**Test 1**: `shouldUsePaperTradingPropertiesInitialBalance`
- Create CapitalTracker with `PaperTradingProperties.initialBalance` (1,000,000)
- Assert `getInitialCapital() == 1,000,000`
- Currently fails: uses hardcoded 50,000

**Test 2**: `shouldHaveConsistentMaxCapitalPerPosition`
- Create CapitalTracker with `PaperTradingProperties.maxCapitalPerPosition` (200,000)
- Assert `getMaxCapitalPerPosition() == 200,000`
- Currently fails: uses 10,000 from BrokerProperties

### GREEN — Fix the code

**File 1**: `backend/broker/src/main/java/com/swingtrade/broker/config/PaperTradingProperties.java` (line 18)
```java
// Change from:
private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(20);
// To:
private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(200000);
```

**File 2**: `backend/broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java` (lines 37-39)
```java
// Change from:
@Autowired
public CapitalTracker(BrokerProperties props) {
    this(DEFAULT_INITIAL_CAPITAL, props.getMaxConcurrentPositions(), props.getMaxCapitalPerPosition());
}
// To:
@Autowired
public CapitalTracker(PaperTradingProperties props) {
    this(props.getInitialBalance(), props.getMaxConcurrentPositions(), props.getMaxCapitalPerPosition());
}
```

**File 3**: `backend/broker/src/main/java/com/swingtrade/broker/config/BrokerProperties.java` (line 18)
```java
// Change from:
private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(10000);
// To:
private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(200000);
```

### Verification
```bash
./gradlew :broker:test --tests=CapitalTrackerTest
```

---

## Phase 6: Trade.close() PnL for SHORT + fees

### RED — Write failing test

**File**: `backend/core/src/test/java/com/swingtrade/domain/TradeTest.java`

**Test 1**: `shouldCalculateShortPnLCorrectly`
- Create Trade with `TradeDirection.SHORT` (requires adding direction field to Trade record)
- Entry: 100, Exit: 90, Quantity: 100
- Expected PnL: (100 - 90) * 100 = 1000 (profit for SHORT)
- Assert `closedTrade.totalPnL() == 1000`
- Currently fails: returns -1000 (LONG formula)

**Test 2**: `shouldDeductFeesFromPnL`
- Create Trade with fees = 10
- Entry: 100, Exit: 110, Quantity: 100
- Expected PnL: (110 - 100) * 100 - 10 = 990
- Assert `closedTrade.totalPnL() == 990`
- Currently fails: returns 1000 (fees not deducted)

**Test 3**: `shouldCalculateShortPnLWithFees`
- Create SHORT Trade with fees = 10
- Entry: 100, Exit: 90, Quantity: 100
- Expected PnL: (100 - 90) * 100 - 10 = 990
- Assert `closedTrade.totalPnL() == 990`

### GREEN — Fix the code

**File 1**: `backend/core/src/main/java/com/swingtrade/domain/Trade.java`

Step A: Add `TradeDirection direction` field to Trade record (after `quantity`, before `totalPnL`)
Step B: Update `Trade.open()` to accept direction parameter (default `TradeDirection.LONG` for backward compat)
Step C: Update `Trade.close()` PnL calculation:
```java
BigDecimal grossPnL;
if (trade.direction() == TradeDirection.SHORT) {
    grossPnL = trade.entryPrice().subtract(exitPrice).multiply(BigDecimal.valueOf(trade.quantity()));
} else {
    grossPnL = exitPrice.subtract(trade.entryPrice()).multiply(BigDecimal.valueOf(trade.quantity()));
}
BigDecimal totalPnL = grossPnL.subtract(trade.fees());
```
Step D: Update all callers of `Trade.open()` to pass direction (most default to LONG)

### Verification
```bash
./gradlew :core:test --tests=TradeTest
```

---

## Phase 7: DailyLossCircuitBreaker persistence

### RED — Write failing test

**File**: `backend/broker/src/integrationTest/java/com/swingtrade/broker/risk/DailyLossCircuitBreakerPersistenceTest.java` (create new)

**Test 1**: `shouldRestoreCircuitStateOnRestart`
- Use `@SpringBootTest` + H2
- Open circuit manually
- Close app, restart
- Assert `isTradingAllowed() == false` (circuit state persisted)
- Currently fails: circuit resets to closed on restart

**Test 2**: `shouldPersistDailyPnLAcrossRestarts`
- Set daily PnL to -50,000
- Close app, restart
- Assert `getCurrentDailyPnL() == -50,000`
- Currently fails: daily PnL resets to 0

### GREEN — Fix the code

**Step A**: Create Flyway migration
**File**: `backend/data/src/main/resources/db/migration/V4__daily_loss_circuit_breaker_state.sql`
```sql
CREATE TABLE daily_loss_circuit_breaker_state (
    id              BIGSERIAL PRIMARY KEY,
    circuit_open    BOOLEAN NOT NULL DEFAULT FALSE,
    circuit_opened_at TIMESTAMP,
    loss_at_open    NUMERIC(15,2),
    last_reset_date DATE NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE daily_loss_circuit_breaker_state
    ADD CONSTRAINT uq_single_row CHECK (id = 1);
```

**Step B**: Create entity + repository
**File**: `backend/data/src/main/java/com/swingtrade/data/entity/DailyLossCircuitBreakerStateEntity.java`
**File**: `backend/data/src/main/java/com/swingtrade/data/repository/DailyLossCircuitBreakerStateRepository.java`

**Step C**: Modify DailyLossCircuitBreaker
- Inject `DailyLossCircuitBreakerStateRepository`
- On construction: load state from DB, restore fields
- On `openCircuit()`: persist state
- On `closeCircuit()`: persist cleared state
- On `updateWithCurrentPositions()`: persist daily PnL

### Verification
```bash
./gradlew :broker:integrationTest --tests=DailyLossCircuitBreakerPersistenceTest
```

---

## Files Summary

| Action | File | Type |
|--------|------|------|
| Create | `api/src/test/java/.../TradeRequestTest.java` | Unit |
| Modify | `api/src/main/java/.../TradeRequest.java` (1 line) | Source |
| Modify | `broker/src/main/java/.../PaperTradingServiceImpl.java` (2 lines) | Source |
| Modify | `broker/src/main/java/.../LiveTradingService.java` (2 lines) | Source |
| Modify | `api/src/main/java/.../PositionService.java` (1 line) | Source |
| Create | `data/src/test/java/.../PositionEntityTest.java` | Unit |
| Modify | `data/src/main/java/.../PositionEntity.java` (1 line) | Source |
| Modify | `api/src/main/java/.../PositionService.java` (1 annotation) | Source |
| Modify | `broker/src/main/java/.../PositionManager.java` (3 annotations) | Source |
| Modify | `broker/src/main/java/.../PaperTradingEngine.java` (2 annotations) | Source |
| Modify | `broker/src/main/java/.../PaperTradingStateService.java` (1 annotation) | Source |
| Modify | `broker/src/main/java/.../PaperTradingProperties.java` (1 value) | Source |
| Modify | `broker/src/main/java/.../BrokerProperties.java` (1 value) | Source |
| Modify | `broker/src/main/java/.../CapitalTracker.java` (1 injection) | Source |
| Modify | `core/src/main/java/.../Trade.java` (direction field + PnL) | Source |
| Expand | `core/src/test/java/.../TradeTest.java` (3 new tests) | Unit |
| Create | `data/src/main/resources/db/migration/V4__daily_loss_circuit_breaker_state.sql` | Migration |
| Create | `data/src/main/java/.../DailyLossCircuitBreakerStateEntity.java` | Entity |
| Create | `data/src/main/java/.../DailyLossCircuitBreakerStateRepository.java` | Repo |
| Modify | `broker/src/main/java/.../DailyLossCircuitBreaker.java` (persist logic) | Source |
| Create | `broker/src/integrationTest/java/.../DailyLossCircuitBreakerPersistenceTest.java` | Integration |

---

## Verification

```bash
cd backend
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Run per phase
./gradlew :api:test --tests=TradeRequestTest
./gradlew :broker:test --tests=PaperTradingServiceImplTest
./gradlew :data:test --tests=PositionEntityTest
./gradlew :api:test --tests=PositionServiceTest
./gradlew :broker:test --tests=CapitalTrackerTest
./gradlew :core:test --tests=TradeTest
./gradlew :broker:integrationTest --tests=DailyLossCircuitBreakerPersistenceTest

# Full verification
./gradlew test
./gradlew check
./gradlew build
./gradlew jacocoTestCoverageVerification
```
