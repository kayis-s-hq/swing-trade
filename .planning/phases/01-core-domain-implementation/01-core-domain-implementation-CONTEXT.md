# Phase 1: Core Domain Implementation - Context

**Gathered:** 2026-03-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Create comprehensive unit tests for all 6 core domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult) with 100% code coverage. Tests validate object creation, business logic (P&L calculations), status checks, and factory methods.

</domain>

<decisions>
## Implementation Decisions

### Test Data Strategy
- Hardcoded values for test data in test methods
- Shared test fixtures in a common TestFixtures class
- Fixtures provide builder-style helpers for each domain model

### Validation Testing
- Include negative tests for all validation scenarios
- Test Signal confidence normalization (clamped to 0-1)
- Test P&L calculation accuracy for Position and Trade
- Test status transitions for trades and positions
- Test all business logic comprehensively

### Business Logic Coverage
- All methods tested: factory methods, P&L calculations, status checks
- Factory methods: Signal.create(), Position.createWithRisk(), Trade.open(), Trade.close()
- P&L methods: Position.calculateUnrealizedPnL(), calculatePnLPercent(), Trade.totalPnL
- Status checks: isOpen(), isClosed(), isProfitable(), isLoss()

### Assertion Style
- AssertJ for fluent assertions
- Standard pattern: `assertThat(result).isEqualTo(expected)`

### Claude's Discretion
- Test method naming conventions
- Exact test data values (as long as they're realistic)
- Test class structure and organization
- Order of test methods within classes

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Stock.java** - Domain record with Exchange and Sector enums
- **OhlcvCandle.java** - Domain record with getRange(), getChangePercent(), isBullish(), isBearish()
- **Signal.java** - Domain record with SignalType enum, create() factory, isBuySignal()/isSellSignal()/isHoldSignal()
- **Position.java** - Domain record with PositionStatus enum, createWithRisk() factory, P&L calculation methods
- **Trade.java** - Domain record with TradeStatus enum, open()/close() factories, isProfitable()/isLoss()
- **SentimentResult.java** - Domain record (to be reviewed)

### Established Patterns
- All domain models are Java records (immutable)
- Factory methods use static `create()`, `open()`, `close()`, `createWithRisk()` patterns
- Enums have display names via constructor
- BigDecimal used for all monetary values
- LocalDate used for all date fields

### Integration Points
- Tests live in `core/src/test/java/com/swingtrade/domain/`
- Shared TestFixtures in `core/src/test/java/com/swingtrade/domain/test/`
- No external dependencies (pure domain logic)

</code_context>

<specifics>
## Specific Ideas

- Use AssertJ's `assertThat()` with fluent assertions
- Shared TestFixtures class with static builder methods
- Hardcoded realistic values (e.g., RELIANCE symbol, NSE exchange, ₹1000 entry price)
- Test edge cases: zero quantities, boundary confidence values (0.0, 1.0), null handling

</specifics>

<deferred>
## Deferred Ideas

- Integration tests with database - Phase 7
- WireMock for external API testing - Phase 7
- API endpoint testing - Phase 8
- Code coverage thresholds - Phase 7

</deferred>

---

*Phase: 01-core-domain-implementation*
*Context gathered: 2026-03-08*
