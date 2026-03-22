# Phase 01: Testing Foundation - Context

**Gathered:** 2026-03-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Create comprehensive unit tests for core domain models and strategy module to establish testing foundation. This phase covers:
- Core module: Unit tests for all 6 domain records (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult)
- Strategy module: Unit tests for TechnicalIndicators, DefaultStrategy, DefaultIndicatorService, DefaultBacktestEngine

This phase does NOT cover:
- Integration tests with real database (Phase 2)
- WireMock external API tests (Phase 2)
- API endpoint testing (Phase 3)

</domain>

<decisions>
## Implementation Decisions

### Test Fixtures Location
- Tests fixtures located in same package as production code
- `src/test/java/com/swingtrade/domain/*Fixtures.java` for domain models
- `src/test/java/com/swingtrade/strategy/*Fixtures.java` for strategy tests

### TA4J Test Data Generation
- Factory methods with named patterns (e.g., `bullishTrendSeries()`, `downtrendSeries()`)
- Reusable fixture classes for generating realistic OHLCV patterns
- Known input/output verification for indicator calculations

### Test Lifecycle Annotations
- `@ExtendWith(MockitoExtension.class)` for JUnit 5 + Mockito integration
- `@BeforeEach` methods for test setup
- `assertThat()` from AssertJ for fluent assertions

### Mocking Strategy
- Core module: 100% real objects (no mocking needed for domain records)
- Strategy module: Mock external dependencies (repositories, API clients)
- Domain models and value objects: Use real instances

### Coverage Targets
- Core module: 100% (domain models are the code being tested)
- Strategy module: 85%+

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `OhlcvCandle.of()` factory method - Can be tested and used as fixture helper
- `Position.createWithRisk()` factory method - Test with known ATR values
- `Signal.create()` factory method with confidence clamping - Test normalization logic
- `Signal.Type` enum - Test all signal types (BUY, SELL, HOLD)
- `Position.PositionStatus` enum - Test all statuses (OPEN, CLOSED, STOPPED, TARGET_HIT)

### Established Patterns
- Java records for domain models - Immutable, concise, auto-generated equals/hashCode/toString
- Factory methods for complex object creation - `of()`, `create()` patterns
- BigDecimal for financial calculations - Ensure precision testing
- TA4J library for technical indicators - `BaseSeriesBuilder`, `EMAIndicator`, `RSIIndicator`

### Integration Points
- Core module: No dependencies - Pure domain objects
- Strategy module: Depends on core module and TA4J library
- Test files placed in parallel `src/test` directory structure

### Existing Test Patterns (for reference)
- `SignalServiceTest.java` - Uses `@ExtendWith(MockitoExtension.class)`
- `DataIngestionServiceTest.java` - Uses Mockito for external clients
- AssertJ assertions pattern: `assertThat(result).isEqualTo(expected)`

</code_context>

<specifics>
## Specific Ideas

- Factory pattern for test data: Create `OhlcvCandleFixtures.bullishCandle()`, `OhlcvCandleFixtures.bearishCandle()`
- TA4J series patterns: Create `TechnicalIndicatorsFixtures.bullishTrendSeries()`, `TechnicalIndicatorsFixtures.oversoldSeries()`
- Test method naming: `[method]_[scenario]_[expectedBehavior]` pattern (e.g., `calculateEMA_withSufficientData_returnsValue`)
- Edge case testing: Test NaN returns, null handling, boundary conditions (0, 1, negative values)
- Known value verification: For EMA/RSI/SMA, use simple numeric arrays with expected results
- Immutability testing: Verify records cannot be modified after creation

</specifics>

<deferred>
## Deferred Ideas

- Integration tests with TestContainers - Phase 2
- WireMock for external API mocking - Phase 2
- API endpoint tests with MockMvc - Phase 3
- JaCoCo coverage threshold configuration - Phase 2
- CI/CD pipeline setup - Phase 3
- Regression test suite - Phase 3
- Performance benchmarks for indicator calculations - Future phase

</deferred>

---

*Phase: 01-testing-foundation*
*Context gathered: 2026-03-07*
