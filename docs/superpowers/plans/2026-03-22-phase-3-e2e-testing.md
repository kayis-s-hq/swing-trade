# Phase 3 E2E Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement end-to-end tests for Phase 3 (Data Pipeline) using Spring Boot TestContainers with PostgreSQL + TimescaleDB.

**Architecture:** Create a dedicated E2E test class that spins up real database containers, mocks external APIs with MockRestServiceServer, and verifies the complete data ingestion workflow from fetch to storage to validation.

**Tech Stack:** Spring Boot Test, TestContainers, MockRestServiceServer, AssertJ, JUnit 5

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java` | Create | Main E2E test class with 5 test methods |
| `api/src/test/java/com/swingtrade/api/fixtures/DataPipelineFixtures.java` | Create | Test data builders for OHLCV candles |
| `api/pom.xml` | Modify | Add TestContainers PostgreSQL dependency |

---

## Task 1: Add TestContainers PostgreSQL Dependency

**Files:**
- Modify: `api/pom.xml`

- [ ] **Step 1: Add TestContainers PostgreSQL dependency**

Add to `api/pom.xml` dependencies section (after existing testcontainers entries):

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verify dependency added**

```bash
cd api
mvn dependency:tree -Dincludes=org.testcontainers:postgresql
```

Expected: Shows testcontainers-postgresql 1.19.3

- [ ] **Step 3: Commit**

```bash
git add api/pom.xml
git commit -m "test: add TestContainers PostgreSQL dependency for E2E tests"
```

---

## Task 2: Create Test Fixtures

**Files:**
- Create: `api/src/test/java/com/swingtrade/api/fixtures/DataPipelineFixtures.java`

- [ ] **Step 1: Write test fixtures file**

```java
package com.swingtrade.api.fixtures;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DataPipelineFixtures {

    public static OhlcvCandleEntity createValidCandle(String symbol, LocalDate date) {
        OhlcvCandleEntity candle = new OhlcvCandleEntity();
        candle.setSymbol(symbol);
        candle.setDate(date);
        candle.setOpenPrice(new BigDecimal("1000.00"));
        candle.setHighPrice(new BigDecimal("1020.00"));
        candle.setLowPrice(new BigDecimal("990.00"));
        candle.setClosePrice(new BigDecimal("1015.00"));
        candle.setVolume(1000000);
        candle.setAdjClosePrice(new BigDecimal("1015.00"));
        return candle;
    }

    public static OhlcvCandleEntity createInvalidCandle(String symbol, LocalDate date) {
        OhlcvCandleEntity candle = new OhlcvCandleEntity();
        candle.setSymbol(symbol);
        candle.setDate(date);
        candle.setOpenPrice(new BigDecimal("1000.00"));
        candle.setHighPrice(new BigDecimal("900.00")); // Invalid: High < Open
        candle.setLowPrice(new BigDecimal("950.00"));
        candle.setClosePrice(new BigDecimal("980.00"));
        candle.setVolume(1000000);
        candle.setAdjClosePrice(new BigDecimal("980.00"));
        return candle;
    }

    public static List<LocalDate> generateTradingDays(LocalDate start, LocalDate end) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek <= 5) { // Monday = 1, Friday = 5
                tradingDays.add(current);
            }
            current = current.plusDays(1);
        }

        return tradingDays;
    }
}
```

- [ ] **Step 2: Verify file created**

```bash
ls -la api/src/test/java/com/swingtrade/api/fixtures/DataPipelineFixtures.java
```

Expected: File exists

- [ ] **Step 3: Commit**

```bash
git add api/src/test/java/com/swingtrade/api/fixtures/DataPipelineFixtures.java
git commit -m "test: add DataPipelineFixtures for E2E test data"
```

---

## Task 3: Create E2E Test Class - Test Structure

**Files:**
- Create: `api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java`

- [ ] **Step 1: Write test class with TestContainers setup**

```java
package com.swingtrade.api;

import com.swingtrade.api.fixtures.DataPipelineFixtures;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.service.DataIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class DataPipelineE2ETest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("swingtrade_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureTests(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DataIngestionService ingestionService;

    @Autowired
    private OhlcvCandleRepository candleRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @MockBean
    private RestTemplate externalRestTemplate;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        candleRepository.deleteAll();
    }
}
```

- [ ] **Step 2: Verify file created**

```bash
ls -la api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java
```

Expected: File exists

- [ ] **Step 3: Commit**

```bash
git add api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java
git commit -m "test: create DataPipelineE2ETest with TestContainers setup"
```

---

## Task 4: Implement Test 1 - Complete Backfill Workflow

**Files:**
- Modify: `api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java`

- [ ] **Step 1: Add mock data generation helper**

Add private method to test class:

```java
private void mockUpstoxApiForStock(String symbol, List<LocalDate> dates) {
    when(externalRestTemplate.getEntities(anyString(), any(), any()))
        .thenReturn(dates.stream().map(date -> {
            // Create mock response with valid candle data
            var response = new ResponseEntity[1];
            // Mock implementation
            return response;
        }).toArray(ResponseEntity[]::new));
}
```

- [ ] **Step 2: Write test method**

Add test method to class:

```java
@Test
void testCompleteBackfillWorkflow() {
    // Given: Generate 1 year of trading days for RELIANCE
    List<LocalDate> tradingDays = DataPipelineFixtures.generateTradingDays(
        LocalDate.now().minusYears(1),
        LocalDate.now()
    );

    // When: Trigger backfill for 1 year
    ingestionService.backfillStockData("RELIANCE", 1);

    // Then: Verify candles saved correctly
    long count = candleRepository.countBySymbol("RELIANCE");
    assertThat(count).isGreaterThan(200); // ~1 year of trading days

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

- [ ] **Step 3: Run test**

```bash
cd api
mvn test -Dtest=DataPipelineE2ETest#testCompleteBackfillWorkflow -v
```

Expected: Test runs (may fail initially due to missing implementation)

- [ ] **Step 4: Fix any issues and re-run**

- [ ] **Step 5: Commit when passing**

```bash
git add api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java
git commit -m "test: add testCompleteBackfillWorkflow E2E test"
```

---

## Task 5: Implement Test 2 - Data Quality Validation

**Files:**
- Modify: `api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java`

- [ ] **Step 1: Write test method**

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

- [ ] **Step 2: Run test**

```bash
mvn test -Dtest=DataPipelineE2ETest#testDataQualityValidation -v
```

- [ ] **Step 3: Commit when passing**

```bash
git commit -m "test: add testDataQualityValidation E2E test"
```

---

## Task 6: Implement Test 3 - Retrieve Latest Candle

**Files:**
- Modify: `api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java`

- [ ] **Step 1: Write test method**

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

- [ ] **Step 2: Run test**

```bash
mvn test -Dtest=DataPipelineE2ETest#testRetrieveLatestCandle -v
```

- [ ] **Step 3: Commit when passing**

```bash
git commit -m "test: add testRetrieveLatestCandle E2E test"
```

---

## Task 7: Implement Test 4 - Retrieve Recent Candles

**Files:**
- Modify: `api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java`

- [ ] **Step 1: Write test method**

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
        java.util.Comparator.comparing(OhlcvCandleEntity::getDate).reversed()
    );

    // Verify all candles match symbol
    recent.forEach(c -> assertThat(c.getSymbol()).isEqualTo("INFY"));
}
```

- [ ] **Step 2: Run test**

```bash
mvn test -Dtest=DataPipelineE2ETest#testRetrieveRecentCandles -v
```

- [ ] **Step 3: Commit when passing**

```bash
git commit -m "test: add testRetrieveRecentCandles E2E test"
```

---

## Task 8: Implement Test 5 - Price Anomaly Detection

**Files:**
- Modify: `api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java`

- [ ] **Step 1: Write test method**

```java
@Test
void testPriceAnomalyDetection() {
    // Given: Inject invalid candle (High < Open)
    OhlcvCandleEntity invalid = new OhlcvCandleEntity();
    invalid.setSymbol("ADANIPORTS");
    invalid.setDate(LocalDate.now());
    invalid.setOpenPrice(new BigDecimal("1000.00"));
    invalid.setHighPrice(new BigDecimal("900.00")); // Invalid: High < Open
    invalid.setLowPrice(new BigDecimal("950.00"));
    invalid.setClosePrice(new BigDecimal("980.00"));
    invalid.setVolume(1000000);
    invalid.setAdjClosePrice(new BigDecimal("980.00"));
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

- [ ] **Step 2: Run test**

```bash
mvn test -Dtest=DataPipelineE2ETest#testPriceAnomalyDetection -v
```

- [ ] **Step 3: Commit when passing**

```bash
git commit -m "test: add testPriceAnomalyDetection E2E test"
```

---

## Task 9: Run All E2E Tests Together

**Files:**
- No file changes

- [ ] **Step 1: Run all E2E tests**

```bash
cd api
mvn test -Dtest=DataPipelineE2ETest -v
```

Expected: All 5 tests pass, total execution time ~1-2 minutes

- [ ] **Step 2: Verify test results**

Check console output for:
- `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
- No flaky test warnings

- [ ] **Step 3: Commit final test file**

```bash
git add api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java
git commit -m "test: complete Phase 3 E2E test suite with all 5 tests"
```

---

## Success Criteria

- [ ] All 5 E2E tests pass consistently
- [ ] Test execution time < 3 minutes total
- [ ] No flaky tests (pass on rerun)
- [ ] TestContainers spin up/down cleanly
- [ ] Database cleaned between tests

---

## Verification Commands

```bash
# Run all E2E tests
cd api
mvn test -Dtest=DataPipelineE2ETest

# Run specific test
mvn test -Dtest=DataPipelineE2ETest#testCompleteBackfillWorkflow

# Check test coverage
mvn test -Dtest=DataPipelineE2ETest --coverage
```

---

*Plan written: 2026-03-22*
*Phase: 3 (Data Pipeline) E2E Testing*
