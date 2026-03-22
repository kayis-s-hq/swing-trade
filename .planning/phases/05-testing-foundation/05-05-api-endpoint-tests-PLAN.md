---
phase: 05-testing-foundation
plan: 05
type: execute
wave: 5
depends_on:
  - 05-03
files_modified:
  - api/src/test/java/com/swingtrade/api/controller/SignalControllerTest.java
  - api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java
  - api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java
  - api/src/test/java/com/swingtrade/api/controller/HealthControllerTest.java
  - api/src/test/resources/application-test.yml
autonomous: true
requirements:
  - REQ-105
user_setup:
  - service: "TestContainers"
    why: "API endpoint tests require PostgreSQL + TimescaleDB"
    env_vars: []
    dashboard_config: []
    note: "Docker must be running"

must_haves:
  truths:
    - SignalController endpoints return correct HTTP status codes
    - TradingController POST /api/trades creates positions correctly
    - TradingController enforces position limits (max 5)
    - PositionController GET/POST operations work correctly
    - Error handling returns 400, 404, 409, 500 appropriately
    - @SpringBootTest + MockMvc properly configured
  artifacts:
    - path: "api/src/test/java/com/swingtrade/api/controller/SignalControllerTest.java"
      provides: "SignalController integration tests"
      min_lines: 150
    - path: "api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java"
      provides: "TradingController integration tests"
      min_lines: 130
    - path: "api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java"
      provides: "PositionController integration tests"
      min_lines: 120
    - path: "api/src/test/java/com/swingtrade/api/controller/HealthControllerTest.java"
      provides: "HealthController tests"
      min_lines: 40
    - path: "api/src/test/resources/application-test.yml"
      provides: "Test configuration for API tests"
      min_lines: 25
  key_links:
    - from: "api/src/test/java/com/swingtrade/api/controller/SignalControllerTest.java"
      to: "api/src/main/java/com/swingtrade/api/controller/SignalController.java"
      via: "@MockMvc perform().get("/api/signals/latest")"
      pattern: "MockMvc\\.perform|@Autowired MockMvc"
    - from: "api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java"
      to: "api/src/main/java/com/swingtrade/api/controller/TradingController.java"
      via: "POST /api/trades creates position, 409 when max positions reached"
      pattern: "perform\\.post|createPosition|max.*positions"
---

<objective>
Create comprehensive API endpoint tests using @SpringBootTest + MockMvc for Signal, Trading, Position, and Health controllers with proper error handling validation.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS.md (REQ-105)
@.planning/ROADMAP.md (Phase 5 goal)
@05-03-integration-test-infrastructure-SUMMARY.md (TestContainers patterns)
@05-04-http-mocking-with-MockRestServiceServer-SUMMARY.md (HTTP mocking patterns)

# API Test Patterns
<!-- @SpringBootTest + MockMvc for controller tests -->

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignalService signalService;

    @MockBean
    private OhlcvCandleRepository ohlcvCandleRepository;

    @Test
    void testGetLatestSignals() throws Exception {
        when(signalService.getLatestSignals())
            .thenReturn(List.of(new SignalResponse("TCS", "BUY", 0.8)));

        mockMvc.perform(get("/api/signals/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("TCS"))
            .andExpect(jsonPath("$[0].type").value("BUY"));
    }

    @Test
    void testGetSignalsBySymbol() throws Exception {
        mockMvc.perform(get("/api/signals/symbol/RELIANCE"))
            .andExpect(status().isOk());
    }
}
```

# Error Handling Test Pattern
<!-- Test 400, 404, 409, 500 responses -->

```java
@Test
void testCreatePositionMaxPositions() throws Exception {
    // Setup: 5 positions already exist
    when(positionService.getOpenPositions()).thenReturn(mockPositions);

    mockMvc.perform(post("/api/trades")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"symbol\": \"TCS\", \"entryPrice\": 3000 }"))
        .andExpect(status().isConflict())  // 409
        .andExpect(jsonPath("$.error").value("Maximum positions reached"));
}

@Test
void testCreatePositionInvalidSymbol() throws Exception {
    mockMvc.perform(post("/api/trades")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"symbol\": \"INVALID\", \"entryPrice\": 3000 }"))
        .andExpect(status().isBadRequest());  // 400
}

@Test
void testGetNonExistentPosition() throws Exception {
    when(positionService.getPositionBySymbol("NONEXISTENT")).thenReturn(null);

    mockMvc.perform(get("/api/positions/NONEXISTENT"))
        .andExpect(status().isNotFound());  // 404
}
```

# MockBean Pattern
<!-- Mock services that shouldn't hit real database -->

```java
@SpringBootTest
class TradingControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean
    private PositionService positionService;  // Mock business logic

    @MockBean
    private PerformanceService performanceService;

    @Test
    void testGetPortfolioPerformance() {
        when(performanceService.getPortfolioPerformance())
            .thenReturn(new PerformanceResponse(100000.0, 0.75));

        mockMvc.perform(get("/api/trades/performance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPnL").value(100000.0));
    }
}
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create SignalControllerTest with endpoint tests</name>
  <files>api/src/test/java/com/swingtrade/api/controller/SignalControllerTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - GET /api/signals/latest returns list of signals
    - GET /api/signals/symbol/{symbol} returns signals for specific stock
    - GET /api/signals/type/{type} filters by BUY/SELL/HOLD
    - GET /api/signals/high-confidence filters by confidence threshold
    - POST /api/signals/generate creates new signal
    - Error handling: 400 for invalid symbol, 500 for server errors
  </behavior>
  <action>
Create SignalControllerTest.java:

```java
package com.swingtrade.api.controller;

import com.swingtrade.api.SignalService;
import com.swingtrade.api.dto.SignalResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;

@WebMvcTest(SignalController.class)
class SignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignalService signalService;

    @Nested
    @Order(1)
    class LatestSignalsTests {

        @Test
        void testGetLatestSignals() throws Exception {
            List<SignalResponse> signals = List.of(
                new SignalResponse("RELIANCE", "BUY", BigDecimal.valueOf(0.85), "Technical indicators aligned"),
                new SignalResponse("TCS", "HOLD", BigDecimal.valueOf(0.60), "Mixed signals")
            );

            when(signalService.getLatestSignals()).thenReturn(signals);

            mockMvc.perform(get("/api/signals/latest")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].type").value("BUY"))
                .andExpect(jsonPath("$[0].confidence").value(0.85))
                .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @Order(2)
    class SymbolSpecificTests {

        @Test
        void testGetSignalsBySymbol() throws Exception {
            List<SignalResponse> signals = List.of(
                new SignalResponse("INFY", "SELL", BigDecimal.valueOf(0.70), "Trend reversal")
            );

            when(signalService.getSignalsBySymbol("INFY")).thenReturn(signals);

            mockMvc.perform(get("/api/signals/symbol/INFY")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("INFY"))
                .andExpect(jsonPath("$[0].type").value("SELL"));
        }

        @Test
        void testGetSignalsBySymbolNotFound() throws Exception {
            when(signalService.getSignalsBySymbol("NONEXISTENT")).thenReturn(List.of());

            mockMvc.perform(get("/api/signals/symbol/NONEXISTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @Order(3)
    class TypeFilterTests {

        @Test
        void testGetSignalsByTypeBuy() throws Exception {
            List<SignalResponse> signals = List.of(
                new SignalResponse("HDFCBANK", "BUY", BigDecimal.valueOf(0.75), "Uptrend confirmed")
            );

            when(signalService.getSignalsByType(SignalResponse.SignalType.BUY)).thenReturn(signals);

            mockMvc.perform(get("/api/signals/type/BUY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BUY"));
        }
    }

    @Nested
    @Order(4)
    class HighConfidenceTests {

        @Test
        void testGetHighConfidenceSignals() throws Exception {
            List<SignalResponse> signals = List.of(
                new SignalResponse("SBIN", "BUY", BigDecimal.valueOf(0.90), "Strong momentum")
            );

            when(signalService.getHighConfidenceSignals(0.7)).thenReturn(signals);

            mockMvc.perform(get("/api/signals/high-confidence")
                    .param("minConfidence", "0.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].confidence").value(0.90));
        }

        @Test
        void testGetHighConfidenceSignalsInvalidThreshold() throws Exception {
            mockMvc.perform(get("/api/signals/high-confidence")
                    .param("minConfidence", "1.5"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @Order(5)
    class GenerateSignalTests {

        @Test
        void testGenerateSignalValid() throws Exception {
            SignalResponse response = new SignalResponse("WIPRO", "BUY", BigDecimal.valueOf(0.72), "EMA crossover");

            when(signalService.generateSignal("WIPRO")).thenReturn(response);

            mockMvc.perform(post("/api/signals/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"symbol\":\"WIPRO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("WIPRO"))
                .andExpect(jsonPath("$.type").value("BUY"));
        }

        @Test
        void testGenerateSignalInvalid() throws Exception {
            mockMvc.perform(post("/api/signals/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"symbol\":\"INVALID123\"}"))
                .andExpect(status().isBadRequest());
        }
    }
}
```
</action>
  <verify>
    <automated>mvn test -pl api -Dtest=SignalControllerTest</automated>
  </verify>
  <done>SignalControllerTest.java exists with 8+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 2: Create TradingControllerTest with trade lifecycle tests</name>
  <files>api/src/test/java/com/swingtrade/api/controller/TradingControllerTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - POST /api/trades creates position successfully
    - POST /api/trades returns 409 when max 5 positions reached
    - POST /api/trades returns 400 for invalid request
    - GET /api/trades returns list of open positions
    - GET /api/trades/performance returns portfolio stats
    - Risk summary endpoint returns sector exposure
  </behavior>
  <action>
Create TradingControllerTest.java:

```java
package com.swingtrade.api.controller;

import com.swingtrade.api.PositionService;
import com.swingtrade.api.PerformanceService;
import com.swingtrade.api.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@WebMvcTest(TradingController.class)
class TradingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PositionService positionService;

    @MockBean
    private PerformanceService performanceService;

    @Nested
    @Order(1)
    class CreatePositionTests {

        @Test
        void testCreatePositionSuccess() throws Exception {
            PositionResponse response = new PositionResponse();
            response.setSymbol("ICICIBANK");
            response.setEntryPrice(new BigDecimal("1050.00"));
            response.setQuantity(100);
            response.setStatus(PositionResponse.PositionStatus.OPEN);

            when(positionService.createPosition(any(TradeRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"symbol\": \"ICICIBANK\", \"entryPrice\": 1050, \"quantity\": 100 }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("ICICIBANK"))
                .andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        void testCreatePositionMaxPositions() throws Exception {
            when(positionService.createPosition(any(TradeRequest.class)))
                .thenThrow(new IllegalStateException("Maximum positions (5) reached"));

            mockMvc.perform(post("/api/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"symbol\": "HDFC", \"entryPrice\": 1500, \"quantity\": 50 }"))
                .andExpect(status().isConflict())  // 409
                .andExpect(jsonPath("$.error").value("Maximum positions reached"));
        }

        @Test
        void testCreatePositionInvalidRequest() throws Exception {
            mockMvc.perform(post("/api/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"symbol\": \"INVALID\", \"entryPrice\": -100 }"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @Order(2)
    class GetPositionsTests {

        @Test
        void testGetOpenPositions() throws Exception {
            List<PositionResponse> positions = List.of(
                new PositionResponse("RELIANCE", new BigDecimal("2500"), 50),
                new PositionResponse("TCS", new BigDecimal("3500"), 30)
            );

            when(positionService.getOpenPositions()).thenReturn(positions);

            mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"));
        }

        @Test
        void testGetTradeHistory() throws Exception {
            List<TradeResponse> trades = List.of(
                new TradeResponse("RELIANCE", new BigDecimal("2540"), new BigDecimal("10000"), TradeResponse.TradeStatus.CLOSED)
            );

            when(positionService.getTradeHistory("RELIANCE")).thenReturn(trades);

            mockMvc.perform(get("/api/trades/RELIANCE/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CLOSED"));
        }
    }

    @Nested
    @Order(3)
    class PerformanceTests {

        @Test
        void testGetPortfolioPerformance() throws Exception {
            PerformanceResponse response = new PerformanceResponse();
            response.setTotalPnL(new BigDecimal("125000"));
            response.setWinRate(0.75);
            response.setTotalTrades(20);

            when(performanceService.getPortfolioPerformance()).thenReturn(response);

            mockMvc.perform(get("/api/trades/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPnL").value(125000))
                .andExpect(jsonPath("$.winRate").value(0.75));
        }
    }

    @Nested
    @Order(4)
    class RiskSummaryTests {

        @Test
        void testGetRiskSummary() throws Exception {
            TradingController.RiskSummary summary = new TradingController.RiskSummary();
            summary.setTotalExposure(new BigDecimal("150000"));
            summary.setNumberOfPositions(3);
            summary.setSectorExposure(Map.of("BANK", 2, "OIL_GAS", 1));

            when(positionService.getRiskSummary()).thenReturn(summary);

            mockMvc.perform(get("/api/trades/risk-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfPositions").value(3));
        }
    }
}
```
</action>
  <verify>
    <automated>mvn test -pl api -Dtest=TradingControllerTest</automated>
  </verify>
  <done>TradingControllerTest.java exists with 7+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 3: Create PositionControllerTest with position management tests</name>
  <files>api/src/test/java/com/swingtrade/api/controller/PositionControllerTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - GET /api/positions returns list of positions
    - GET /api/positions/{symbol} returns specific position
    - GET /api/positions/closed returns closed positions
    - POST /api/positions/{symbol}/close closes position
    - GET /api/positions/stats returns position statistics
    - GET /api/positions/sector-allocation returns allocation percentages
  </behavior>
  <action>
Create PositionControllerTest.java:

```java
package com.swingtrade.api.controller;

import com.swingtrade.api.PositionService;
import com.swingtrade.api.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@WebMvcTest(PositionController.class)
class PositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PositionService positionService;

    @Nested
    @Order(1)
    class GetPositionsTests {

        @Test
        void testGetAllPositions() throws Exception {
            List<PositionResponse> positions = List.of(
                new PositionResponse("ASIANPAINT", new BigDecimal("2300"), 40)
            );

            when(positionService.getOpenPositions()).thenReturn(positions);

            mockMvc.perform(get("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].symbol").value("ASIANPAINT"));
        }

        @Test
        void testGetPositionBySymbol() throws Exception {
            PositionResponse position = new PositionResponse("BAJAJFINANCE", new BigDecimal("7200"), 20);

            when(positionService.getPositionBySymbol("BAJAJFINANCE")).thenReturn(position);

            mockMvc.perform(get("/api/positions/BAJAJFINANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BAJAJFINANCE"))
                .andExpect(jsonPath("$.entryPrice").value(7200));
        }

        @Test
        void testGetPositionBySymbolNotFound() throws Exception {
            when(positionService.getPositionBySymbol("NOTEXISTENT")).thenReturn(null);

            mockMvc.perform(get("/api/positions/NOTEXISTENT"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @Order(2)
    class ClosedPositionsTests {

        @Test
        void testGetClosedPositions() throws Exception {
            List<PositionResponse> positions = List.of(
                new PositionResponse("MARUTI", new BigDecimal("11000"), 10, PositionResponse.PositionStatus.CLOSED)
            );

            when(positionService.getClosedPositions()).thenReturn(positions);

            mockMvc.perform(get("/api/positions/closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CLOSED"));
        }
    }

    @Nested
    @Order(3)
    class ClosePositionTests {

        @Test
        void testClosePositionSuccess() throws Exception {
            PositionResponse response = new PositionResponse();
            response.setSymbol("BHARTIARTL");
            response.setStatus(PositionResponse.PositionStatus.CLOSED);
            response.setExitReason("Target hit");

            when(positionService.closePosition("BHARTIARTL", "Target hit")).thenReturn(response);

            mockMvc.perform(post("/api/positions/BHARTIARTL/close")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"exitReason\": \"Target hit\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        void testClosePositionNotFound() throws Exception {
            when(positionService.closePosition("NOTEXISTENT", null)).thenReturn(null);

            mockMvc.perform(post("/api/positions/NOTEXISTENT/close"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @Order(4)
    class StatisticsTests {

        @Test
        void testGetPositionStats() throws Exception {
            PositionController.PositionStats stats = new PositionController.PositionStats();
            stats.setTotalPositions(10);
            stats.setOpenPositions(3);
            stats.setTotalPnL(new BigDecimal("50000"));
            stats.setWinRate(0.80);

            when(positionService.getPositionStats()).thenReturn(stats);

            mockMvc.perform(get("/api/positions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPositions").value(10))
                .andExpect(jsonPath("$.winRate").value(0.80));
        }

        @Test
        void testGetSectorAllocation() throws Exception {
            PositionController.SectorAllocation allocation = new PositionController.SectorAllocation();
            allocation.setAllocation(Map.of("BANK", 0.40, "OIL_GAS", 0.30, "IT", 0.30));
            allocation.setTotalExposure(new BigDecimal("200000"));

            when(positionService.getSectorAllocation()).thenReturn(allocation);

            mockMvc.perform(get("/api/positions/sector-allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocation.BANK").value(0.40));
        }
    }
}
```
</action>
  <verify>
    <automated>mvn test -pl api -Dtest=PositionControllerTest</automated>
  </verify>
  <done>PositionControllerTest.java exists with 8+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 4: Create HealthControllerTest for system health checks</name>
  <files>api/src/test/java/com/swingtrade/api/controller/HealthControllerTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - GET /api/health returns 200 OK
    - Health check includes database, LLM endpoint, broker connection status
    - All health endpoints return JSON with status field
  </behavior>
  <action>
Create HealthControllerTest.java:

```java
package com.swingtrade.api.controller;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.database").exists())
            .andExpect(jsonPath("$.llm").exists())
            .andExpect(jsonPath("$.broker").exists());
    }

    @Test
    void testHealthEndpointWithDetails() throws Exception {
        mockMvc.perform(get("/api/health?details=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components").exists());
    }
}
```
</action>
  <verify>
    <automated>mvn test -pl api -Dtest=HealthControllerTest</automated>
  </verify>
  <done>HealthControllerTest.java exists with 2+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 5: Create application-test.yml for API tests</name>
  <files>api/src/test/resources/application-test.yml</files>
  <action>
Create application-test.yml for API tests:

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
  flyway:
    enabled: true
  mail:
    host: localhost
    port: 25
    test: true
logging:
  level:
    com.swingtrade.api: DEBUG
    org.springframework.test: DEBUG
testcontainers:
  check-access: false
```
</action>
  <verify>
    <automated>cat api/src/test/resources/application-test.yml</automated>
  </verify>
  <done>application-test.yml created for API test configuration</done>
</task>

</tasks>

<verification>
Overall checks:
1. Run `mvn test -pl api -Dtest=SignalControllerTest` to verify signal tests
2. Run `mvn test -pl api -Dtest=TradingControllerTest` to verify trade tests
3. Run `mvn test -pl api -Dtest=PositionControllerTest` to verify position tests
4. Run `mvn test -pl api -Dtest=HealthControllerTest` to verify health tests
5. All tests should pass with WebMvcTest (no @SpringBootTest needed - mocks services)
</verification>

<success_criteria>
- SignalControllerTest.java with 8+ tests
- TradingControllerTest.java with 7+ tests
- PositionControllerTest.java with 8+ tests
- HealthControllerTest.java with 2+ tests
- All 4 test classes pass with `mvn test -pl api`
- Error scenarios tested (400, 404, 409, 500)
- @WebMvcTest used (lighter than @SpringBootTest)
</success_criteria>

<output>
After completion, create `.planning/phases/05-testing-foundation/05-05-api-endpoint-tests-SUMMARY.md`
</output>
