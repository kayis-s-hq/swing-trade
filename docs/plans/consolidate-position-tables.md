# Consolidate Position Tables into Unified `positions` Table

## Section 1: What's Already Done

Nothing.

## Section 2: Implementation Steps

### Step 1: Add `broker_type` to domain `Position` record

**File:** `backend/core/src/main/java/com/swingtrade/domain/Position.java`

Add `String brokerType` as field #2 (after `id`, before `symbol`). Update compact constructor with `if (brokerType == null) brokerType = "PAPER";`. Add `String brokerType` parameter to `of()` factory after `id`, pass to constructor. Add `null` for brokerType in `createWithRisk()`.

### Step 2: Add `broker_type` column to `PositionEntity`

**File:** `backend/data/src/main/java/com/swingtrade/data/entity/PositionEntity.java`

Add field: `@Column(name="broker_type", length=10, nullable=false, columnDefinition="VARCHAR(10) DEFAULT 'PAPER'") private String brokerType = "PAPER";`

Update `PositionEntity(Position)` constructor: `this.brokerType = position.brokerType() != null ? position.brokerType() : "PAPER";`

Update `fromDomain()`: `entity.setBrokerType(position.brokerType() != null ? position.brokerType() : "PAPER");`

Update `toDomain()`: add `brokerType` as last arg to `Position.of()`.

Add getter/setter: `getBrokerType()` / `setBrokerType()`.

### Step 3: Add `findByBrokerType` to `PositionRepository`

**File:** `backend/data/src/main/java/com/swingtrade/data/repository/PositionRepository.java`

Add after line 100:
```java
@Query("SELECT p FROM PositionEntity p WHERE p.brokerType = :brokerType ORDER BY p.entryDate DESC")
List<PositionEntity> findByBrokerType(@Param("brokerType") String brokerType);
```

### Step 4: Add `findByBrokerType` to `PositionStore` interface

**File:** `backend/core/src/main/java/com/swingtrade/domain/store/PositionStore.java`

Add: `List<Position> findByBrokerType(String brokerType);`

### Step 5: Implement `findByBrokerType` in `PositionStoreImpl`

**File:** `backend/data/src/main/java/com/swingtrade/data/store/PositionStoreImpl.java`

Add after line 67:
```java
@Override public List<Position> findByBrokerType(String brokerType) {
    return repository.findByBrokerType(brokerType).stream().map(PositionEntity::toDomain).toList();
}
```

### Step 6: Refactor `PaperTradingStateService` to use unified `PositionRepository`

**File:** `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java`

Add `PositionRepository unifiedPositionRepo` to constructor. Remove imports for PaperTradingPositionEntity, PaperTradingPositionRepository, PaperTradingClosedPositionEntity, PaperTradingClosedPositionRepository.

`savePosition()` — use `unifiedPositionRepo`: find by position_id, create new PositionEntity with `brokerType="PAPER"`, or update existing. Save to unifiedPositionRepo.

`closePosition()` — find entity in unifiedPositionRepo by position_id, set status="CLOSED", currentPrice, realizedPnL, exitTime, exitReason, save. No more moving between two tables.

`loadOpenPositions()` — use `unifiedPositionRepo.findAllOpenPositions()`, filter by `brokerType="PAPER"`.

`loadClosedPositions()` — use `unifiedPositionRepo.findByStatus("CLOSED")` + `findByStatus("STOPPED")` + `findByStatus("TARGET_HIT")`, filter by `brokerType="PAPER"`.

Keep: Portfolio/Order/Snapshot repos (still needed for other state).

### Step 7: Update `PositionService` — remove dual-write

**File:** `backend/api/src/main/java/com/swingtrade/api/service/PositionService.java`

Remove import: `PaperTradingClosedPositionEntity`.

`getClosedPositions()` — read from positionStore: combine findByStatus(CLOSED) + findByStatus(STOPPED) + findByStatus(TARGET_HIT).

Remove `convertToResponse(PaperTradingClosedPositionEntity)` method (lines 363-380).

`getPositionStats()` — use positionStore for closed positions.

### Step 8: Update `PerformanceService` — read from unified positions table

**File:** `backend/api/src/main/java/com/swingtrade/api/service/PerformanceService.java`

Remove imports: `PaperTradingClosedPositionEntity`, `PaperTradingClosedPositionRepository`.

Replace constructor: inject `PositionRepository` instead of `PaperTradingClosedPositionRepository`.

Replace all `closedPosRepo.findAllByOrderByExitTimeDesc()` with a helper method:
```java
private List<PositionEntity> fetchClosedPositions() {
    List<PositionEntity> c = new ArrayList<>();
    c.addAll(positionRepo.findByStatus("CLOSED"));
    c.addAll(positionRepo.findByStatus("STOPPED"));
    c.addAll(positionRepo.findByStatus("TARGET_HIT"));
    c.sort(Comparator.comparing(e -> e.getExitTime() != null ? e.getExitTime() : LocalDateTime.MAX));
    return c;
}
```
Cache result in local variable in each method to avoid repeated DB calls.

### Step 9: Update `MonthlyReportService` — remove broker dependency

**File:** `backend/api/src/main/java/com/swingtrade/api/service/MonthlyReportService.java`

Remove imports: `TelegramConfig`, `TelegramMessageFormatter`. Update constructor to accept only PositionRepository + SignalRepository. Remove Telegram send logic from `generateMonthlyReport()`, keep logging only.

### Step 10: Write Flyway migration V20

See Section 3.

### Step 11: Delete broker module files

See Section 4.

## Section 3: Migration SQL

**File:** `V20__consolidate_positions.sql`

```sql
ALTER TABLE positions ADD COLUMN IF NOT EXISTS broker_type VARCHAR(10) DEFAULT 'PAPER';
UPDATE positions SET broker_type = 'PAPER' WHERE broker_type IS NULL;

-- Merge paper_trading_positions (open positions)
INSERT INTO positions (broker_type, symbol, entry_price, entry_date, quantity,
    stop_loss, target, status, entry_reason, current_price,
    position_id, exchange, direction, average_price,
    unrealized_pnl, realized_pnl, entry_time, exit_time, exit_reason)
SELECT 'PAPER', p.symbol, p.entry_price, DATE(p.entry_time), p.quantity,
    p.stop_loss, p.target_price, COALESCE(p.status,'OPEN'), p.entry_reason,
    p.current_price, p.position_id, 'NSE', COALESCE(p.direction,'LONG'),
    p.average_price, COALESCE(p.unrealized_pnl, p.pnl, 0),
    COALESCE(p.realized_pnl, 0), p.entry_time, p.exit_time, p.exit_reason
FROM paper_trading_positions p
WHERE NOT EXISTS (SELECT 1 FROM positions pp WHERE pp.position_id = p.position_id);

-- Merge paper_trading_closed_positions (closed positions)
INSERT INTO positions (broker_type, symbol, entry_price, entry_date, quantity,
    stop_loss, target, status, entry_reason, current_price,
    position_id, exchange, direction,
    unrealized_pnl, realized_pnl, entry_time, exit_time, exit_reason)
SELECT 'PAPER', c.symbol, c.entry_price, DATE(c.entry_time), c.quantity,
    NULL, NULL, COALESCE(c.status,'CLOSED'), c.entry_reason,
    c.exit_price, c.position_id, 'NSE', COALESCE(c.direction,'LONG'),
    COALESCE(c.pnl, 0), COALESCE(c.realized_pnl, 0),
    c.entry_time, c.exit_time, c.exit_reason
FROM paper_trading_closed_positions c
WHERE NOT EXISTS (SELECT 1 FROM positions pp WHERE pp.position_id = c.position_id);

DROP TABLE IF EXISTS paper_trading_closed_positions;
DROP TABLE IF EXISTS paper_trading_positions;
```

## Section 4: Files to Delete

| File | Reason |
|------|--------|
| `broker/.../entity/PaperTradingPositionEntity.java` | Replaced by PositionEntity with broker_type='PAPER' |
| `broker/.../entity/PaperTradingClosedPositionEntity.java` | Merged into positions table |
| `broker/.../repository/PaperTradingPositionRepository.java` | PositionRepository handles it |
| `broker/.../repository/PaperTradingClosedPositionRepository.java` | PositionRepository handles it |

Keep: Order/Portfolio/Snapshot entities and repos (still needed by PaperTradingStateService). Keep `telegram/` dir (still referenced by MonthlyReportService).

## Section 5: Verification

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
cd backend && mvn clean compile -DskipTests && mvn test

# After app starts, verify in DB:
psql -h localhost -p 5435 -U swing_trade -d swing_trade -c \
  "SELECT broker_type, COUNT(*) FROM positions GROUP BY broker_type;"
# Expected: broker_type='PAPER' with rows from both old tables

psql -h localhost -p 5435 -U swing_trade -d swing_trade -c \
  "SELECT tablename FROM pg_tables WHERE tablename LIKE '%paper%' OR tablename LIKE '%position%';"
# Expected: paper_trading_positions and paper_trading_closed_positions NOT listed

curl -s http://localhost:8080/api/positions/open | jq '.[0].brokerType'
# Expected: "PAPER"

curl -s http://localhost:8080/api/performance | jq .
# Expected: non-null metrics, no crash
```