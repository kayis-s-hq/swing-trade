# API Testing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create comprehensive API endpoint tests for the SwingTrade REST API using `@WebMvcTest` slice testing with shared test fixtures.

**Architecture:** Use Spring's `@WebMvcTest` annotation for slice testing controllers with `MockMvc` for request simulation. Shared `@TestConfiguration` fixtures provide DRY test data across all controller tests.

**Tech Stack:** JUnit 5, Mockito, Spring Boot Test, AssertJ, MockMvc

---

## Context

This plan creates API endpoint tests for the SwingTrade swing trading system. The tests use Spring's slice testing approach (`@WebMvcTest`) to isolate the Web layer from the full application context, providing fast and reliable tests.

**Key Files to Create:**
- `api/src/test/java/com/swingtrade/api/test/fixtures/ApiTestFixtures.java` - Shared test data fixtures
- `api/src/test/java/com/swingtrade/api/test/integration/BaseWebMvcTest.java` - Base test class
- `api/src/test/java/com/swingtrade/api/controller/SignalControllerTest.java` - Signal endpoint tests
- `api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java` - Position endpoint tests
- `api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java` - Trading endpoint tests

**Existing Controllers to Test:**
- `SignalController` - `/api/signals` endpoints
- `PositionController` - `/api/positions` endpoints
- `TradingController` - `/api/trades` endpoints

**Domain Models:**
- `Signal` - Trading signal with Symbol, SignalType (BUY/SELL/HOLD), confidence, reasoning
- `Position` - Open position with Symbol, entryPrice, quantity, status

---

## Task 1: Create ApiTestFixtures.java

**Files:**
- Create: `api/src/test/java/com/swingtrade/api/test/fixtures/ApiTestFixtures.java`

**Step 1: Write the test fixtures**

```java
package com.swingtrade.api.test.fixtures;

import com.swingtrade.domain.Position;
import com.swingtrade.domain.Signal;
import com.swingtrade.api.dto.SignalResponse;
import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.PerformanceResponse;
import com.swingtrade.api.dto.ScanResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.TestConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@TestConfiguration
public class ApiTestFixtures {

    // Factory methods for test data
    public static Signal createBuySignal(String symbol) {
        return new Signal(
            null,
            symbol,
            LocalDate.now(),
            Signal.SignalType.BUY,
            new BigDecimal("0.85"),
            "Strong bullish momentum with RSI oversold",
            new BigDecimal("2500.00"),
            new BigDecimal("2450.00"),
            new BigDecimal("2600.00"),
            new BigDecimal("2.5"),
            "RSI=35, MACD bullish crossover",
            LocalDate.now()
        );
    }

    public static Signal createSellSignal(String symbol) {
        return new Signal(
            null,
            symbol,
            LocalDate.now(),
            Signal.SignalType.SELL,
            new BigDecimal("0.75"),
            "Bearish divergence detected",
            new BigDecimal("3800.00"),
            new BigDecimal("3850.00"),
            new BigDecimal("3700.00"),
            new BigDecimal("2.0"),
            "RSI=72, resistance rejection",
            LocalDate.now()
        );
    }

    public static Signal createHoldSignal(String symbol) {
        return new Signal(
            null,
            symbol,
            LocalDate.now(),
            Signal.SignalType.HOLD,
            new BigDecimal("0.55"),
            "Mixed signals, wait for confirmation",
            new BigDecimal("1500.00"),
            new BigDecimal("1480.00"),
            new BigDecimal("1520.00"),
            new BigDecimal("1.5"),
            "Neutral RSI, sideways trend",
            LocalDate.now()
        );
    }

    public static Position createLongPosition(String symbol, BigDecimal entryPrice, int quantity) {
        return new Position(
            null,
            symbol,
            entryPrice,
            LocalDate.now(),
            quantity,
            entryPrice.multiply(new BigDecimal("0.95")),
            entryPrice.multiply(new BigDecimal("1.10")),
            Position.PositionStatus.OPEN,
            "Breakout above resistance",
            entryPrice
        );
    }

    public static List<Signal> createSampleSignals() {
        return List.of(
            createBuySignal("RELIANCE"),
            createSellSignal("TCS"),
            createHoldSignal("INFY")
        );
    }

    public static PerformanceResponse createPerformanceMetrics() {
        PerformanceResponse response = new PerformanceResponse();
        response.setTotalTrades(10);
        response.setWinRate(65.0);
        response.setTotalReturn(15.5);
        response.setSharpeRatio(1.2);
        return response;
    }

    public static ScanResponse createScanResult() {
        ScanResponse response = new ScanResponse();
        response.setSignalsFound(3);
        response.setStatus(ScanResponse.ScanStatus.COMPLETED);
        response.setMessage("Scan completed successfully");
        return response;
    }
}
```

**Step 2: Verify compilation**

Run: `cd api && mvn compile test-compile`
Expected: SUCCESS with no errors

**Step 3: Commit**

```bash
git add api/src/test/java/com/swingtrade/api/test/fixtures/ApiTestFixtures.java
git commit -m "test: create shared API test fixtures"
```

---

## Task 2: Create BaseWebMvcTest.java

**Files:**
- Create: `api/src/test/java/com/swingtrade/api/test/integration/BaseWebMvcTest.java`

**Step 1: Write the base test class**

```java
package com.swingtrade.api.test.integration;

import com.swingtrade.api.test.fixtures.ApiTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest
public abstract class BaseWebMvcTest {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected ApiTestFixtures fixtures;

    protected MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
}
```

**Step 2: Verify compilation**

Run: `cd api && mvn compile test-compile`
Expected: SUCCESS with no errors

**Step 3: Commit**

```bash
git add api/src/test/java/com/swingtrade/api/test/integration/BaseWebMvcTest.java
git commit -m "test: create base WebMvc test class"
```

---

## Task 3: Create SignalControllerTest.java

**Files:**
- Create: `api/src/test/java/com/swingtrade/api/controller/SignalControllerTest.java`

**Step 1: Write the failing test**

```java
package com.swingtrade.api.controller;

import com.swingtrade.api.dto.SignalResponse;
import com.swingtrade.api.test.fixtures.ApiTestFixtures;
import com.swingtrade.api.test.integration.BaseWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SignalController.class)
class SignalControllerTest extends BaseWebMvcTest {

    @MockBean
    private com.swingtrade.api.SignalService signalService;

    @MockBean
    private com.swingtrade.api.ScanService scanService;

    @Test
    void testGetLatestSignals_returns200WithSignals() throws Exception {
        when(signalService.getLatestSignals()).thenReturn(List.of(
            convertToSignalResponse(fixtures.createBuySignal("RELIANCE")),
            convertToSignalResponse(fixtures.createSellSignal("TCS"))
        ));

        mockMvc.perform(get("/api/signals/latest")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetSignalsBySymbol_returns200WithSignals() throws Exception {
        when(signalService.getSignalsBySymbol(any())).thenReturn(List.of(
            convertToSignalResponse(fixtures.createBuySignal("INFY"))
        ));

        mockMvc.perform(get("/api/signals/symbol/INFY")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].symbol").value("INFY"));
    }

    @Test
    void testGetSignalsByType_returns200WithFilteredSignals() throws Exception {
        when(signalService.getSignalsByType(any())).thenReturn(List.of(
            convertToSignalResponse(fixtures.createBuySignal("TCS"))
        ));

        mockMvc.perform(get("/api/signals/type/BUY")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetHighConfidenceSignals_returns200WithFilteredSignals() throws Exception {
        when(signalService.getHighConfidenceSignals(any())).thenReturn(List.of(
            convertToSignalResponse(fixtures.createBuySignal("RELIANCE"))
        ));

        mockMvc.perform(get("/api/signals/high-confidence?minConfidence=0.8")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGenerateSignal_returns400WithInvalidSymbol() throws Exception {
        mockMvc.perform(post("/api/signals/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"invalid symbol\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd api && mvn test -Dtest=SignalControllerTest#testGetLatestSignals_returns200WithSignals`
Expected: FAIL with compilation error or missing dependencies

**Step 3: Implement the controller (if not exists)**

Check if `SignalController` exists. If not, create it with the endpoints defined in the tests.

**Step 4: Run test to verify it passes**

Run: `cd api && mvn test -Dtest=SignalControllerTest`
Expected: All 5 tests PASS

**Step 5: Commit**

```bash
git add api/src/test/java/com/swingtrade/api/controller/SignalControllerTest.java
git commit -m "test: add SignalController tests"
```

---

## Task 4: Create PositionControllerTest.java

**Files:**
- Create: `api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java`

**Step 1: Write the failing test**

```java
package com.swingtrade.api.controller;

import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.test.fixtures.ApiTestFixtures;
import com.swingtrade.api.test.integration.BaseWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PositionController.class)
class PositionControllerTest extends BaseWebMvcTest {

    @MockBean
    private com.swingtrade.api.PositionService positionService;

    @Test
    void testGetPositions_returns200WithPositions() throws Exception {
        when(positionService.getOpenPositions()).thenReturn(List.of(
            convertToPositionResponse(fixtures.createLongPosition("RELIANCE", new BigDecimal("2500"), 100))
        ));

        mockMvc.perform(get("/api/positions")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetPosition_returns200WithPosition() throws Exception {
        when(positionService.getPositionBySymbol(any())).thenReturn(
            convertToPositionResponse(fixtures.createLongPosition("TCS", new BigDecimal("3800"), 50))
        );

        mockMvc.perform(get("/api/positions/TCS")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("TCS"));
    }

    @Test
    void testGetPosition_returns404WhenNotFound() throws Exception {
        when(positionService.getPositionBySymbol(any())).thenReturn(null);

        mockMvc.perform(get("/api/positions/UNKNOWN")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetClosedPositions_returns200WithPositions() throws Exception {
        when(positionService.getClosedPositions()).thenReturn(List.of());

        mockMvc.perform(get("/api/positions/closed")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd api && mvn test -Dtest=PositionControllerTest#testGetPositions_returns200WithPositions`
Expected: FAIL with compilation error or missing dependencies

**Step 3: Implement the controller (if not exists)**

Check if `PositionController` exists. If not, create it with the endpoints defined in the tests.

**Step 4: Run test to verify it passes**

Run: `cd api && mvn test -Dtest=PositionControllerTest`
Expected: All 4 tests PASS

**Step 5: Commit**

```bash
git add api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java
git commit -m "test: add PositionController tests"
```

---

## Task 5: Create TradingControllerTest.java

**Files:**
- Create: `api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java`

**Step 1: Write the failing test**

```java
package com.swingtrade.api.controller;

import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.PerformanceResponse;
import com.swingtrade.api.test.fixtures.ApiTestFixtures;
import com.swingtrade.api.test.integration.BaseWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradingController.class)
class TradingControllerTest extends BaseWebMvcTest {

    @MockBean
    private com.swingtrade.api.PositionService positionService;

    @MockBean
    private com.swingtrade.api.PerformanceService performanceService;

    @Test
    void testGetPortfolioPerformance_returns200WithMetrics() throws Exception {
        when(performanceService.getPortfolioPerformance()).thenReturn(fixtures.createPerformanceMetrics());

        mockMvc.perform(get("/api/trades/performance")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTrades").numberValue(10))
            .andExpect(jsonPath("$.winRate").numberValue(65.0));
    }

    @Test
    void testGetRiskSummary_returns200WithSummary() throws Exception {
        when(positionService.getRiskSummary()).thenReturn(new TradingController.RiskSummary());

        mockMvc.perform(get("/api/trades/risk-summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd api && mvn test -Dtest=TradingControllerTest#testGetPortfolioPerformance_returns200WithMetrics`
Expected: FAIL with compilation error or missing dependencies

**Step 3: Implement the controller (if not exists)**

Check if `TradingController` exists. If not, create it with the endpoints defined in the tests.

**Step 4: Run test to verify it passes**

Run: `cd api && mvn test -Dtest=TradingControllerTest`
Expected: All 2 tests PASS

**Step 5: Commit**

```bash
git add api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java
git commit -m "test: add TradingController tests"
```

---

## Task 6: Run Full Test Suite

**Files:**
- No new files

**Step 1: Run all API tests**

Run: `cd api && mvn test`
Expected: All tests PASS

**Step 2: Verify test coverage**

Run: `cd api && mvn jacoco:report`
Expected: Coverage report generated at `target/site/jacoco/index.html`

**Step 3: Commit**

```bash
git add api/src/test/
git commit -m "test: complete API endpoint test suite"
```

---

## Summary

This plan creates a comprehensive API testing suite with:
- **Shared test fixtures** for DRY test data
- **Base WebMvc test class** for common setup
- **3 controller test classes** covering all API endpoints
- **Minimal error validation** (status codes only, as requested)

**Total Tests:** ~11 tests across 3 controller classes

---

*Implementation plan: 2026-03-08*
