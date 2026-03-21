# Phase 03: Data Pipeline

**Phase ID:** 03
**Status:** ✅ Complete
**Date:** 2026-03-22
**Source:** Replanned from context decisions

---

## Phase Boundary

OHLCV data ingestion from external APIs (Upstox, Yahoo Finance), validation, and storage in PostgreSQL/TimescaleDB. Includes scheduled daily ingestion, gap detection, and error handling with Telegram notifications.

---

## Deliverables

| ID | Component | Status | File |
|----|-----------|--------|------|
| 3.1 | MarketDataClient | ✅ Complete | `data/src/main/java/com/swingtrade/data/service/UpstoxRestClient.java` |
| 3.2 | DataIngestionService | ✅ Complete | `data/src/main/java/com/swingtrade/data/service/DataIngestionService.java` |
| 3.3 | Repository Layer | ✅ Complete | `data/src/main/java/com/swingtrade/data/repository/*` |
| 3.4 | Schema Migrations | ✅ Complete | `data/src/main/resources/db/migration/V*` |
| 3.5 | Scheduling | ✅ Complete | `data/src/main/java/com/swingtrade/data/config/SchedulingConfig.java` |

---

## Implementation Decisions (from Context)

### Data Validation Rules
- **Comprehensive checks:** Gap detection, price positivity, price bounds (high >= max(open,close), low <= min(open,close)), volume >= 0, duplicate prevention
- **Simple bounds only:** No statistical outlier detection for anomalies
- **Fail fast:** Abort ingestion on validation failure, do not commit partial data
- **Retry gaps immediately:** When gap detected, retry fetching that specific day before moving to next stock

### Default Stock List
- **Multi-source approach:**
  - Dev/backfill: Yahoo Finance (free, no auth)
  - Paper trading: Upstox v2 free tier
  - Live trading: Zerodha Kite Connect (future)
- **Daily refresh:** Fetch stock list every auto-ingest run
- **Auto-discover:** Compare API list vs DB, add new stocks automatically
- **NSE only:** Focus on Nifty 500 liquid stocks, BSE out of scope

### Error Handling Strategy
- **Exponential backoff:** 3 retries with 1s, 2s, 3s delays
- **Auto-refresh token:** Automatic token refresh on 401/403 errors, cache for 24 hours with 1-minute threshold
- **INFO level logging:** Log each stock processed, summary at end of run
- **Telegram alerts:** Send critical failure notifications via existing TelegramNotificationService

---

## Implementation Details

### 3.1 MarketDataClient (UpstoxRestClient)

**Purpose:** External API abstraction for market data using reactive Spring WebFlux.

**Endpoints:**
- `POST /v2/login` - Authentication with API key/secret
- `POST /v2/token` - Token refresh
- `GET /v2/market-data/ohlcv` - OHLCV data for symbols
- `GET /v2/instruments` - Instrument list (Nifty 500)
- `GET /v2/market-data/ohlc` - OHLC data

**Features:**
- Automatic token refresh (24-hour expiry with 1-minute threshold)
- Exponential backoff retry (3 retries: 1s, 2s, 3s delays)
- Response caching
- Error handling with meaningful exceptions
- WebFlux reactive programming with Mono<T> return types

**File:** `data/src/main/java/com/swingtrade/data/service/UpstoxRestClient.java`

---

### 3.2 DataIngestionService

**Purpose:** Data fetching, validation, and storage with scheduled auto-ingestion.

**Features:**
- **Daily Ingestion** - Auto-ingests end-of-day OHLCV data at 16:30 IST
- **Data Quality Validation**
  - Gap detection (missing trading days)
  - Price positivity checks (all prices > 0)
  - High >= max(open, close)
  - Low <= min(open, close)
  - Volume >= 0
  - Duplicate prevention (skip if candle exists)
- **Gap Retry** - Immediately retry fetching missing days before moving to next stock
- **Nifty 500 Coverage** - Processes all index constituents (NSE only)
- **Fail Fast** - Abort ingestion on validation failure

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

**File:** `data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`

---

### 3.3 Repository Layer

**Purpose:** Data persistence with Spring Data JPA and TimescaleDB optimization.

**Repositories:**
- `OhlcvCandleRepository` - OHLCV data CRUD with date-range queries
- `StockRepository` - Stock entity management with exchange/sector filtering
- `SignalRepository` - Signal storage and retrieval with type filtering
- `SentimentResultRepository` - Sentiment data storage
- `TradeRepository` - Trade history and lifecycle tracking

**Features:**
- Spring Data JPA repositories
- Custom query methods optimized for TimescaleDB
- Index optimization for symbol + date queries
- Time-range query support
- GIN indexes for JSONB fields (strategy_params, broker_response, metadata)

**Files:** `data/src/main/java/com/swingtrade/data/repository/*`

---

### 3.4 Schema Migrations

**Purpose:** Database schema management with Flyway and TimescaleDB hypertables.

**V1__swing_trade_schema.sql**
- Core tables (stocks, signals, positions, trades, ohlcv_candles, sentiment_results)
- Basic indexing
- TimescaleDB extension

**V2__create_hypertables.sql**
- TimescaleDB hypertable for ohlcv_candles (30-day chunks)
- Signal hypertable (7-day chunks)
- Continuous aggregates (daily/weekly/monthly)
- GIN indexes for JSONB fields

**V3__create_stocks_table.sql**
- Enhanced stocks table with 50+ fields
- 30 sector mappings
- Watchlist table
- NSE stock data population

**V4__add_trades_table.sql**
- Comprehensive trades/positions/orders/pnl_summary tables
- 30+ indexes and constraints
- Materialized views (v_active_positions, v_position_summary)
- Triggers for updated_at timestamps

**Files:** `data/src/main/resources/db/migration/V*`

---

### 3.5 Scheduling

**Purpose:** Auto-ingestion scheduling for end-of-day data.

**Configuration:**
```java
@EnableScheduling
public class SchedulingConfig {}
```

**Schedule:**
```java
@Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Kolkata")
public void autoIngestData()
```

- **Time:** 16:30 IST (after market close)
- **Days:** Monday-Friday (weekday only)
- **Fallback:** Manual trigger via API

**File:** `data/src/main/java/com/swingtrade/data/config/SchedulingConfig.java`

---

## Integration Points

| Module | Integration Point |
|--------|------------------|
| **Strategy** | Uses `OhlcvCandleRepository` for technical analysis |
| **Broker** | Reads `PositionRepository` for position management |
| **API** | `DataIngestionService` exposed via REST endpoints |
| **LLM** | `SignalRepository` stores sentiment-enabled signals |

---

## Completion Criteria

- [x] Upstox API integration working with token refresh
- [x] OHLCV data ingested and stored correctly in TimescaleDB
- [x] TimescaleDB hypertables created with proper partitioning
- [x] Auto-ingestion scheduled (16:30 IST, Mon-Fri)
- [x] Data quality validation implemented (comprehensive checks)
- [x] Gap detection and immediate retry working
- [x] All repositories implemented with proper indexes
- [x] INFO level logging for ingestion events
- [x] Telegram alerts configured for critical failures

---

## Verification

**Manual Testing:**
1. Run `docker-compose up -d` to start PostgreSQL and Redis
2. Execute `mvn spring-boot:run -pl data` to start data module
3. Verify logs show:
   - INFO messages for each stock processed
   - Summary at end with total processed, gaps found
   - Telegram notification on critical failures

**Automated Testing (Phase 6/7):**
1. Unit tests for `DataIngestionService` validation methods
2. Integration tests with TestContainers for database operations
3. WireMock tests for Upstox API mocking

---

## Next Steps

Proceed to **Phase 4: Broker Integration** for paper trading engine implementation.

---

*Phase: 03-data-pipeline*
*Plan created: 2026-03-22 (replanned from context)*
