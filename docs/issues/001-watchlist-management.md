# feat(api): add watchlist management endpoints

**Labels:** `enhancement` `tier-1-backend` `api`
**Estimated effort:** 2-3 days

## Problem

There is no way to manage a watchlist of stocks to monitor. The system can generate signals for any symbol, but users cannot curate a list of stocks they care about and filter signals by watchlist membership.

## Proposed Solution

Add CRUD endpoints for a watchlist feature, backed by a new database table.

## API Endpoints

```
POST   /api/watchlist          - Add a stock to watchlist
GET    /api/watchlist          - Get all watched stocks (paginated)
GET    /api/watchlist/{symbol} - Get watchlist status for a symbol
DELETE /api/watchlist/{symbol} - Remove stock from watchlist
GET    /api/watchlist/signals  - Get signals only for watched stocks
```

## Database Schema

```sql
CREATE TABLE watchlist (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(50) DEFAULT 'system',
    notes TEXT,
    UNIQUE(symbol, user_id)
);

CREATE INDEX idx_watchlist_symbol ON watchlist(symbol);
CREATE INDEX idx_watchlist_user ON watchlist(user_id);
```

## Files to Create/Modify

### New files
- `api/src/main/java/com/swingtrade/api/entity/WatchlistEntity.java` - JPA entity
- `api/src/main/java/com/swingtrade/api/repository/WatchlistRepository.java` - Spring Data repository
- `api/src/main/java/com/swingtrade/api/dto/WatchlistAddRequest.java` - Request DTO
- `api/src/main/java/com/swingtrade/api/dto/WatchlistItemResponse.java` - Response DTO
- `api/src/main/java/com/swingtrade/api/service/WatchlistService.java` - Business logic
- `api/src/main/java/com/swingtrade/api/controller/WatchlistController.java` - REST controller
- `data/src/main/resources/db/migration/V6__create_watchlist_table.sql` - Flyway migration

### Modified files
- `api/src/main/java/com/swingtrade/api/SignalService.java` - Add `getWatchlistSignals()` method

## Acceptance Criteria

- [ ] Watchlist table created with Flyway migration V6
- [ ] Entity has symbol, added_at, user_id, notes fields
- [ ] POST /api/watchlist validates symbol exists in stocks table before adding
- [ ] POST returns 409 Conflict if symbol already in watchlist
- [ ] GET /api/watchlist returns paginated list of watched stocks
- [ ] DELETE /api/watchlist/{symbol} returns 204 on success, 404 if not found
- [ ] GET /api/watchlist/signals returns signals filtered to only watched symbols
- [ ] All endpoints have unit tests
- [ ] Integration test verifies end-to-end watchlist flow
- [ ] Code coverage >= 80%

## Notes

- Symbol validation should check against the `stocks` table to prevent adding invalid tickers
- Default user_id is "system" for single-user mode
- Consider adding a `last_checked_at` field later for tracking when signals were last generated per watchlist item
- Use `PaginatedResponse<T>` pattern consistent with existing `PositionController`
