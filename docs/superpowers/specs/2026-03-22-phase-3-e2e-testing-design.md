# Phase 3 E2E Testing Design

**Date:** 2026-03-22
**Phase:** 3 (Data Pipeline)
**Status:** Approved

---

## Overview

End-to-end testing for Phase 3 (Data Pipeline) using Spring Boot TestContainers with PostgreSQL + TimescaleDB. Tests the complete data ingestion workflow from API fetch to database storage to validation.

---

## Test Architecture

### Test Class Structure

```
DataPipelineE2ETest.java
├── Testcontainers setup (PostgreSQL + TimescaleDB)
├── MockRestServiceServer for Upstox API
├── Full @SpringBootTest context
├── Flyway migrations auto-run
└── Complete E2E workflow tests
```

### Dependencies

| Dependency | Purpose | Scope |
|------------|---------|-------|
| `spring-boot-starter-test` | Test framework, MockMvc | test |
| `testcontainers` | PostgreSQL container | test |
| `testcontainers:junit-jupiter` | TestContainers JUnit integration | test |
| `postgresql` | JDBC driver for test DB | test |
| `MockRestServiceServer` | Mock Upstox API responses | test |
| `assertj-core` | Fluent assertions | test |

---

## Test Flow

### Test 1: Complete Backfill Workflow

**Expected Behavior:**
1. Trigger backfill for "RELIANCE" covering 5 years
2. System fetches OHLCV data from mocked Upstox API
3. Candles saved to TimescaleDB hypertable
4. Weekend days skipped automatically
5. Summary log shows total candles ingested

**Verification:**
- Count candles in database matches expected trading days
- No duplicates inserted
- All price fields valid (high >= open/close, low <= open/close)

---

### Test 2: Data Quality Validation

**Expected Behavior:**
1. Run validation for "RELIANCE" date range
2. System calculates expected trading days
3. Compares against actual candles in DB
4. Reports gaps and anomalies

**Verification:**
- `expectedTradingDays` matches calendar calculation
- `actualTradingDays` matches DB count
- `hasIssues` = false when no gaps/anomalies

---

### Test 3: Retrieve Latest Candle

**Expected Behavior:**
1. Query for most recent candle for a stock
2. System returns latest record by date

**Verification:**
- `Optional<OhlcvCandleEntity>` contains valid candle
- Date matches latest date in DB for that symbol

---

### Test 4: Retrieve Recent Candles

**Expected Behavior:**
1. Request last N days of candles for a stock
2. System returns ordered list (descending by date)

**Verification:**
- Returns exactly N candles (or fewer if not enough data)
- Sorted by date descending
- All candles match requested symbol

---

### Test 5: Price Anomaly Detection

**Expected Behavior:**
1. Inject invalid candle (High < Open)
2. Run anomaly detection
3. System identifies and reports invalid data

**Verification:**
- `PriceAnomaly` object created with correct details
- `hasIssues` = true in validation report
- Anomaly description matches injected invalid data

---

## Test Class Implementation

### Base Test Configuration

```java
@DataPipelineE2ETest
@SpringBootTest
@Testcontainers
@AutoConfigureTestMvc
class DataPipelineE2ETest {

    @Testcontainers
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("swingtrade_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureTests(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::JdbcUrl);
        registry.add("spring.datasource.username", postgres::username);
        registry.add("spring.datasource.password", postgres::password);
    }

    @Autowired
    private DataIngestionService ingestionService;

    @Autowired
    private OhlcvCandleRepository candleRepository;

    @Autowired
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Mock Upstox API
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(restTemplate).build();
        // Configure mock responses
    }
}
```

### Test 1: Complete Backfill Workflow

```java
@Test
void testCompleteBackfillWorkflow() {
    // Given: Mock Upstox returns OHLCV data for RELIANCE
    // When: Trigger backfill for 5 years
    ingestionService.backfillStockData("RELIANCE", 5);

    // Then: Verify candles saved correctly
    long count = candleRepository.countBySymbol("RELIANCE");
    assertThat(count).isGreaterThan(1000); // ~5 years of trading days

    // Verify no duplicates
    List<OhlcvCandleEntity> candles = candleRepository.findAllBySymbol("RELIANCE");
    assertThat(candles).hasNoDuplicates();

    // Verify price validity
    candles.forEach(c -> {
        assertThat(c.getHighPrice()).isGreaterThanOrEqualTo(c.getOpenPrice());
        assertThat(c.getHighPrice()).isGreaterThanOrEqualTo(c.getClosePrice());
        assertThat(c.getLowPrice()).isLessThanOrEqualTo(c.getOpenPrice());
        assertThat(c.getLowPrice()).isLessThanOrEqualTo(c.getClosePrice());
    });
}
```

### Test 2: Data Quality Validation

```java
@Test
void testDataQualityValidation() {
    // Given: Backfilled data exists
    ingestionService.backfillStockData("HDFCBANK", 1);

    // When: Validate data quality
    DataIngestionService.DataQualityReport report =
        ingestionService.validateDataQuality(
            "HDFCBANK",
            LocalDate.now().minusYears(1),
            LocalDate.now()
        );

    // Then: Verify validation results
    assertThat(report.getExpectedTradingDays()).isGreaterThan(200);
    assertThat(report.getActualTradingDays()).isEqualTo(
        candleRepository.countBySymbol("HDFCBANK")
    );
    assertThat(report.getHasIssues()).isFalse();
    assertThat(report.getGaps()).isEmpty();
    assertThat(report.getAnomalies()).isEmpty();
}
```

### Test 3: Retrieve Latest Candle

```java
@Test
void testRetrieveLatestCandle() {
    // Given: Data exists in DB
    ingestionService.backfillStockData("TCS", 1);

    // When: Get latest candle
    Optional<OhlcvCandleEntity> latest =
        ingestionService.getLatestCandle("TCS");

    // Then: Verify latest candle
    assertThat(latest).isPresent();
    assertThat(latest.get().getSymbol()).isEqualTo("TCS");

    // Verify it's actually the latest
    List<OhlcvCandleEntity> all =
        candleRepository.findAllBySymbolOrderByDateDesc("TCS");
    assertThat(latest.get().getDate()).isEqualTo(all.get(0).getDate());
}
```

### Test 4: Retrieve Recent Candles

```java
@Test
void testRetrieveRecentCandles() {
    // Given: Data exists in DB
    ingestionService.backfillStockData("INFY", 2);

    // When: Get last 30 days
    List<OhlcvCandleEntity> recent =
        ingestionService.getRecentCandles("INFY", 30);

    // Then: Verify results
    assertThat(recent).hasSizeBetween(1, 30);
    assertThat(recent).isSortedAccordingTo(
        Comparator.comparing(OhlcvCandleEntity::getDate).reversed()
    );

    // Verify all candles match symbol
    recent.forEach(c -> assertThat(c.getSymbol()).isEqualTo("INFY"));
}
```

### Test 5: Price Anomaly Detection

```java
@Test
void testPriceAnomalyDetection() {
    // Given: Inject invalid candle (High < Open)
    OhlcvCandleEntity invalid = new OhlcvCandleEntity();
    invalid.setSymbol("ADANIPORTS");
    invalid.setDate(LocalDate.now());
    invalid.setOpenPrice(BigDecimal.valueOf(1000));
    invalid.setHighPrice(BigDecimal.valueOf(900)); // Invalid: High < Open
    invalid.setLowPrice(BigDecimal.valueOf(950));
    invalid.setClosePrice(BigDecimal.valueOf(980));
    invalid.setVolume(1000000);
    candleRepository.save(invalid);

    // When: Validate data quality
    DataIngestionService.DataQualityReport report =
        ingestionService.validateDataQuality(
            "ADANIPORTS",
            LocalDate.now().minusDays(1),
            LocalDate.now()
        );

    // Then: Verify anomaly detected
    assertThat(report.getHasIssues()).isTrue();
    assertThat(report.getAnomalies()).hasSize(1);

    DataIngestionService.PriceAnomaly anomaly = report.getAnomalies().get(0);
    assertThat(anomaly.getType()).isEqualTo("High price invalid");
    assertThat(anomaly.getSymbol()).isEqualTo("ADANIPORTS");
}
```

---

## Test Execution

### Run All E2E Tests

```bash
cd api
mvn test -Dtest=DataPipelineE2ETest
```

### Run Specific Test

```bash
mvn test -Dtest=DataPipelineE2ETest#testCompleteBackfillWorkflow
```

### Expected Execution Time

| Test | Approx. Time |
|------|--------------|
| testCompleteBackfillWorkflow | 30-45s |
| testDataQualityValidation | 10-15s |
| testRetrieveLatestCandle | 5-10s |
| testRetrieveRecentCandles | 5-10s |
| testPriceAnomalyDetection | 5-10s |
| **Total** | **~1-2 minutes** |

---

## Success Criteria

- [ ] All 5 tests pass consistently
- [ ] Test execution time < 3 minutes total
- [ ] No flaky tests (pass on rerun)
- [ ] TestContainers spin up/down cleanly
- [ ] Database cleaned between tests

---

## Next Steps

1. **Write implementation plan** - Use `/gsd:plan-phase 5` to add E2E tests to Phase 5 (Testing Foundation)
2. **Execute tests** - Run tests against current codebase
3. **Fix any failures** - Address test failures if they occur

---

*Spec written: 2026-03-22*
*Phase: 3 (Data Pipeline) E2E Testing*
