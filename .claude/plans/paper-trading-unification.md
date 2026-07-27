# Paper Trading System Unification — Implementation Plan

## Problem Statement

The paper trading system has two competing engine implementations (`PaperTradeEngine` and `PaperTradingEngine`) with inconsistent wiring, broken closePosition logic, no signal-to-trade auto-execution, and scattered config issues. The system is ~60% functional — positions can be created manually but closing them doesn't release capital, P&L is inaccurate, and signals never auto-execute.

## Goal

Unify on `PaperTradingEngine` as the single canonical paper trading engine, fix all critical bugs, wire up automatic signal-to-trade execution, clean up config, and ensure all tests pass.

## Files to Delete

1. `backend/broker/src/main/java/com/swingtrade/broker/engine/PaperTradeEngine.java` — old broken engine
2. `backend/broker/src/main/java/com/swingtrade/broker/config/BrokerConfig.java` — only created PaperTradeEngine bean
3. `backend/broker/src/main/java/com/swingtrade/broker/service/DryRunService.java` — duplicate of factory version
4. `backend/broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java` — duplicate of factory version
5. `backend/broker/src/test/java/com/swingtrade/broker/PaperTradeEngineTest.java` — tests for deleted engine

## Files to Modify

1. `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingServiceImpl.java`
2. `backend/broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java`
3. `backend/api/src/main/java/com/swingtrade/api/PositionService.java`
4. `backend/broker/src/main/java/com/swingtrade/broker/risk/DailyLossCircuitBreaker.java`
5. `backend/broker/src/main/resources/application.properties`
6. `backend/api/src/main/resources/application-local.properties`
7. `backend/broker/src/test/java/com/swingtrade/broker/PaperTradingServiceImplTest.java`
8. `backend/broker/src/test/java/com/swingtrade/broker/BrokerServiceFactoryTest.java`
9. `backend/api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java`
10. `backend/api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java`

## New Files

1. `backend/api/src/main/java/com/swingtrade/api/SignalExecutionJob.java` — scheduled job to auto-execute BUY signals

---

## Task 1: Rewrite PaperTradingServiceImpl

**File:** `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingServiceImpl.java`

**Changes:**
- Remove `@Primary` annotation
- Change import from `PaperTradeEngine` to `PaperTradingEngine`
- Change field type from `PaperTradeEngine` to `PaperTradingEngine`
- Update constructor to inject `PaperTradingEngine`
- Update all delegate methods to use `PaperTradingEngine` API:
  - `placeOrder()` → `paperTradingEngine.executePendingOrder(order.getOrderId(), order.getPrice())`
  - `cancelOrder()` → `paperTradingEngine.cancelOrder(orderId)`
  - `getPortfolio()` → `paperTradingEngine.getPortfolio()`
  - `getOpenPositions()` → `paperTradingEngine.getOpenPositions()`
  - `getPosition(positionId)` → `paperTradingEngine.getPosition(positionId)`
  - `calculateProfitLoss(position)` → `position.getProfitLoss()`
  - `getMaxConcurrentPositions()` → `paperTradingEngine.getMaxConcurrentPositions()`
  - `getMaxCapitalPerPosition()` → `paperTradingEngine.getMaxCapitalPerPosition()`

**Why remove @Primary:** With duplicate DryRunService/LiveTradingService deleted, there's no bean conflict. The factory creates the appropriate service dynamically.

---

## Task 2: Update BrokerServiceFactory

**File:** `backend/broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java`

**Changes:**
- Change import from `PaperTradeEngine` to `PaperTradingEngine`
- Change constructor parameter from `PaperTradeEngine` to `PaperTradingEngine`
- Update `createService(BrokerMode.PAPER)` to use `PaperTradingEngine`
- Update `createService(default)` to use `PaperTradingEngine`

**No changes needed for:** `createLiveTradingService()` and `createDryRunService()` — they use the factory DryRunService/LiveTradingService (not the deleted service versions).

---

## Task 3: Delete PaperTradeEngine and BrokerConfig

**Files:**
- `backend/broker/src/main/java/com/swingtrade/broker/engine/PaperTradeEngine.java` → DELETE
- `backend/broker/src/main/java/com/swingtrade/broker/config/BrokerConfig.java` → DELETE

**Verify no other references:** After deletion, run `grep -r "PaperTradeEngine" backend/` and `grep -r "BrokerConfig" backend/` to confirm no remaining imports. Fix any found.

---

## Task 4: Delete Duplicate DryRunService/LiveTradingService

**Files:**
- `backend/broker/src/main/java/com/swingtrade/broker/service/DryRunService.java` → DELETE
- `backend/broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java` → DELETE

**Verify no other references:** Run `grep -r "import com.swingtrade.broker.service.DryRunService" backend/` and same for LiveTradingService. The factory versions are in `com.swingtrade.broker.factory` package.

---

## Task 5: Fix PositionService.createPosition()

**File:** `backend/api/src/main/java/com/swingtrade/api/PositionService.java`

**Current problem:** `createPosition()` saves a `PositionEntity` to DB but does NOT create the position in `PaperTradingEngine`. The engine and DB are out of sync.

**Fix:**
```java
public PositionResponse createPosition(TradeRequest request) {
    // ... existing validation ...
    
    PositionEntity entity = new PositionEntity();
    // ... existing entity setup ...
    
    PositionEntity savedEntity = positionRepository.save(entity);
    
    // NEW: Also create position in PaperTradingEngine
    try {
        Order order = paperTradingEngine.getPendingOrders().isEmpty() ? null : null;
        // Alternative: use positionManager.createPosition directly
        String positionId = "POS_" + String.format("%08d", savedEntity.getId());
        positionManager.createPosition(
            positionId,
            request.getSymbol(),
            TradeDirection.LONG,
            request.getQuantity(),
            entryPrice,
            null, // ATR not available at creation time
            request.getEntryReason()
        );
    } catch (Exception e) {
        logger.warn("Failed to create position in PaperTradingEngine: {}", e.getMessage());
    }
    
    return convertToResponse(savedEntity);
}
```

**Better approach:** Inject `PositionManager` and `OrderManager` into `PositionService` and use them to create the position through the proper engine pipeline:
1. Create order via `orderManager.createBuyOrder()`
2. Execute order via `paperTradingEngine.executePendingOrder()`
3. Position is created automatically by `executePendingOrder` → `createPositionFromOrder`

---

## Task 6: Fix PositionService.closePosition()

**File:** `backend/api/src/main/java/com/swingtrade/api/PositionService.java`

**Current problem:** Closes in DB first, then tries to close in engine with silent exception swallowing.

**Fix:**
```java
public PositionResponse closePosition(String symbol, String exitReason) {
    Optional<PositionEntity> entityOpt = positionRepository.findOpenBySymbol(symbol);
    if (entityOpt.isEmpty()) return null;
    
    PositionEntity entity = entityOpt.get();
    BigDecimal exitPrice = entity.getCurrentPrice() != null ? entity.getCurrentPrice() : entity.getEntryPrice();
    String reason = exitReason != null ? exitReason : "manual_close";
    
    // NEW: Close in engine FIRST (updates portfolio capital, calculates P&L)
    try {
        paperTradingEngine.closePosition(entity.getId(), exitPrice, reason);
    } catch (Exception e) {
        logger.warn("Position {} not found in PaperTradingEngine: {}", symbol, e.getMessage());
        // Don't swallow — position may exist in DB but not in engine
    }
    
    // Then update DB entity
    entity.setStatus("CLOSED");
    entity.setUpdatedAt(LocalDateTime.now());
    positionRepository.save(entity);
    
    return convertToResponse(entity);
}
```

---

## Task 7: Fix Win Rate Calculation

**File:** `backend/api/src/main/java/com/swingtrade/api/PositionService.java`

**Current bug (line 162):** `winRate = targetHitCount / closedCount` — only counts target-hit positions as wins, ignoring manually closed profitable positions.

**Fix:**
```java
// Count winners: positions where PnL > 0 (regardless of close reason)
long winCount = allPositions.stream()
    .filter(p -> !"OPEN".equals(p.getStatus()))
    .filter(p -> p.getPnl() != null && p.getPnl().compareTo(BigDecimal.ZERO) > 0)
    .count();

if (closedCount > 0) {
    stats.setWinRate((double) winCount / closedCount * 100.0);
}
```

**Also fix:** The PnL calculation on line 138-142 uses `currentPrice - entryPrice` which doesn't account for position direction. For long positions this is correct, but should explicitly check direction.

---

## Task 8: Fix DailyLossCircuitBreaker.calculateTotalRealizedPnL()

**File:** `backend/broker/src/main/java/com/swingtrade/broker/risk/DailyLossCircuitBreaker.java`

**Current bug (line 218):** Returns `BigDecimal.ZERO` with comment "can be enhanced."

**Fix:**
```java
private BigDecimal calculateTotalRealizedPnL() {
    return positionManager.getTotalRealizedPnL();
}
```

This uses the PositionManager's existing implementation which sums P&L from all closed positions.

---

## Task 9: Wire Up Signal-to-Trade Pipeline

**New file:** `backend/api/src/main/java/com/swingtrade/api/SignalExecutionJob.java`

**Purpose:** Scheduled job that polls for unprocessed BUY signals and auto-executes them as paper trades.

**Implementation:**
```java
@Service
@Slf4j
public class SignalExecutionJob {
    
    private final SignalRepository signalRepository;
    private final PaperTradingEngine paperTradingEngine;
    private final OhlcvCandleRepository ohlcvCandleRepository;
    private final SignalService signalService;
    
    @Scheduled(fixedDelayString = "${paper.trading.signal-execution-delay:30000}")
    public void executePendingSignals() {
        // 1. Find unprocessed BUY signals from the last N minutes
        List<SignalEntity> pendingSignals = signalRepository.findUnprocessedBuySignals(
            LocalDateTime.now().minusMinutes(5)
        );
        
        for (SignalEntity signalEntity : pendingSignals) {
            executeSignal(signalEntity);
        }
    }
    
    private void executeSignal(SignalEntity signalEntity) {
        // 2. Get current price from latest candle
        OhlcvCandleEntity latestCandle = ohlcvCandleRepository.findLatestBySymbol(
            signalEntity.getSymbol(), LocalDateTime.now().minusMinutes(1)
        );
        
        if (latestCandle == null) {
            log.warn("No candle data for {}, skipping signal execution", signalEntity.getSymbol());
            return;
        }
        
        // 3. Convert to domain Signal
        com.swingtrade.domain.Signal domainSignal = new com.swingtrade.domain.Signal(
            signalEntity.getId(),
            signalEntity.getSymbol(),
            com.swingtrade.domain.Signal.SignalType.BUY,
            signalEntity.getConfidence(),
            signalEntity.getDate(),
            signalEntity.getReasoning(),
            signalEntity.getEntryPrice(),
            signalEntity.getStopLoss(),
            signalEntity.getTarget(),
            signalEntity.getRiskReward(),
            signalEntity.getIndicators(),
            signalEntity.getGeneratedAt()
        );
        
        // 4. Execute via PaperTradingEngine
        paperTradingEngine.executeSignal(domainSignal, latestCandle.getClosePrice());
        
        // 5. Mark as processed (add processed flag to signal entity)
        signalEntity.setProcessed(true);
        signalRepository.save(signalEntity);
    }
}
```

**Requires:**
- Add `processed` boolean column to `SignalEntity` (new Flyway migration `V4__add_signal_processed_flag.sql`)
- Add `findUnprocessedBuySignals` method to `SignalRepository`
- Add `findLatestBySymbol` method to `OhlcvCandleRepository`

---

## Task 10: Add Flyway Migration for Signal Processed Flag

**New file:** `backend/data/src/main/resources/db/migration/V4__add_signal_processed_flag.sql`

```sql
ALTER TABLE signals ADD COLUMN processed BOOLEAN DEFAULT FALSE;
CREATE INDEX idx_signals_processed_date ON signals(processed, date);
```

---

## Task 11: Update SignalEntity

**File:** `backend/data/src/main/java/com/swingtrade/data/entity/SignalEntity.java`

**Changes:**
- Add `processed` field: `private Boolean processed = false;`
- Add getter/setter
- Update `toDomain()` if needed

---

## Task 12: Update SignalRepository

**File:** `backend/data/src/main/java/com/swingtrade/data/repository/SignalRepository.java`

**Add method:**
```java
List<SignalEntity> findBySignalTypeAndDateAfterAndProcessedFalse(
    SignalType signalType, LocalDateTime date, boolean processed);
```

---

## Task 13: Update OhlcvCandleRepository

**File:** `backend/data/src/main/java/com/swingtrade/data/repository/OhlcvCandleRepository.java`

**Add method:**
```java
Optional<OhlcvCandleEntity> findLatestBySymbolBeforeDate(String symbol, LocalDateTime date);
```

---

## Task 14: Update Config Files

### `backend/broker/src/main/resources/application.properties`

**Change:**
```properties
# Line 173: Change from dry_run to paper
broker.mode=paper
```

### `backend/api/src/main/resources/application-local.properties`

**No changes needed** — already has `trading.paper.trading.enabled=true` and `app.features.paper-trading.enabled=true`.

### `backend/.env`

**Consider adding:** `SPRING_PROFILES_ACTIVE=local,fyers` (already referenced in CLAUDE.md but not in .env)

---

## Task 15: Fix Tests

### Delete
- `backend/broker/src/test/java/com/swingtrade/broker/PaperTradeEngineTest.java`

### Update
- `backend/broker/src/test/java/com/swingtrade/broker/PaperTradingServiceImplTest.java` — update all assertions to match `PaperTradingEngine` API
- `backend/broker/src/test/java/com/swingtrade/broker/BrokerServiceFactoryTest.java` — update to use `PaperTradingEngine`
- `backend/api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java` — remove `@Disabled` annotation, fix any failing assertions
- `backend/api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java` — verify tests pass with unified engine

### New
- `backend/api/src/test/java/com/swingtrade/api/SignalExecutionJobTest.java` — test signal auto-execution

---

## Task 16: Verify Build and Run Tests

```bash
cd backend && mvn clean install
cd backend/data && mvn test
cd backend/broker && mvn test
cd backend/api && mvn test
```

---

## Execution Order

```
Phase 1: Delete old code
  1. Delete PaperTradeEngine.java
  2. Delete BrokerConfig.java
  3. Delete duplicate DryRunService.java (broker/service/)
  4. Delete duplicate LiveTradingService.java (broker/service/)
  5. Delete PaperTradeEngineTest.java

Phase 2: Rewrite references
  6. Rewrite PaperTradingServiceImpl.java (Task 1)
  7. Update BrokerServiceFactory.java (Task 2)

Phase 3: Fix bugs
  8. Fix PositionService.createPosition() (Task 5)
  9. Fix PositionService.closePosition() (Task 6)
  10. Fix win rate calculation (Task 7)
  11. Fix DailyLossCircuitBreaker (Task 8)

Phase 4: New features
  12. Add Flyway migration V4 (Task 10)
  13. Update SignalEntity (Task 11)
  14. Update SignalRepository (Task 12)
  15. Update OhlcvCandleRepository (Task 13)
  16. Create SignalExecutionJob (Task 9)

Phase 5: Config & tests
  17. Update config files (Task 14)
  18. Fix tests (Task 15)
  19. Verify build (Task 16)
```

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Breaking existing API contracts | PaperTradingEngine already exposes the same BrokerService methods |
| SignalExecutionJob executes on stale data | Schedule after candle ingestion; add date-before filter |
| Tests fail after engine swap | Update all test assertions; PaperTradingEngine has richer API |
| PositionService creates position in both DB and engine | Use single pipeline: DB → OrderManager → executePendingOrder |
| Circular dependency: API module needs broker module | API already depends on broker; PaperTradingEngine is in broker |

---

## Success Criteria

1. `PaperTradeEngine` and `BrokerConfig` deleted — zero references remain
2. `PaperTradingServiceImpl` delegates entirely to `PaperTradingEngine`
3. `BrokerServiceFactory` creates `PaperTradingEngine` for PAPER mode
4. `createPosition()` creates position in both DB and engine
5. `closePosition()` properly updates portfolio capital and P&L
6. Win rate correctly counts profitable positions (PnL > 0)
7. `DailyLossCircuitBreaker` tracks realized P&L from closed positions
8. `SignalExecutionJob` auto-executes BUY signals as paper trades
9. All broker module tests pass
10. All API module tests pass (TradingControllerTest no longer @Disabled)
11. `mvn clean install` succeeds with zero errors