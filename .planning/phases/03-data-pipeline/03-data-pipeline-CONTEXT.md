# Phase 3: Data Pipeline - Context

**Gathered:** 2026-03-22
**Status:** Ready for planning

<domain>
## Phase Boundary

OHLCV data ingestion from external APIs (Upstox, Yahoo Finance), validation, and storage in PostgreSQL/TimescaleDB. Includes scheduled daily ingestion, gap detection, and error handling with Telegram notifications.

</domain>

<decisions>
## Implementation Decisions

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

### Claude's Discretion
- Exact log message formats
- Specific error message wording
- Telegram notification template design
- DataQualityReport structure details

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **UpstoxRestClient.java** - API client with retry logic, token caching, reactive WebFlux
- **DataIngestionService.java** - Scheduled ingestion with @Scheduled at 16:30 IST
- **DataQualityReport** - Validation reporting nested class
- **OhlcvCandleRepository.java** - TimescaleDB-optimized queries
- **TelegramNotificationService.java** - Existing notification service for alerts

### Established Patterns
- **Reactive Programming:** WebFlux with Mono<T> return types
- **Transaction Management:** @Transactional for database operations
- **DateTime Handling:** ZoneId.of("Asia/Kolkata") for IST timezone
- **BigDecimal Precision:** NUMERIC(15,4) in DB, BigDecimal in Java

### Integration Points
- **Database:** PostgreSQL + TimescaleDB hypertables (V2 migration)
- **Strategy:** OhlcvCandleRepository provides data for technical analysis
- **Broker:** PositionRepository for position management
- **API:** DataIngestionService exposed via REST endpoints

</code_context>

<specifics>
## Specific Ideas

- TimescaleDB hypertable partitioned by date
- EOD ingestion: Spring @Scheduled job at 16:30 IST weekdays
- Use existing NIFTY_500_STOCKS constant as fallback
- GIN indexes for JSONB fields (already in V2 migration)

</specifics>

<deferred>
## Deferred Ideas

- BSE stock coverage - separate phase
- Zerodha Kite Connect integration - future phase
- Statistical outlier detection - could be enhancement
- Advanced price anomaly detection - future enhancement
- Manual review queue for anomalies - future enhancement

</deferred>

---

*Phase: 03-data-pipeline*
*Context gathered: 2026-03-22*
