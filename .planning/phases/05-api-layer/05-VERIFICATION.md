---
phase: 05-api-layer
verified: 2026-03-22T23:55:00Z
status: gaps_found
score: 3/6 must-haves verified
gaps:
  - truth: "POST /api/trades endpoint creates new trading positions"
    status: failed
    reason: "TradingController calls positionService.createPosition() which does not exist. PositionService also has type mismatch: returns Position instead of PositionResponse."
    artifacts:
      - path: "api/src/main/java/com/swingtrade/api/controller/TradingController.java"
        issue: "Line 48: calls non-existent createPosition(request) method"
      - path: "api/src/main/java/com/swingtrade/api/PositionService.java"
        issue: "Missing createPosition() method; return types mismatch (Position vs PositionResponse)"
    missing:
      - "PositionService.createPosition(TradeRequest) method"
      - "Type conversion from Position to PositionResponse"

  - truth: "GET /api/portfolio returns portfolio overview and performance metrics"
    status: failed
    reason: "TradingController.getPortfolioPerformance() calls performanceService.getPortfolioPerformance() but PerformanceService only has getPerformanceStats()."
    artifacts:
      - path: "api/src/main/java/com/swingtrade/api/controller/TradingController.java"
        issue: "Line 173: calls non-existent getPortfolioPerformance() method"
      - path: "api/src/main/java/com/swingtrade/api/PerformanceService.java"
        issue: "Method is getPerformanceStats(), not getPortfolioPerformance()"
    missing:
      - "Rename or create getPortfolioPerformance() in PerformanceService"
      - "Return PerformanceResponse instead of PerformanceStats"

  - truth: "POST /api/scan triggers manual market scan and returns results"
    status: failed
    reason: "SignalController calls scanService.triggerScan(request) but ScanService only has triggerManualScan() with no request parameter. Also return type mismatch: ScanService.triggerManualScan() returns ScanResult, controller expects ScanResponse."
    artifacts:
      - path: "api/src/main/java/com/swingtrade/api/controller/SignalController.java"
        issue: "Line 191: calls non-existent triggerScan(request) method with ScanRequest parameter"
      - path: "api/src/main/java/com/swingtrade/api/ScanService.java"
        issue: "triggerManualScan() has no parameters; returns ScanResult not ScanResponse"
    missing:
      - "ScanService.triggerScan(ScanRequest request) method or parameter support"
      - "Convert ScanResult to ScanResponse return type"

  - truth: "GET /api/scan/history retrieves past scan results"
    status: failed
    reason: "SignalController calls scanService.getScanHistory() expecting List<ScanResponse> but ScanService returns List<ScanResult> and returns empty list."
    artifacts:
      - path: "api/src/main/java/com/swingtrade/api/controller/SignalController.java"
        issue: "Line 218: expects List<ScanResponse> return type"
      - path: "api/src/main/java/com/swingtrade/api/ScanService.java"
        issue: "getScanHistory() returns List<ScanResult>, currently empty (line 66)"
    missing:
      - "Convert ScanResult to ScanResponse"
      - "Implement actual scan history retrieval instead of empty list"

  - truth: "Query parameter filtering works for signal retrieval (signalType, minConfidence, date, limit)"
    status: failed
    reason: "SignalController.getSignalsByDateRange() calls service with (startDate, endDate) but SignalService.getSignalsByDateRange() requires (startDate, endDate, signalType). Also getSignalsByType(type) has parameter type mismatch."
    artifacts:
      - path: "api/src/main/java/com/swingtrade/api/controller/SignalController.java"
        issue: "Line 87: calls getSignalsByDateRange(startDate, endDate) missing signalType"
      - path: "api/src/main/java/com/swingtrade/api/SignalService.java"
        issue: "Line 60: getSignalsByDateRange requires signalType parameter; getSignalsByType expects String not SignalType enum"
    missing:
      - "Add signalType parameter to SignalController.getSignalsByDateRange() call"
      - "Fix type conversion SignalResponse.SignalType to String in controller"
---

# Phase 05: API Layer Verification Report

**Phase Goal:** Implement REST API endpoints for system interaction and monitoring.

**Verified:** 2026-03-22T23:55:00Z

**Status:** GAPS FOUND

**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth   | Status     | Evidence       |
| --- | ------- | ---------- | -------------- |
| 1   | REST API server starts and responds to health check requests | ✓ VERIFIED | HealthController fully implemented with /api/health, /api/health/details, /api/health/database, /api/health/market-data, /api/health/llm, /api/health/full endpoints |
| 2   | POST /api/trades endpoint creates new trading positions | ✗ FAILED | TradingController calls non-existent positionService.createPosition(); PositionService.getOpenPositions() returns Position not PositionResponse |
| 3   | GET /api/portfolio returns portfolio overview and performance metrics | ✗ FAILED | TradingController calls non-existent performanceService.getPortfolioPerformance(); PerformanceService only has getPerformanceStats() |
| 4   | GET /api/positions lists all positions with filtering | ⚠️ PARTIAL | PositionController has endpoints but underlying PositionService returns Position objects, controller expects PositionResponse DTOs |
| 5   | GET /api/signals retrieves signals with query parameter filtering | ✗ FAILED | SignalService.getSignalsByDateRange requires signalType parameter not provided by controller; getSignalsByType type mismatch (String vs SignalType enum) |
| 6   | POST /api/scan triggers manual market scan and GET /api/scan/history retrieves results | ✗ FAILED | ScanService.triggerScan(request) does not exist; scanService.getScanHistory() returns ScanResult not ScanResponse |

**Score:** 1/6 truths fully verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | ----------- | ------ | ------- |
| `TradingController` | REST controller with /api/trades endpoints | ⚠️ ORPHANED | File exists with proper decorators, but calls non-existent service methods |
| `SignalController` | REST controller with /api/signals endpoints | ⚠️ ORPHANED | File exists with comprehensive endpoints, but service method signatures don't match calls |
| `PositionController` | REST controller with /api/positions endpoints | ⚠️ ORPHANED | File exists and well-structured, but PositionService incompatible (returns domain objects not DTOs) |
| `PerformanceService` | Service for performance calculations | ✗ STUB | Only has getPerformanceStats(); missing getPortfolioPerformance() |
| `ScanService` | Service for market scanning | ✗ STUB | triggerScan(request) missing; triggerManualScan() takes no parameters; getScanHistory() returns empty list |
| `DTOs` | Request/response objects for all endpoints | ✓ VERIFIED | All required DTOs properly defined: SignalResponse, PositionResponse, PerformanceResponse, ScanResponse, TradeRequest, TradeResponse, etc. |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| TradingController | PositionService | positionService.createPosition() | NOT_WIRED | Method does not exist |
| TradingController | PerformanceService | performanceService.getPortfolioPerformance() | NOT_WIRED | Method does not exist (actual: getPerformanceStats) |
| SignalController | ScanService | scanService.triggerScan(request) | NOT_WIRED | Method does not exist (actual: triggerManualScan) |
| SignalController | ScanService | scanService.getScanHistory() | PARTIAL | Method exists but returns ScanResult not ScanResponse, returns empty list |
| SignalController | SignalService | signalService.getSignalsByDateRange() | PARTIAL | Signature mismatch: call omits required signalType parameter |
| SignalController | SignalService | signalService.getSignalsByType() | PARTIAL | Parameter type mismatch: expects String, receives SignalResponse.SignalType enum |
| PositionController | PositionService | positionService.getOpenPositions() | PARTIAL | Method exists but return type mismatch (Position vs PositionResponse) |
| HealthController | DataIngestionService/StrategyService/LlmService | Injected services | ✓ WIRED | All optional dependencies properly injected with required=false |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Health endpoint accessible | `curl -s http://localhost:8080/api/health` | Not tested (server not running) | ? SKIP |
| Trade creation payload valid | JSON validation of TradeRequest DTO | All request DTOs properly defined | ✓ PASS (schema) |
| Error responses structured | ErrorResponse DTO exists and used | ErrorResponse DTO defined (lines 1-2625 of ErrorResponse.java) | ✓ PASS (schema) |
| HTTP status codes implemented | Grep for ResponseEntity with status | All controllers use appropriate status codes (201 for CREATE, 404 for NOT FOUND, 500 for errors) | ✓ PASS (pattern) |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| ScanService.java | 66 | `return new ArrayList<>()` in getScanHistory() | 🛑 BLOCKER | Always returns empty list; scan history never populated |
| PositionService.java | 106-109 | `getCurrentPrice()` returns hardcoded 100.0 | ⚠️ WARNING | P&L calculations use static price, never real data |
| SignalService.java | 114 | `getTechnicalAnalysis()` returns placeholder with empty indicators list | ⚠️ WARNING | Technical analysis data never populated |
| SignalService.java | 125 | `getSentimentAnalysis()` returns hardcoded "NEUTRAL" sentiment | ⚠️ WARNING | Sentiment analysis never uses real LLM data |
| PerformanceService.java | 93 | `calculateSharpeRatio()` returns hardcoded 1.0 | ⚠️ WARNING | Performance metrics hardcoded, not calculated |
| PerformanceService.java | 102 | `calculateMaxDrawdown()` returns hardcoded 5.0 | ⚠️ WARNING | Max drawdown never calculated from actual data |
| TradingController.java | 206-212 | `buildErrorResponse()` misuses PositionResponse for error wrapping | ⚠️ WARNING | Error handling uses domain DTO for error details (anti-pattern) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| REST API endpoints functional | PLAN.md | Endpoints must be callable and return valid responses | ✗ BLOCKED | Multiple method signature mismatches prevent compilation/runtime success |
| Request/Response DTOs properly defined | PLAN.md | All DTOs must be properly structured | ✓ SATISFIED | All required DTOs exist with proper structure and getters/setters |
| Error handling with HTTP status codes | PLAN.md | Appropriate error responses with status codes | ⚠️ PARTIAL | Controllers implement error handling, but some use wrong DTOs for errors |
| Query parameter filtering working | PLAN.md | Filtering by signalType, minConfidence, date, limit | ✗ BLOCKED | Service method signatures don't match controller calls |
| Performance metrics accurate | PLAN.md | Metrics must use actual data, not hardcoded values | ✗ BLOCKED | All performance calculations return hardcoded placeholder values |
| Scan functionality working | PLAN.md | Scan endpoint triggers and returns results | ✗ BLOCKED | ScanService methods missing or incompatible with controller calls |
| API documentation complete | PLAN.md | API docs must exist and be accurate | ⚠️ PARTIAL | PLAN.md documents endpoints, but actual implementation deviates significantly |

### Gaps Summary

The Phase 05 API Layer implementation has **critical wiring failures** preventing goal achievement:

**Root Causes:**

1. **Service Interface Mismatch:** Controllers call methods that don't exist on services:
   - `positionService.createPosition()` — does not exist
   - `performanceService.getPortfolioPerformance()` — exists as `getPerformanceStats()`
   - `scanService.triggerScan(request)` — exists as `triggerManualScan()`

2. **Type Incompatibility:** Services return domain objects while controllers expect DTOs:
   - PositionService returns `Position` objects; controllers expect `PositionResponse` DTOs
   - ScanService returns `ScanResult`; controller expects `ScanResponse`

3. **Method Signature Mismatches:** Controllers call with wrong parameters:
   - `getSignalsByDateRange(start, end)` called with 2 params; method needs 3 (includes signalType)
   - `getSignalsByType(enum)` called with enum; method expects String

4. **Stub Implementations:** Services have placeholder implementations:
   - `ScanService.getScanHistory()` returns empty `new ArrayList<>()`
   - `PerformanceService` metrics hardcoded (Sharpe=1.0, MaxDD=5.0)
   - `SignalService.getTechnicalAnalysis()` and `getSentimentAnalysis()` return empty/neutral placeholders
   - `PositionService.getCurrentPrice()` hardcoded to 100.0

**Impact on Goal:**

- REST endpoints exist but **cannot compile** due to method signature mismatches
- **No functional API** until services match controller expectations
- **No real data flows** through scan, performance, or sentiment endpoints due to stubs
- **Error handling inconsistent** (uses PositionResponse for errors instead of ErrorResponse)

**What Would Be Needed for Goal Achievement:**

1. **Immediate fixes** (for compilation):
   - Add `createPosition(TradeRequest)` to PositionService
   - Rename/create `getPortfolioPerformance()` in PerformanceService
   - Add `triggerScan(ScanRequest)` to ScanService
   - Add type converters: Position→PositionResponse, ScanResult→ScanResponse
   - Fix SignalService method signatures

2. **Data flow fixes** (for functionality):
   - Populate `getScanHistory()` from database instead of empty list
   - Implement real performance metric calculations (Sharpe, Max Drawdown, etc.)
   - Implement real technical/sentiment analysis data retrieval
   - Fetch current prices from market data instead of hardcoded values

3. **Type safety fixes**:
   - Use proper error response DTOs (ErrorResponse, not PositionResponse)
   - Ensure all controllers return DTOs, not domain objects
   - Match parameter types between controllers and services

---

_Verified: 2026-03-22T23:55:00Z_
_Verifier: Claude (gsd-verifier)_
