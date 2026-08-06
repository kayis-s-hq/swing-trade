# Paper Trading: DB Persistence + EOD Monitoring

## Problem Statement

Paper trading is ~60% functional. Two gaps block a working simulation:

1. **All state is in-memory** — positions, orders, portfolio capital. Restart = zero state.
2. **No position monitoring** — `PaperTradingEngine.checkPositionTriggers()` and `updatePositionsFromDomain()` are fully implemented but never called. SL/TP never auto-fires. Positions can only be closed manually via REST API.

The existing `paper-trading-unification.md` plan addressed engine consolidation and signal auto-execution (both done). This plan builds on top: persistence + monitoring.

## Decisions (user-confirmed)

- **Data source:** EOD candle data from Fyers (same ingestion pipeline as existing)
- **Auth:** Best-effort retry on token expiry (PIN-based refresh, log on failure)
- **Signal strategy:** EOD-only. Signals generated at 17:00 IST, executed by SignalExecutionJob
- **Monitoring:** Fyers-specific job, uses `FyersServiceClient` directly
- **Persistence:** All paper trading state (positions, orders, portfolio) persisted to DB. Separate from live trading data.
- **Fyers cost:** Historical candle data is free (rate-limited, not metered)

---

## Architecture

```
FyersServiceClient → EodIngestionScheduler (16:30 IST) → ohlcv_candles (DB)
  → SignalEngine (17:00 IST) → signals (DB)
  → SignalExecutionJob (30s) → PaperTradingEngine
    → PaperTradingStateService (NEW) → paper_trading_positions (DB)
    → PaperTradingStateService → paper_trading_orders (DB)
    → PaperTradingStateService → paper_trading_portfolio (DB)

PaperTradingMonitorService (NEW, 16:45 IST) → FyersServiceClient
  → latest candle → PaperTradingEngine.updatePositionsFromDomain()
  → PaperTradingEngine.checkPositionTriggers()
  → PaperTradingStateService persist state changes
```

Key design: PaperTradingEngine stays in-memory (fast). PaperTradingStateService bridges engine ↔ DB. On startup, state is loaded from DB into the engine. After each engine mutation, state is persisted back.

---

## Task 1: DB Schema — Paper Trading Tables

**New migration:** `backend/data/src/main/resources/db/migration/V13__add_paper_trading_state.sql`

```sql
-- Portfolio state (single row)
CREATE TABLE IF NOT EXISTS paper_trading_portfolio (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    VARCHAR(32) NOT NULL DEFAULT 'default',
    initial_capital NUMERIC(15,2) NOT NULL DEFAULT 10000000,
    current_capital NUMERIC(15,2) NOT NULL DEFAULT 10000000,
    total_realized_pnl NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_unrealized_pnl NUMERIC(15,2) NOT NULL DEFAULT 0,
    open_position_count INTEGER NOT NULL DEFAULT 0,
    max_concurrent_positions INTEGER NOT NULL DEFAULT 5,
    max_capital_per_position_pct NUMERIC(5,2) NOT NULL DEFAULT 20,
    commission_rate NUMERIC(6,4) NOT NULL DEFAULT 0.05,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default row
INSERT INTO paper_trading_portfolio (id, portfolio_id, initial_capital, current_capital)
VALUES (1, 'default', 10000000, 10000000)
ON CONFLICT (id) DO NOTHING;

-- Open positions
CREATE TABLE IF NOT EXISTS paper_trading_positions (
    id              BIGSERIAL PRIMARY KEY,
    position_id     VARCHAR(32) NOT NULL UNIQUE,
    symbol          VARCHAR(16) NOT NULL,
    direction       VARCHAR(10) NOT NULL DEFAULT 'LONG',
    quantity        INTEGER NOT NULL,
    entry_price     NUMERIC(15,2) NOT NULL,
    average_price   NUMERIC(15,2),
    current_price   NUMERIC(15,2),
    stop_loss       NUMERIC(15,2),
    target_price    NUMERIC(15,2),
    pnl             NUMERIC(15,2) NOT NULL DEFAULT 0,
    unrealized_pnl  NUMERIC(15,2) NOT NULL DEFAULT 0,
    realized_pnl    NUMERIC(15,2) NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    entry_reason    TEXT,
    entry_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exit_time       TIMESTAMP,
    exit_reason     VARCHAR(64),
    last_updated    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pt_positions_symbol ON paper_trading_positions(symbol, status);
CREATE INDEX IF NOT EXISTS idx_pt_positions_status ON paper_trading_positions(status);

-- Closed positions (history)
CREATE TABLE IF NOT EXISTS paper_trading_closed_positions (
    id              BIGSERIAL PRIMARY KEY,
    position_id     VARCHAR(32) NOT NULL,
    symbol          VARCHAR(16) NOT NULL,
    direction       VARCHAR(10) NOT NULL DEFAULT 'LONG',
    quantity        INTEGER NOT NULL,
    entry_price     NUMERIC(15,2) NOT NULL,
    exit_price      NUMERIC(15,2) NOT NULL,
    stop_loss       NUMERIC(15,2),
    target_price    NUMERIC(15,2),
    pnl             NUMERIC(15,2) NOT NULL,
    realized_pnl    NUMERIC(15,2) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    entry_reason    TEXT,
    entry_time      TIMESTAMP NOT NULL,
    exit_time       TIMESTAMP NOT NULL,
    exit_reason     VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pt_closed_positions_symbol ON paper_trading_closed_positions(symbol);
CREATE INDEX IF NOT EXISTS idx_pt_closed_positions_exit_time ON paper_trading_closed_positions(exit_time);

-- Orders
CREATE TABLE IF NOT EXISTS paper_trading_orders (
    id              BIGSERIAL PRIMARY KEY,
    order_id        VARCHAR(32) NOT NULL UNIQUE,
    symbol          VARCHAR(16) NOT NULL,
    order_type      VARCHAR(16) NOT NULL DEFAULT 'MARKET',
    direction       VARCHAR(10) NOT NULL,
    quantity        INTEGER NOT NULL,
    price           NUMERIC(15,2) NOT NULL,
    limit_price     NUMERIC(15,2),
    stop_price      NUMERIC(15,2),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    commission      NUMERIC(15,4) NOT NULL DEFAULT 0,
    executed_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pt_orders_symbol ON paper_trading_orders(symbol);
CREATE INDEX IF NOT EXISTS idx_pt_orders_status ON paper_trading_orders(status);
CREATE INDEX IF NOT EXISTS idx_pt_orders_created_at ON paper_trading_orders(created_at);

-- Portfolio history (snapshots for P&L charts)
CREATE TABLE IF NOT EXISTS paper_trading_portfolio_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    snapshot_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_value     NUMERIC(15,2) NOT NULL,
    cash_balance    NUMERIC(15,2) NOT NULL,
    market_value    NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_pnl       NUMERIC(15,2) NOT NULL DEFAULT 0,
    return_pct      NUMERIC(8,4) NOT NULL DEFAULT 0,
    open_positions  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pt_snapshots_time ON paper_trading_portfolio_snapshots(snapshot_time);
```

---

## Task 2: JPA Entities + Repositories

### New entities in `backend/data/src/main/java/com/swingtrade/data/entity/`:

1. `PaperTradingPortfolioEntity.java`
   - Fields: portfolioId, initialCapital, currentCapital, totalRealizedPnl, totalUnrealizedPnl, openPositionCount, maxConcurrentPositions, maxCapitalPerPositionPct, commissionRate
   - `@Id` = 1 (singleton row)
   - `@Table(name = "paper_trading_portfolio")`

2. `PaperTradingPositionEntity.java`
   - Fields: positionId (unique), symbol, direction, quantity, entryPrice, averagePrice, currentPrice, stopLoss, targetPrice, pnl, unrealizedPnl, realizedPnl, status, entryReason, entryTime, exitTime, exitReason, lastUpdated
   - `@Table(name = "paper_trading_positions")`

3. `PaperTradingClosedPositionEntity.java`
   - Fields: positionId, symbol, direction, quantity, entryPrice, exitPrice, stopLoss, targetPrice, pnl, realizedPnl, status, entryReason, entryTime, exitTime, exitReason
   - `@Table(name = "paper_trading_closed_positions")`

4. `PaperTradingOrderEntity.java`
   - Fields: orderId (unique), symbol, orderType, direction, quantity, price, limitPrice, stopPrice, status, commission, executedAt, createdAt, updatedAt
   - `@Table(name = "paper_trading_orders")`

5. `PaperTradingSnapshotEntity.java`
   - Fields: snapshotTime, totalValue, cashBalance, marketValue, totalPnl, returnPct, openPositions
   - `@Table(name = "paper_trading_portfolio_snapshots")`

### New repositories in `backend/data/src/main/java/com/swingtrade/data/repository/`:

1. `PaperTradingPortfolioRepository.java` — `save(PaperTradingPortfolioEntity)`, `findById(1L)`, `findByPortfolioId(String)`
2. `PaperTradingPositionRepository.java` — `findBySymbolAndStatusOpen(String)`, `findAllByStatusOpen()`, `findAllByStatusClosedOrderByExitTimeDesc()`, `save()`, `deleteByPositionId(String)`
3. `PaperTradingClosedPositionRepository.java` — `findBySymbol(String)`, `findAllByOrderByExitTimeDesc()`, `countByStatusClosed()`
4. `PaperTradingOrderRepository.java` — `findByStatusPending()`, `findBySymbol(String)`, `save()`
5. `PaperTradingSnapshotRepository.java` — `findAllByOrderBySnapshotTimeDesc()`, `findBySnapshotTimeAfter(LocalDateTime)`, `save()`

---

## Task 3: PaperTradingStateService — DB Bridge

**New file:** `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingStateService.java`

This is the core persistence layer. It bridges the in-memory `PaperTradingEngine` with DB entities.

```java
@Service
public class PaperTradingStateService {
    private final PaperTradingEngine engine;
    private final PaperTradingPortfolioRepository portfolioRepo;
    private final PaperTradingPositionRepository positionRepo;
    private final PaperTradingClosedPositionRepository closedPositionRepo;
    private final PaperTradingOrderRepository orderRepo;
    private final PaperTradingSnapshotRepository snapshotRepo;
    
    // On startup: load all state into engine
    @PostConstruct
    public void loadState() {
        // 1. Load portfolio
        PaperTradingPortfolioEntity saved = portfolioRepo.findById(1L).orElse(null);
        if (saved != null) {
            engine.getPortfolio().setCurrentCapital(saved.getCurrentCapital());
            engine.getPortfolio().setInitialCapital(saved.getInitialCapital());
        }
        
        // 2. Load open positions into engine
        for (PaperTradingPositionEntity e : positionRepo.findAllByStatusOpen()) {
            Position pos = e.toDomain();
            engine.getPortfolio().addPosition(pos);
            positionManager.addPosition(pos);
        }
        
        // 3. Load closed positions
        for (PaperTradingClosedPositionEntity e : closedPositionRepo.findAllByOrderByExitTimeDesc()) {
            // Just track realized P&L, don't add to open positions
        }
        
        // 4. Load order history for ID continuity
        // ...
    }
    
    // Persist portfolio state
    public void savePortfolio() {
        PaperTradingPortfolioEntity entity = portfolioRepo.findById(1L).orElse(new PaperTradingPortfolioEntity());
        entity.setCurrentCapital(engine.getPortfolio().getCurrentCapital());
        entity.setInitialCapital(engine.getPortfolio().getInitialCapital());
        entity.setTotalRealizedPnl(engine.getTotalRealizedPnL());
        entity.setTotalUnrealizedPnl(engine.getTotalUnrealizedPnL());
        entity.setOpenPositionCount(engine.getOpenPositionCount());
        portfolioRepo.save(entity);
    }
    
    // Persist a new open position
    public void savePosition(Position position) {
        PaperTradingPositionEntity entity = new PaperTradingPositionEntity(position);
        entity.setLastUpdated(LocalDateTime.now());
        positionRepo.save(entity);
    }
    
    // Move position to closed, persist to closed_positions
    public void closePosition(PaperTradingPositionEntity entity) {
        PaperTradingClosedPositionEntity closed = new PaperTradingClosedPositionEntity();
        // Copy fields from open position
        closedPositionRepo.save(closed);
        positionRepo.deleteByPositionId(entity.getPositionId());
    }
    
    // Persist order
    public void saveOrder(Order order) {
        PaperTradingOrderEntity entity = new PaperTradingOrderEntity(order);
        orderRepo.save(entity);
    }
    
    // Snapshot portfolio for charts
    public void saveSnapshot() {
        PaperTradingSnapshotEntity entity = new PaperTradingSnapshotEntity();
        entity.setTotalValue(engine.getPortfolio().getTotalValue());
        entity.setCashBalance(engine.getCurrentCash());
        entity.setMarketValue(engine.getPortfolio().getTotalValue().subtract(engine.getCurrentCash()));
        entity.setTotalPnl(engine.getTotalPnL());
        entity.setReturnPct(engine.getReturnPercentage());
        entity.setOpenPositions(engine.getOpenPositionCount());
        snapshotRepo.save(entity);
    }
}
```

---

## Task 4: Wire PaperTradingStateService into PaperTradingEngine

Modify `PaperTradingEngine` to accept `PaperTradingStateService` (via setter or constructor) and call `savePosition()` / `saveOrder()` / `savePortfolio()` after each mutation method:

- `executeSignal()` → after order creation, `stateService.saveOrder(order)`
- `executePendingOrder()` → after position creation, `stateService.savePosition(position)`
- `closePosition()` → `stateService.closePosition(entity)` then `stateService.savePortfolio()`
- `partialExitPosition()` → `stateService.savePosition(position)` + `stateService.savePortfolio()`
- `updatePositionsFromDomain()` → after price update, `stateService.savePosition(position)`

This keeps the engine in-memory fast while ensuring every mutation is persisted.

---

## Task 5: Wire PaperTradingStateService into PaperTradingServiceImpl

Update `PaperTradingServiceImpl` methods to also persist through `stateService`:

- `placeOrder()` → after engine call, `stateService.saveOrder()`
- `cancelOrder()` → `stateService.saveOrder()` with updated status
- `getPortfolio()` → before returning, `stateService.savePortfolio()` (throttled to avoid DB thrash)

---

## Task 6: Fix SignalEntity field population

**File:** `backend/strategy/src/main/java/com/swingtrade/strategy/engine/SignalEngine.java`

**Current problem:** `generatePriceActionSignalForSymbol()` creates a `SignalResult` with computed ATR, EMA values, but does NOT set `entryPrice`, `stopLoss`, `target`, `riskReward` on the `SignalEntity`. The `SwingTradingStrategy` may also have this issue.

**Fix:** In `SignalEngine.generatePriceActionSignalForSymbol()`, after computing the signal:

```java
// After computing signal from PriceActionSignalEngine
signalEntity.setEntryPrice(latestCandle.getClosePrice());
signalEntity.setStopLoss(computedStopLoss);  // from ATR-based calculation
signalEntity.setTarget(computedTarget);      // from risk-reward ratio
signalEntity.setRiskReward(computedRR);      // target - entry / entry - stopLoss
```

Similarly verify `SwingTradingStrategy` path populates these fields.

**Requires:** Check `PriceActionSignalEngine.generateSignal()` return type — it should return stopLoss/target/riskReward. If not, extend the return type.

---

## Task 7: PaperTradingMonitorService — EOD Position Monitoring

**New file:** `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingMonitorService.java`

Fyers-specific service that runs during market hours (09:15-15:30 IST) to monitor open positions.

```java
@Service
public class PaperTradingMonitorService {
    private final FyersServiceClient fyersClient;
    private final PaperTradingEngine engine;
    private final PaperTradingStateService stateService;
    private final Logger logger = LoggerFactory.getLogger(PaperTradingMonitorService.class);
    
    @Scheduled(cron = "0 15 16 * * MON-FRI", zone = "Asia/Kolkata")
    // Runs at 16:45 IST, after market close (15:30 IST)
    // Uses EOD candle data — same source as EodIngestionScheduler
    public void monitorPositions() {
        List<Position> openPositions = engine.getOpenPositions();
        if (openPositions.isEmpty()) return;
        
        for (Position pos : openPositions) {
            try {
                // Fetch latest candle from Fyers
                OhlcvCandle candle = fetchLatestCandle(pos.getSymbol());
                if (candle == null) continue;
                
                // Update position price
                engine.updatePositionsFromDomain(candle);
                
                // Check SL/TP triggers
                engine.checkPositionTriggers(pos.getSymbol(), candle);
                
                // Check if position was closed by trigger
                if (pos.getStatus() != PositionStatus.OPEN) {
                    logger.info("Position {} closed by SL/TP trigger: {}", pos.getPositionId(), pos.getStatus());
                    stateService.closePosition(convertToEntity(pos));
                    stateService.savePortfolio();
                }
                
                // Persist updated position
                stateService.savePosition(pos);
                
            } catch (Exception e) {
                logger.warn("Failed to monitor position {} for {}: {}", 
                    pos.getPositionId(), pos.getSymbol(), e.getMessage());
                // Best-effort: don't fail the whole job if one symbol fails
            }
        }
        
        // Save portfolio snapshot
        stateService.saveSnapshot();
    }
    
    private OhlcvCandle fetchLatestCandle(String symbol) {
        // Use FyersServiceClient.fetchLatestCandle(symbol)
        // Returns the most recent completed candle
        // Best-effort: handle auth errors, rate limits, empty results
        try {
            return fyersClient.fetchLatestCandle(symbol);
        } catch (AuthenticationException e) {
            logger.error("Fyers auth failed for {}: {}. Token may need refresh.", symbol, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Fyers fetch failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }
}
```

**Scheduling:** Runs at 16:45 IST (after market close at 15:30). Uses EOD candle data — same as `EodIngestionScheduler`. This avoids intraday polling entirely. The EOD candle from Fyers has the day's final price, which is what we need for SL/TP checks.

**Why after market close:** Fyers historical data is EOD-only. Fetching during market hours returns the last completed candle, not live prices. Waiting until after close ensures we get the full day's data. If the user wants intraday monitoring later, they can switch to WebSocket (dormant) or add intraday candle polling.

---

## Task 8: Wire FyersServiceClient into broker module

`FyersServiceClient` is in the `data` module. `PaperTradingMonitorService` is in the `broker` module. The broker module currently depends on `core` but not `data`.

**Option A (recommended):** Add `data` module as a dependency of `broker` module.
- Edit `backend/broker/pom.xml` — add `<dependency> com.swingtrade:data </dependency>`
- This is clean since broker already uses `OhlcvCandle` domain from data module

**Option B:** Inject `MarketDataClientProvider` into `PaperTradingMonitorService` and use the active client. But user confirmed Fyers-specific only, so Option A is simpler.

---

## Task 9: Fix PaperTradingServiceImpl.placeOrder()

**File:** `backend/broker/src/main/java/com/swingtrade/broker/service/PaperTradingServiceImpl.java`

**Current bug (line 40):** `orderManager.createBuyOrder()` hardcodes buy. Ignores `order.getDirection()`.

**Fix:**
```java
@Override
public Order placeOrder(Order order) {
    if (order == null) {
        throw new IllegalArgumentException("Order cannot be null");
    }
    
    Order engineOrder;
    if (order.getDirection() == TradeDirection.LONG) {
        engineOrder = orderManager.createBuyOrder(
            order.getSymbol(), order.getQuantity().intValue(), order.getPrice());
    } else {
        engineOrder = orderManager.createSellOrder(
            order.getSymbol(), order.getQuantity().intValue(), order.getPrice());
    }
    
    engineOrder = paperTradingEngine.executePendingOrder(engineOrder.getOrderId(), order.getPrice());
    stateService.saveOrder(engineOrder);
    return engineOrder;
}
```

**Requires:** `OrderManager` must have `createSellOrder()` method. If not, add it.

---

## Task 10: Portfolio Snapshot Scheduler

**New file:** `backend/broker/src/main/java/com/swingtrade/broker/scheduler/PortfolioSnapshotScheduler.java`

```java
@Service
public class PortfolioSnapshotScheduler {
    private final PaperTradingStateService stateService;
    
    @Autowired
    public PortfolioSnapshotScheduler(PaperTradingStateService stateService) {
        this.stateService = stateService;
    }
    
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Kolkata")
    // Runs at 16:30 IST, same time as EOD ingestion
    public void takeSnapshot() {
        stateService.saveSnapshot();
    }
}
```

---

## Task 11: REST API Updates

Update controllers to use `PaperTradingStateService` for persisted data:

### PositionController
- `GET /api/trading/positions` → use `PaperTradingStateService.getOpenPositions()` → queries DB + engine
- `GET /api/trading/positions/history` → use `PaperTradingStateService.getClosedPositions()` → queries DB
- `POST /api/trading/positions/{symbol}/close` → engine close + stateService close + save

### TradingController
- `GET /api/trading/portfolio` → `stateService.getPortfolio()` → DB + engine
- `GET /api/trading/portfolio/snapshots` → `snapshotRepo.findAllByOrderBySnapshotTimeDesc()`
- `GET /api/trading/orders` → `orderRepo.findAllByOrderByCreatedAtDesc()`

---

## Task 12: Tests

### New tests:
1. `PaperTradingStateServiceTest` — loadState, savePortfolio, savePosition, closePosition, saveSnapshot
2. `PaperTradingMonitorServiceTest` — monitorPositions with mock Fyers responses, SL/TP trigger
3. `PortfolioSnapshotSchedulerTest` — verify cron fires, snapshot saved

### Updated tests:
1. `PaperTradingServiceImplTest` — add sell order test, state persistence verification
2. `PaperTradingEngineTest` — verify stateService callbacks after mutations
3. `PaperTradingServiceImplTest` — verify DB persistence after placeOrder

---

## Task 13: Config

### `backend/broker/src/main/resources/application.properties`
```properties
# Paper trading state persistence
paper.trading.state-persistence.enabled=true
paper.trading.snapshot-cron=0 30 16 * * MON-FRI
paper.trading.monitor-cron=0 15 16 * * MON-FRI
```

### `backend/api/src/main/resources/application-local.properties`
```properties
paper.trading.state-persistence.enabled=true
```

---

## Execution Order

```
Phase 1: Schema + entities
  1. V13 migration (Task 1)
  2. JPA entities (Task 2)
  3. Repositories (Task 2)

Phase 2: Persistence layer
  4. PaperTradingStateService (Task 3)
  5. Wire into PaperTradingEngine (Task 4)
  6. Wire into PaperTradingServiceImpl (Task 5)

Phase 3: Bug fixes
  7. Fix SignalEntity field population (Task 6)
  8. Fix placeOrder() sell handling (Task 9)

Phase 4: Monitoring
  9. Wire FyersClient into broker module (Task 8)
  10. PaperTradingMonitorService (Task 7)
  11. PortfolioSnapshotScheduler (Task 10)

Phase 5: API + tests
  12. REST API updates (Task 11)
  13. Tests (Task 12)
  14. Config (Task 13)
```

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Circular dependency: broker → data | Build failure | broker already uses OhlcvCandle domain from data; clean dependency |
| State drift: engine in-memory vs DB | Inconsistent state | All mutations go through engine → stateService persists. No direct DB writes. |
| Fyers token expiry during monitoring job | Position not updated | Best-effort: catch errors, log clearly, skip that symbol, continue with others |
| Startup load fails if DB has stale data | App won't start | @PostConstruct loadState wraps in try-catch; starts with defaults on failure |
| PortfolioSnapshotScheduler fires before EOD ingestion | Snapshot has stale candle data | Both at 16:30 IST — snapshot runs after ingestion by cron order (30 vs 45 minute) |
| Signal fields still null from strategy engine | SignalExecutionJob uses defaults | Task 6 fixes this; if fields remain null, engine falls back to ATR-based defaults |

---

## Success Criteria

1. V13 migration runs — 5 new tables created
2. PaperTradingStateService loads state from DB on startup
3. PaperTradingStateService persists state after every engine mutation
4. PaperTradingMonitorService fetches Fyers EOD candle, checks SL/TP triggers
5. SignalEntity populated with entryPrice/stopLoss/target/riskReward from PriceActionSignalEngine
6. placeOrder() handles both LONG and SHORT directions
7. Portfolio snapshots saved to DB for P&L charts
8. REST API returns persisted position/order/portfolio data
9. All broker + data module tests pass
10. `mvn clean install` succeeds