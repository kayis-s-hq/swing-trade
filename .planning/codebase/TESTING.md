# Testing Patterns

**Analysis Date:** 2026-03-07

## Test Framework

### Test Runner
- **Framework:** JUnit 5 (Jupiter)
- **Version:** 5.11.0
- **Configuration:** Maven Surefire Plugin 3.3.0
- **Config File:** No separate config - uses Maven default configuration

### Assertion Library
- **Library:** AssertJ Core (version 3.26.3)
- **Usage:** Fluent assertions throughout test suite
- **Key patterns:** `assertThat()`, `assertEquals()`, `assertNotNull()`, `assertTrue()`, `assertFalse()`, `assertThrows()`

### Mocking Framework
- **Framework:** Mockito (version 5.12.0)
- **Extension:** Mockito JUnit 5 Extension (`@ExtendWith(MockitoExtension.class)`)
- **Spring Mocking:** Spring Extension for Spring beans (`@ExtendWith(SpringExtension.class)`)

### WireMock
- **Purpose:** HTTP client testing for external API integrations
- **Version:** 3.8.0
- **Used in:** `llm` and `api` modules

### Test Containers
- **Purpose:** Integration testing with PostgreSQL
- **Dependencies:**
  - `testcontainers:postgresql`
  - `testcontainers:junit-jupiter`
  - `testcontainers:testcontainers`
- **Used in:** `data` module

### Test Commands

```bash
# Run all tests
mvn test

# Run tests with coverage
mvn test -Pcoverage

# Run a specific test class
mvn test -Dtest=DataIngestionServiceTest

# Run a specific test method
mvn test -Dtest=DataIngestionServiceTest#testAutoIngestData

# Run tests in watch mode
mvn test -DfailIfNoTests=false

# View coverage report
mvn jacoco:report
open target/site/jacoco/index.html
```

## Test File Organization

### Location
Tests are co-located with production code in parallel directory structure:
```
src/main/java/com/swingtrade/module/
src/test/java/com/swingtrade/module/
```

### Directory Structure per Module
```
module/
├── src/main/java/com/swingtrade/module/
│   ├── ... (production code)
└── src/test/java/com/swingtrade/module/
    ├── ... (test classes)
    ├── service/      # Service layer tests
    ├── controller/   # Controller tests (api module)
    └── engine/       # Engine tests (broker module)
```

### File Naming
- **Test class names:** `[ProductionClass]Test.java` (e.g., `SignalServiceTest.java`, `PaperTradeEngineTest.java`)
- **Integration tests:** `[Module]IntegrationTest.java` (e.g., `BrokerModuleIntegrationTest.java`)
- **Module tests:** `[Module]ModuleTest.java` (e.g., `LlmModuleTest.java`)

### Test Structure Pattern
```java
/**
 * Comprehensive test class for [Component Name].
 * Tests [description of functionality].
 */
@ExtendWith(MockitoExtension.class)
public class [ComponentName]Test {

    private [DependencyType] dependency;
    private [ComponentUnderTest] componentUnderTest;

    @BeforeEach
    void setUp() {
        // Initialize dependencies and component
    }

    @Test
    void testMethodName_ShouldExpectedBehavior() {
        // Given - setup test data
        // When - execute method under test
        // Then - verify results
    }
}
```

## Test Structure

### Setup Pattern
Tests use `@BeforeEach` methods for test preparation:

```java
@BeforeEach
void setUp() {
    upstoxConfig = new UpstoxConfig();
    upstoxConfig.setApiUrl("https://api.upstox.com/v2");
    upstoxConfig.setAccessToken("test_token");

    upstoxRestClient = Mockito.mock(UpstoxRestClient.class);
    dataIngestionService = new DataIngestionService(upstoxRestClient);
}
```

### Test Method Naming Convention
Pattern: `[methodName]_[scenario]_[expectedBehavior]`

**Examples:**
- `testPlaceOrder_ValidOrder_ShouldSucceed`
- `testPlaceOrder_InvalidOrder_ShouldThrowException`
- `testGetPortfolio_ShouldReturnPortfolio`
- `testCalculateProfitLoss_ValidPosition_ShouldReturnCorrectValue`

### Test Organization
Tests are grouped by feature within a single class. Related tests share common setup and test data.

**Example from `SignalServiceTest.java`:**
```java
/**
 * Comprehensive unit tests for SignalService
 * Tests signal management functionality including retrieval and filtering
 */
@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @InjectMocks
    private SignalService signalService;

    private List<Signal> testSignals;

    @BeforeEach
    void setUp() {
        // Set up test data
        testSignals = new ArrayList<>();
        testSignals.add(new Signal("AAPL", "BUY", 150.0, LocalDateTime.now(), "Breakout above resistance"));
        // ... more test signals
    }

    @Test
    void testGetLatestSignals_ReturnsNonEmptyList() {
        // Act
        List<Signal> signals = signalService.getLatestSignals();

        // Assert
        assertNotNull(signals, "Signals list should not be null");
        assertFalse(signals.isEmpty(), "Signals list should not be empty");
        assertEquals(2, signals.size(), "Should return exactly 2 signals");
    }
}
```

### Test Types per Module

| Module | Unit Tests | Integration Tests |
|--------|-----------|-------------------|
| `api` | SignalServiceTest, SwingTradeControllerTest | None |
| `broker` | PaperTradeEngineTest | BrokerModuleIntegrationTest |
| `data` | DataIngestionServiceTest | None (uses TestContainers) |
| `llm` | LlmModuleTest | None |
| `strategy` | None | None |
| `core` | None | None |

## Mocking

### Mockito Usage Patterns

**Mocking External Dependencies:**
```java
private UpstoxRestClient upstoxRestClient;
private DataIngestionService dataIngestionService;

@BeforeEach
void setUp() {
    upstoxRestClient = Mockito.mock(UpstoxRestClient.class);
    dataIngestionService = new DataIngestionService(upstoxRestClient);
}

@Test
void testAutoIngestData() {
    verifyNoInteractions(upstoxRestClient);
}
```

**Injecting Mocks into Spring Beans:**
```java
@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @InjectMocks
    private SignalService signalService;
}
```

**Verifying Method Calls:**
```java
@Test
void testBackfillStockData() {
    dataIngestionService.backfillStockData("RELIANCE", fromDate, toDate);
    verifyNoInteractions(upstoxRestClient);
}
```

**MockitoAnnotations:**
- Use `@ExtendWith(MockitoExtension.class)` for JUnit 5
- `@InjectMocks` automatically creates and injects dependencies

### What to Mock
- External API clients (`UpstoxRestClient`, `MarketDataClient`)
- Repository interfaces (`OhlcvCandleRepository`, `StockRepository`)
- Database access and persistence layers
- Third-party service integrations

### What NOT to Mock
- The class under test (mock `paperTradeEngine`, not tests of `PaperTradeEngine`)
- Domain models and value objects (use real instances)
- Configuration objects (use actual or test-specific instances)

**Example of NOT mocking domain models:**
```java
@Test
void testCalculateProfitLoss_ValidPosition_ShouldReturnCorrectValue() {
    // Use real Position object
    Position position = new Position(
        "pos_1",
        "AAPL",
        TradeDirection.LONG,
        new BigDecimal("100"),
        new BigDecimal("150.00"),
        new BigDecimal("142.50"),
        new BigDecimal("165.00")
    );
    position.setCurrentPrice(new BigDecimal("155.00"));

    BigDecimal result = paperTradeEngine.calculateProfitLoss(position);

    assertEquals(new BigDecimal("500.00"), result);
}
```

## Fixtures and Factories

### Test Data Creation
Tests create test data inline or use helper methods. No separate fixture files.

**Inline Fixture Pattern:**
```java
Order order = new Order(
    "order_1",
    "AAPL",
    OrderType.MARKET,
    TradeDirection.LONG,
    new BigDecimal("100"),
    new BigDecimal("150.00"),
    null,
    null
);
```

**Factory Method Pattern (Domain Objects):**
```java
// Uses domain object's factory method
OhlcvCandle candle = OhlcvCandle.of(
    symbol,
    LocalDate.now(),
    open,
    high,
    low,
    close,
    volume
);
```

**Test Data List Pattern:**
```java
testSignals = new ArrayList<>();
testSignals.add(new Signal("AAPL", "BUY", 150.0, LocalDateTime.now(), "Breakout above resistance"));
testSignals.add(new Signal("TSLA", "SELL", 250.0, LocalDateTime.now(), "Support level broken"));
testSignals.add(new Signal("MSFT", "BUY", 380.5, LocalDateTime.now(), "Moving average crossover"));
```

## Coverage

### Coverage Tool
- **Tool:** JaCoCo Maven Plugin
- **Version:** 0.8.12
- **Profile:** `coverage` activates full coverage reporting

### Coverage Configuration
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.plugin.version}</version>
    <configuration>
        <excludes>**/domain/*,</**/model/*,</**/dto/*</excludes>
    </configuration>
</plugin>
```

**Excluded Packages:**
- Domain classes (`com.swingtrade.domain`)
- Model classes (`com.swingtrade.*.model`)
- DTO classes (`com.swingtrade.*.dto`)

These packages are POJOs/records that don't require testing coverage.

### Coverage Reporting
```bash
# Generate coverage report
mvn test jacoco:report

# View report in browser
open target/site/jacoco/index.html
```

## Test Types

### Unit Tests
**Scope:** Test individual methods and classes in isolation.

**Example - `PaperTradeEngineTest.java`:**
```java
@ExtendWith(MockitoExtension.class)
public class PaperTradeEngineTest {

    private PaperTradeEngine paperTradeEngine;
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("100000.00");

    @BeforeEach
    void setUp() {
        paperTradeEngine = new PaperTradeEngine(INITIAL_CAPITAL);
    }

    @Test
    void testPlaceOrder_ValidOrder_ShouldSucceed() {
        // Given
        Order order = new Order("order_1", "AAPL", OrderType.MARKET,
            TradeDirection.LONG, new BigDecimal("100"), new BigDecimal("150.00"), null, null);

        // When
        Order result = paperTradeEngine.placeOrder(order);

        // Then
        assertNotNull(result);
        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
        assertEquals("order_1", result.getOrderId());
    }

    @Test
    void testPlaceOrder_InvalidOrder_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            paperTradeEngine.placeOrder(null);
        });
    }
}
```

### Integration Tests
**Scope:** Test interaction between multiple components with Spring context.

**Example - `BrokerModuleIntegrationTest.java`:**
```java
@SpringBootTest
public class BrokerModuleIntegrationTest {

    private PaperTradingServiceImpl brokerService;
    private PaperTradeEngine paperTradeEngine;

    @BeforeEach
    void setUp() {
        paperTradeEngine = new PaperTradeEngine(INITIAL_CAPITAL);
        brokerService = new PaperTradingServiceImpl(paperTradeEngine);
    }

    @Test
    void testPaperTradingFunctionality_ComprehensiveTest() {
        // Test 1: Place order
        Order order = new Order("order_1", "AAPL", OrderType.MARKET,
            TradeDirection.LONG, new BigDecimal("100"), new BigDecimal("150.00"), null, null);

        Order placedOrder = brokerService.placeOrder(order);
        assertNotNull(placedOrder);
        assertEquals(OrderStatus.ACCEPTED, placedOrder.getStatus());

        // Test 2: Get portfolio
        var portfolio = brokerService.getPortfolio();
        assertNotNull(portfolio);

        // Test 3: Get open positions
        List<Position> openPositions = brokerService.getOpenPositions();
        assertNotNull(openPositions);

        // ... more test scenarios
    }
}
```

### Entity Tests
**Scope:** Test persistence and data layer with TestContainers.

**Example - `DataIngestionServiceTest.java`:**
```java
class DataIngestionServiceTest {

    private DataIngestionService dataIngestionService;
    private UpstoxRestClient upstoxRestClient;
    private UpstoxConfig upstoxConfig;

    @BeforeEach
    void setUp() {
        upstoxConfig = new UpstoxConfig();
        upstoxConfig.setApiUrl("https://api.upstox.com/v2");
        upstoxConfig.setAccessToken("test_token");

        upstoxRestClient = Mockito.mock(UpstoxRestClient.class);
        dataIngestionService = new DataIngestionService(upstoxRestClient);
    }

    @Test
    void testDataQualityValidation() {
        LocalDate fromDate = LocalDate.now().minusYears(3);
        LocalDate toDate = LocalDate.now();

        DataIngestionService.DataQualityReport report =
            dataIngestionService.validateDataQuality("RELIANCE", fromDate, toDate);

        assert report != null;
        assert report.getStockSymbol().equals("RELIANCE");
    }
}
```

## Common Patterns

### Arrange-Act-Assert Pattern
All tests follow AAA pattern explicitly with comments:

```java
@Test
void testGetLatestSignals_ReturnsCorrectSignalTypes() {
    // Arrange - the service returns BUY and SELL signals
    List<Signal> signals = signalService.getLatestSignals();

    // Act & Assert
    assertTrue(signals.stream().anyMatch(s -> "BUY".equals(s.getType())),
               "Should contain at least one BUY signal");
    assertTrue(signals.stream().anyMatch(s -> "SELL".equals(s.getType())),
               "Should contain at least one SELL signal");
}
```

### Async Testing
Most tests are synchronous. For async operations, use `CompletableFuture` patterns (not currently used in test suite).

### Error Testing
Test exception scenarios with `assertThrows`:

```java
@Test
void testPlaceOrder_InvalidOrder_ShouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> {
        paperTradeEngine.placeOrder(null);
    });
}
```

### Validation Testing
Test constraint violations:

```java
@Test
void testGetLatestSignals_ValidatesPriceRange() {
    List<Signal> signals = signalService.getLatestSignals();

    for (Signal signal : signals) {
        assertNotNull(signal.getPrice());
        assertTrue(signal.getPrice() > 0, "Signal price should be positive");
    }
}
```

### Collection Validation Testing
Test collection properties:

```java
@Test
void testGetLatestSignals_ListIsMutable() {
    List<Signal> signals = signalService.getLatestSignals();

    assertDoesNotThrow(() -> signals.clear(),
                       "Returned list should be mutable");
}

@Test
void testGetLatestSignals_NoNullElements() {
    List<Signal> signals = signalService.getLatestSignals();

    assertFalse(signals.contains(null), "List should not contain null elements");
}
```

### Streaming Assertions
Use Java streams for complex assertions:

```java
@Test
void testGetLatestSignals_ReturnsDistinctSymbols() {
    List<Signal> signals = signalService.getLatestSignals();

    List<String> symbols = signals.stream().map(Signal::getSymbol).toList();
    assertTrue(symbols.stream().distinct().count() == symbols.size(),
               "All signals should have distinct symbols");
}
```

### Regex Validation
Test data format constraints:

```java
@Test
void testGetLatestSignals_SymbolFormat() {
    List<Signal> signals = signalService.getLatestSignals();

    for (Signal signal : signals) {
        assertNotNull(signal.getSymbol());
        assertTrue(signal.getSymbol().matches("[A-Z]+"),
                   "Symbol should consist of uppercase letters");
    }
}
```

## Test Coverage Gaps

### Currently Untested Areas
- **Strategy module:** No test files found
- **Core module:** No test files found
- **LLM service:** Limited testing of LLM client functionality
- **Data module:** Basic tests only, no integration with real database

### Recommended Test Additions
1. Add unit tests for `Strategy` interface implementations
2. Add domain model tests for `Stock`, `OhlcvCandle`, `Signal` records
3. Add integration tests for `api` module REST endpoints
4. Add wiremock tests for external API calls in `data` and `llm` modules
5. Add TestContainers-based integration tests for database operations

---

*Testing analysis: 2026-03-07*
