# API Testing Design Document

**Document Version:** 1.0
**Created:** 2026-03-08
**Last Updated:** 2026-03-08

---

## Overview

This document describes the testing strategy for the SwingTrade API module, focusing on REST endpoint validation using Spring's slice testing approach.

---

## Design Approach

**Selected Approach:** Slice Tests (`@WebMvcTest`) with Shared Test Configuration

**Rationale:**
- Fast test execution (only loads Web layer, not full Spring context)
- Clean separation of concerns
- DRY test data through shared configuration
- Easy to debug and maintain
- Aligns with constraint to use plain Mockito + Spring's built-in test tools

---

## Architecture

### Test Structure

```
api/src/test/java/com/swingtrade/api/
├── test/
│   ├── fixtures/
│   │   └── ApiTestFixtures.java      # Shared @TestConfiguration for test data
│   └── integration/
│       └── BaseWebMvcTest.java       # Base class with @WebMvcTest + MockMvc
└── controller/
    ├── SignalControllerTest.java
    ├── OrderControllerTest.java
    ├── PerformanceControllerTest.java
    └── ScanControllerTest.java
```

---

## Components

### 1. ApiTestFixtures.java

**Location:** `api/src/test/java/com/swingtrade/api/test/fixtures/ApiTestFixtures.java`

**Purpose:** Centralized test data creation for all API tests

**Features:**
- `@TestConfiguration` class with `@Bean` definitions
- Factory methods for creating test data (Stock, Signal, Position, Trade)
- Pre-configured mock responses for common scenarios
- Static imports for clean test code

**Example:**
```java
@TestConfiguration
public class ApiTestFixtures {

    @Bean
    public SignalService signalService() {
        return mock(SignalService.class);
    }

    // Factory methods
    public static Signal createBuySignal(String symbol) { ... }
    public static Position createLongPosition(String symbol, double quantity) { ... }
    public static List<Signal> createSampleSignals() { ... }
}
```

---

### 2. BaseWebMvcTest.java

**Location:** `api/src/test/java/com/swingtrade/api/test/integration/BaseWebMvcTest.java`

**Purpose:** Common setup for all controller tests

**Features:**
- `@WebMvcTest` annotation for slice testing
- `@MockBean` for all service dependencies
- `@Autowired MockMvc` for request simulation
- `@Autowired ApiTestFixtures` for test data
- Common test setup in `@BeforeEach`

**Example:**
```java
@WebMvcTest(SwingTradeController.class)
@ExtendWith(MockitoExtension.class)
class BaseWebMvcTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected SignalService signalService;

    @MockBean
    protected PositionService positionService;

    @Autowired
    protected ApiTestFixtures fixtures;

    @BeforeEach
    void setup() {
        // Common setup for all tests
    }
}
```

---

### 3. Controller Test Classes

#### SignalControllerTest.java

**Endpoints Tested:**
- `GET /api/signals/latest` - Returns latest signals
- `GET /api/signals/{symbol}` - Returns signals for specific symbol
- `GET /api/signals?signalType=BUY` - Filters by signal type
- `GET /api/signals?minConfidence=0.7` - Filters by confidence threshold
- Invalid symbol format returns 400 Bad Request

#### OrderControllerTest.java

**Endpoints Tested:**
- `POST /api/orders` - Places order successfully
- `POST /api/orders` - Rejects when max positions exceeded (409 Conflict)
- `POST /api/orders` - Rejects when position size > 20% capital (400 Bad Request)
- `POST /api/orders` - Rejects invalid order type (400 Bad Request)

#### PerformanceControllerTest.java

**Endpoints Tested:**
- `GET /api/performance` - Returns trading performance metrics
- `GET /api/positions` - Returns open positions only
- `GET /api/portfolio` - Returns current portfolio with positions

#### ScanControllerTest.java

**Endpoints Tested:**
- `GET /api/scan` - Returns scan results with signal analysis
- `POST /api/scan` - Triggers manual signal generation

---

## Data Flow

```
Test Method
    ↓
BaseWebMvcTest (@WebMvcTest + @MockBean)
    ↓
ApiTestFixtures (test data creation)
    ↓
MockMvc.perform(request)
    ↓
MockMvcResultActions (response validation)
    ↓
assertThat(status, content, etc.)
```

---

## Error Handling Strategy

**Approach:** Test only HTTP status codes and generic error messages (minimal validation)

**Implementation:**
```java
@Test
void testInvalidSymbolFormat_returns400() throws Exception {
    mockMvc.perform(get("/api/signals/invalid symbol"))
        .andExpect(status().isBadRequest());
}
```

**No detailed error response validation** - just status code checks.

---

## Testing Strategy

### Test Coverage per Controller

- **Happy path:** Successful operations
- **Validation errors:** Invalid input → 400 Bad Request
- **Business rule violations:** Position limits → 409 Conflict
- **Edge cases:** Empty results, null values

### Test Naming Convention

`test<Method>_<Endpoint>_<ExpectedOutcome>()`

**Example:** `testGetSignals_latest_returns200WithSignals()`

---

## Dependencies

**Required:**
- JUnit 5 (Jupiter)
- Mockito
- Spring Boot Test
- AssertJ (for fluent assertions)
- JSON Assert (for JSON response validation)

**Status:** Already in `api/pom.xml`

---

## Test Execution

| Metric | Target |
|--------|--------|
| **Speed** | Fast (~1-2 seconds per test class) |
| **Isolation** | High (no external dependencies) |
| **CI/CD Ready** | Yes (deterministic, no flaky tests) |

---

## Success Criteria

1. All controller endpoints are tested
2. All tests pass locally and in CI/CD
3. Test execution time < 10 minutes total
4. No compilation warnings
5. Clear, maintainable test code

---

## References

- **REQ-105:** REST API Endpoint Tests
- **Phase 3:** API Endpoint Testing

---

*Design document: 2026-03-08*
