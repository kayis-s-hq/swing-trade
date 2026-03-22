# Phase 5: API Layer

**Phase ID:** 05
**Status:** ✅ Complete
**Date:** 2026-03-08

---

## Objective

Implement REST API endpoints for system interaction and monitoring.

---

## Deliverables

| ID | Component | Status | File |
|----|-----------|--------|------|
| 5.1 | TradingController | ✅ Complete | `api/src/main/java/com/swingtrade/api/controller/TradingController.java` |
| 5.2 | SignalController | ✅ Complete | `api/src/main/java/com/swingtrade/api/controller/SignalController.java` |
| 5.3 | PositionController | ✅ Complete | `api/src/main/java/com/swingtrade/api/controller/PositionController.java` |
| 5.4 | PerformanceService | ✅ Complete | `api/src/main/java/com/swingtrade/api/PerformanceService.java` |
| 5.5 | ScanService | ✅ Complete | `api/src/main/java/com/swingtrade/api/ScanService.java` |
| 5.6 | DTOs | ✅ Complete | `api/src/main/java/com/swingtrade/api/dto/*` |

---

## Implementation Details

### 5.1 TradingController

**Purpose:** Trade execution and portfolio management endpoints.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/trades | Place new trade order |
| GET | /api/portfolio | Get current portfolio overview |
| GET | /api/positions | List all positions (open + closed) |

**Request DTOs:**
- `TradeRequest` - symbol, quantity, entryReason

**Response DTOs:**
- `OrderResponse` - Order confirmation with status
- `PortfolioResponse` - Total value, P&L, positions count

---

### 5.2 SignalController

**Purpose:** Trading signal retrieval and filtering.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/signals/latest | Get latest signals (all symbols) |
| GET | /api/signals/{symbol} | Get signals for specific symbol |
| GET | /api/signals | List all signals with filters |

**Query Parameters:**
- `signalType` - Filter by BUY/SELL/HOLD
- `minConfidence` - Minimum confidence threshold (0.0-1.0)
- `date` - Filter by date
- `limit` - Maximum results (default 50)

**Response DTO:**
- `SignalResponse` - Signal details with risk parameters

---

### 5.3 PositionController

**Purpose:** Position management and status tracking.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/positions | Get open positions |
| GET | /api/positions/{id} | Get position details |
| POST | /api/positions/{id}/close | Close position manually |

**Response DTO:**
- `PositionResponse` - Position details with P&L calculations

---

### 5.4 PerformanceService

**Purpose:** Trading performance metrics and analytics.

**Metrics:**
- Total P&L (realized + unrealized)
- Win rate
- Total trades
- Winning trades / Losing trades
- Average win / Average loss
- Profit factor
- Max drawdown
- Sharpe ratio (annualized)

**Response DTO:**
- `PerformanceResponse` - All performance metrics

---

### 5.5 ScanService

**Purpose:** Signal scanning and manual trigger.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/scan | Get current scan results |
| POST | /api/scan | Trigger manual signal generation |

**Scan Results:**
- Signals for all Nifty 500 stocks
- Signal distribution (BUY/SELL/HOLD counts)
- Confidence distribution
- Sector breakdown

**Response DTO:**
- `ScanResponse` - Scan results with statistics

---

### 5.6 DTOs

**Purpose:** Request and response object definitions.

**Request DTOs:**
- `TradeRequest` - symbol, quantity, entryReason

**Response DTOs:**
- `SignalResponse` - Complete signal with risk parameters
- `PositionResponse` - Position with P&L calculations
- `PerformanceResponse` - All performance metrics
- `ScanResponse` - Scan results with statistics
- `OrderResponse` - Order confirmation

---

## Completion Criteria

- [x] All REST endpoints functional
- [x] Request/Response DTOs properly defined
- [x] Error handling with appropriate HTTP status codes
- [x] Query parameter filtering working
- [x] Performance metrics accurate
- [x] Scan functionality working
- [x] API documentation complete

---

## API Documentation

**Base URL:** `http://localhost:8080/api`

**Error Response Format:**
```json
{
  "timestamp": "2026-03-08T17:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Position limit exceeded",
  "path": "/api/trades"
}
```

---

## Next Steps

Proceed to **Phase 6: Testing Foundation** for unit test implementation.
