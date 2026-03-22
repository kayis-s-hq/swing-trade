# Phase 8: API Endpoint Testing - Context

**Gathered:** 2026-03-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Create comprehensive API endpoint tests for the SwingTrade REST API. Test all endpoints across Trading, Signal, Position, Health, and Scan controllers with 100% coverage including happy paths and error cases.

</domain>

<decisions>
## Implementation Decisions

### Test coverage
- 100% endpoint coverage required
- All paths must be tested (success, failure, edge cases)
- Error responses validated (400, 404, 409, 500)

### Test framework
- @SpringBootTest + MockMvc for full integration testing
- Consistent with existing test patterns in the codebase

### External API mocking
- MockRestServiceServer for Upstox API mocking
- MockRestServiceServer for vLLM endpoint mocking
- NOT WireMock (project decision: lighter footprint, Spring-native)

### Test data strategy
- @Testcontainers with real PostgreSQL + TimescaleDB
- Real database containers for accurate integration testing
- Schema migrations applied via Flyway

### Claude's Discretion
- Exact test class organization
- Test data fixture design
- Assertion library choices (Hamcrest vs AssertJ)
- Test naming conventions

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **BaseWebMvcTest.java** (`api/src/test/java/com/swingtrade/api/test/integration/BaseWebMvcTest.java`) — Base test class with @SpringBootTest, MockMvc setup, test configuration
- **Existing controller tests** — SignalControllerTest, TradingControllerTest, PositionControllerTest, PerformanceControllerTest, ScanControllerTest, ErrorHandlingTest — patterns to follow/extend
- **HealthController** — Already has health endpoints (/api/health, /details, /database, /market-data, /llm, /full) — test coverage needed

### Established Patterns
- **REST API structure:**
  - `/api/trades` — Trading operations (POST create, GET list, GET by symbol, POST close, GET history, GET performance, GET risk-summary)
  - `/api/signals` — Signal operations (GET latest, GET by symbol/type/date-range, GET high-confidence, POST generate, POST scan, GET scan/history, GET analysis/sentiment/combined)
  - `/api/positions` — Position operations (GET list, GET by symbol/status/sector, GET stats/allocation, POST close)
  - `/api/health` — Health checks (GET basic/details/database/market-data/llm/full)
- **Response patterns:** ResponseEntity with status codes, error wrappers, pagination support
- **DTOs:** PositionResponse, SignalResponse, ScanResponse, PerformanceResponse, HealthStatus

### Integration Points
- **Services:** PositionService, SignalService, ScanService, PerformanceService
- **External APIs:** UpstoxRestClient (market data), VLLMClient (sentiment)
- **Database:** OhlcvCandleRepository, StockRepository, SignalRepository, PositionRepository, TradeRepository

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard Spring Boot testing approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 08-api-endpoint-testing*
*Context gathered: 2026-03-22*
