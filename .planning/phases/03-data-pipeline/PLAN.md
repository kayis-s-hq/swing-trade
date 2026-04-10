---
phase: 03-data-pipeline
type: summary

# WORKTREE WORKFLOW ENFORCEMENT
# CRITICAL: This plan MUST be executed in a git worktree environment
# Root .planning/ is source of truth - worktree .planning/ is working copy
worktree_enforcement:
  required: true
  reason: "Prevents direct edits to root .planning/ on main branch"
  workflow:
    - step: 1
      action: "Verify worktree directory"
      command: "pwd | grep worktrees"
      fail_message: "ERROR: Must be in a worktree directory (e.g., .claude/worktrees/phase-05/)"
    - step: 2
      action: "Verify worktree branch"
      command: "git branch --show-current"
      expected_pattern: "worktree-phase-.*"
      fail_message: "ERROR: Must be on a worktree branch (e.g., worktree-phase-05)"
    - step: 3
      action: "Edit planning docs in worktree"
      path: ".planning/phases/03-data-pipeline/"
      note: "Do NOT edit .planning/ in root repository"
    - step: 4
      action: "Sync to root before merge"
      command: 'Skill("superpowers:gsd-worktree-workflow --sync-to-root")'
      when: "Before merging worktree branch to main"
    - step: 5
      action: "Verify before merge"
      command: "gsd:verify"
      when: "After plan completion, before merge"

---

# Phase 3: Data Pipeline

**Phase ID:** 03
**Status:** ✅ Complete
**Date:** 2026-03-08

---

## Objective

Implement OHLCV data ingestion and storage for the swing trading system.

---

## Deliverables

| ID | Component | Status | File |
|----|-----------|--------|------|
| 3.1 | MarketDataClient | ✅ Complete | `data/src/main/java/com/swingtrade/data/service/MarketDataClient.java` |
| 3.2 | DataIngestionService | ✅ Complete | `data/src/main/java/com/swingtrade/data/service/DataIngestionService.java` |
| 3.3 | Repository Layer | ✅ Complete | `data/src/main/java/com/swingtrade/data/repository/*` |
| 3.4 | Schema Migrations | ✅ Complete | `data/src/main/resources/db/migration/V*` |
| 3.5 | Scheduling | ✅ Complete | `data/src/main/java/com/swingtrade/data/config/SchedulingConfig.java` |

---

## Implementation Details

### 3.1 MarketDataClient

**Purpose:** External API abstraction for market data.

**Implementation:** UpstoxRestClient

**Endpoints:**
- `POST /v2/login` - Authentication with API key/secret
- `POST /v2/token` - Token refresh
- `GET /v2/market-data/ohlcv` - OHLCV data for symbols
- `GET /v2/instruments` - Instrument list (Nifty 500)
- `GET /v2/market-data/ohlc` - OHLC data

**Features:**
- Automatic token refresh
- Retry logic for failed requests
- Response caching
- Error handling with meaningful exceptions

---

### 3.2 DataIngestionService

**Purpose:** Data fetching, validation, and storage.

**Features:**
- **Daily Ingestion** - Auto-ingests end-of-day OHLCV data
- **Data Quality Validation**
  - Price positivity checks
  - High >= max(open, close)
  - Low <= min(open, close)
  - Volume non-negative
- **Gap Detection** - Identifies missing trading days
- **Gap Repair** - Fetches and fills missing data
- **Nifty 500 Coverage** - Processes all index constituents

**Data Quality Report:**
```java
class DataQualityReport {
    int totalSymbols;
    int successfulIngestions;
    int failedIngestions;
    List<DataGap> gaps;
    List<PriceAnomaly> anomalies;
}
```

---

### 3.3 Repository Layer

**Purpose:** Data persistence with Spring Data JPA.

**Repositories:**
- `OhlcvCandleRepository` - OHLCV data CRUD
- `StockRepository` - Stock entity management
- `SignalRepository` - Signal storage and retrieval
- `SentimentResultRepository` - Sentiment data storage
- `TradeRepository` - Trade history

**Features:**
- Spring Data JPA repositories
- Custom query methods
- Index optimization for symbol + date queries
- Time-range query support

---

### 3.4 Schema Migrations

**Purpose:** Database schema management with Flyway.

**Migrations:**

**V1__swing_trade_schema.sql**
- Core tables (stocks, signals, positions, trades)
- Basic indexing

**V2__create_hypertables.sql**
- TimescaleDB hypertable for ohlcv_candles
- Proper partitioning by time
- Compression for historical data

**V3__create_stocks_table.sql**
- Stocks table with metadata
- Sector and exchange classification
- ISIN and lot size

**V4__add_trades_table.sql**
- Trades table with full lifecycle
- Position references
- P&L tracking

---

### 3.5 Scheduling

**Purpose:** Auto-ingestion scheduling.

**Configuration:** SchedulingConfig

**Schedule:**
```java
@Scheduled(cron = "0 30 16 * * MON-FRI")
```
- **Time:** 16:30 IST (after market close)
- **Days:** Monday-Friday (weekday only)
- **Fallback:** Manual trigger via API

---

## Completion Criteria

- [x] Upstox API integration working
- [x] OHLCV data ingested and stored correctly
- [x] TimescaleDB hypertables created
- [x] Auto-ingestion scheduled (16:30 IST)
- [x] Data quality validation implemented
- [x] Gap detection and repair working
- [x] All repositories implemented

---

## Next Steps

Proceed to **Phase 4: Broker Integration** for paper trading engine implementation.
