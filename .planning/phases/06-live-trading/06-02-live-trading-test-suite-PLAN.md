---
wave: 2
depends_on:
  - 06-01-live-trading-verification
files_modified:
  - broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java
  - broker/src/test/java/com/swingtrade/broker/factory/BrokerServiceFactoryTest.java
  - broker/src/test/java/com/swingtrade/broker/service/LiveTradingServiceTest.java
  - broker/src/test/java/com/swingtrade/broker/service/DryRunServiceTest.java
autonomous: true
requirements_addressed: REQ-030, REQ-031, REQ-032, REQ-033
---

# Phase 6: Live Trading Test Suite

**Objective:** Create comprehensive unit tests for Kite Connect integration and broker services.

## Tasks

### Task 1: Create KiteConnectClientTest

<read_first>
- broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java
- broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java
- broker/src/main/java/com/swingtrade/broker/model/Order.java
- broker/src/main/java/com/swingtrade/broker/model/Position.java
- broker/src/main/java/com/swingtrade/broker/model/Portfolio.java
</read_first>

<action>
1. Create KiteConnectClientTest.java with at least 15 tests:
   - testPlaceOrder_success
   - testPlaceOrder_apiException
   - testCancelOrder_success
   - testCancelOrder_orderNotFound
   - testGetPortfolio_success
   - testGetPortfolio_apiException
   - testGetPositions_success
   - testGetPosition_found
   - testGetPosition_notFound
   - testCalculateProfitLoss_longPosition
   - testCalculateProfitLoss_shortPosition
   - testTestConnection_success
   - testTestConnection_connectionFailed
   - testGetMarketPrice_success
   - testSetAccessToken_success

2. Use MockRestServiceServer to mock vLLM/Kite API responses
3. Create test fixtures for Order, Position, Portfolio
4. Test with both live and sandbox environments
</action>

<acceptance_criteria>
- KiteConnectClientTest.java exists in broker/src/test/java/com/swingtrade/broker/kite/
- KiteConnectClientTest.java has @ExtendWith(MockitoExtension.class)
- KiteConnectClientTest.java has at least 15 test methods
- KiteConnectClientTest.java uses MockRestServiceServer for API mocking
- KiteConnectClientTest.java tests both success and failure scenarios
- KiteConnectClientTest.java tests OAuth token handling
- mvn test -pl broker -Dtest=KiteConnectClientTest passes
</acceptance_criteria>

### Task 2: Create BrokerServiceFactoryTest

<read_first>
- broker/src/test/java/com/swingtrade/broker/factory/BrokerServiceFactoryTest.java
- broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
- broker/src/main/java/com/swingtrade/broker/config/BrokerMode.java
</read_first>

<action>
1. Create BrokerServiceFactoryTest.java with at least 12 tests:
   - testCreateService_paperMode
   - testCreateService_liveMode
   - testCreateService_dryRunMode
   - testSwitchMode_paperToLive
   - testSwitchMode_liveToPaper
   - testSwitchMode_invalidMode
   - testValidateKiteConfig_valid
   - testValidateKiteConfig_missingApiKey
   - testValidateKiteConfig_missingAccessToken
   - testGetActiveService_paperMode
   - testGetActiveService_liveMode
   - testGetActiveService_dryRunMode

2. Test that factory creates correct service for each mode
3. Test that switchMode updates the active service
4. Test validation of Kite config when switching to LIVE
</action>

<acceptance_criteria>
- BrokerServiceFactoryTest.java exists in broker/src/test/java/com/swingtrade/broker/factory/
- BrokerServiceFactoryTest.java has @ExtendWith(MockitoExtension.class)
- BrokerServiceFactoryTest.java has at least 12 test methods
- BrokerServiceFactoryTest.java tests all three modes (PAPER, LIVE, DRY_RUN)
- BrokerServiceFactoryTest.java tests mode switching
- BrokerServiceFactoryTest.java tests Kite config validation
- mvn test -pl broker -Dtest=BrokerServiceFactoryTest passes
</acceptance_criteria>

### Task 3: Create LiveTradingServiceTest

<read_first>
- broker/src/test/java/com/swingtrade/broker/service/LiveTradingServiceTest.java
- broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java
- broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java
</read_first>

<action>
1. Create LiveTradingServiceTest.java with at least 15 tests:
   - testPlaceOrder_callsKiteClient
   - testPlaceOrder_validatesRiskControls
   - testPlaceOrder_riskLimitExceeded
   - testPlaceOrder_circuitBreakerActive
   - testPlaceOrder_killSwitchActive
   - testCancelOrder_callsKiteClient
   - testGetPortfolio_callsKiteClient
   - testGetPositions_callsKiteClient
   - testGetPosition_callsKiteClient
   - testCalculateProfitLoss_callsKiteClient
   - testGetMaxConcurrentPositions_returns3
   - testGetMaxCapitalPerPosition_returnsConfigValue
   - testPlaceOrder_apiException
   - testPlaceOrder_orderTimeout
   - testPlaceOrder_orderRejected

2. Mock KiteConnectClient and RiskControlsService
3. Test that LiveTradingService delegates to KiteConnectClient
4. Test that risk controls are enforced before order placement
</action>

<acceptance_criteria>
- LiveTradingServiceTest.java exists in broker/src/test/java/com/swingtrade/broker/service/
- LiveTradingServiceTest.java has @ExtendWith(MockitoExtension.class)
- LiveTradingServiceTest.java has at least 15 test methods
- LiveTradingServiceTest.java mocks KiteConnectClient
- LiveTradingServiceTest.java mocks RiskControlsService
- LiveTradingServiceTest.java tests risk control enforcement
- LiveTradingServiceTest.java tests API exception handling
- mvn test -pl broker -Dtest=LiveTradingServiceTest passes
</acceptance_criteria>

### Task 4: Create DryRunServiceTest

<read_first>
- broker/src/test/java/com/swingtrade/broker/service/DryRunServiceTest.java
- broker/src/main/java/com/swingtrade/broker/service/DryRunService.java
</read_first>

<action>
1. Create DryRunServiceTest.java with at least 12 tests:
   - testPlaceOrder_logsOrder
   - testPlaceOrder_doesNotExecute
   - testCancelOrder_logsCancellation
   - testCancelOrder_doesNotExecute
   - testGetPortfolio_returnsEmpty
   - testGetPositions_returnsEmpty
   - testGetPosition_returnsNull
   - testCalculateProfitLoss_returnsZero
   - testGetMaxConcurrentPositions_returns3
   - testGetMaxCapitalPerPosition_returnsConfigValue
   - testPlaceOrder_withRiskValidation
   - testDryRunMode_isSafeMode

2. Verify DryRunService logs all operations without executing
3. Test that DryRunService returns empty/safe values for queries
4. Test that risk controls are still validated in dry-run mode
</action>

<acceptance_criteria>
- DryRunServiceTest.java exists in broker/src/test/java/com/swingtrade/broker/service/
- DryRunServiceTest.java has @ExtendWith(MockitoExtension.class)
- DryRunServiceTest.java has at least 12 test methods
- DryRunServiceTest.java verifies order logging
- DryRunServiceTest.java verifies no actual execution
- DryRunServiceTest.java returns empty portfolio/positions
- DryRunServiceTest.java validates risk controls
- mvn test -pl broker -Dtest=DryRunServiceTest passes
</acceptance_criteria>

### Task 5: Run All Broker Tests and Verify Coverage

<read_first>
- broker/pom.xml
- target/site/jacoco/index.html (after running tests)
</read_first>

<action>
1. Run all broker module tests:
   mvn test -pl broker

2. Verify all tests pass:
   - KiteConnectClientTest
   - BrokerServiceFactoryTest
   - LiveTradingServiceTest
   - DryRunServiceTest
   - All existing broker tests

3. Generate JaCoCo coverage report:
   mvn jacoco:report -pl broker

4. Verify broker module has 80%+ coverage:
   - Check target/site/jacoco/com.swingtrade.broker/index.html
   - Verify KiteConnectClient has 80%+ coverage
   - Verify BrokerServiceFactory has 80%+ coverage
   - Verify LiveTradingService has 80%+ coverage
   - Verify DryRunService has 80%+ coverage
</action>

<acceptance_criteria>
- mvn test -pl broker passes with all tests
- All 4 new test classes pass
- All existing broker tests still pass
- JaCoCo report generated in target/site/jacoco/
- Broker module has 80%+ code coverage
- KiteConnectClient has 80%+ coverage
- BrokerServiceFactory has 80%+ coverage
- LiveTradingService has 80%+ coverage
- DryRunService has 80%+ coverage
</acceptance_criteria>
