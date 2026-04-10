---
phase: 6
plan: 02
type: testing
depends_on:
  - 06-01-live-trading
files_modified:
  - broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java
  - broker/src/test/java/com/swingtrade/broker/risk/KillSwitchServiceTest.java
  - broker/src/test/java/com/swingtrade/broker/risk/CapitalTrackerTest.java
  - broker/src/test/java/com/swingtrade/broker/factory/BrokerServiceFactoryTest.java
  - broker/src/test/java/com/swingtrade/broker/service/LiveTradingServiceTest.java
  - broker/src/test/java/com/swingtrade/broker/service/DryRunServiceTest.java
  - api/src/test/java/com/swingtrade/api/controller/AdminControllerTest.java
  - api/src/test/java/com/swingtrade/api/dto/KillSwitchRequestTest.java
autonomous: true
requirements_addressed: REQ-030, REQ-031, REQ-032, REQ-033

# WORKTREE WORKFLOW ENFORCEMENT
# CRITICAL: This plan MUST be executed in a git worktree environment
# Root .planning/ is source of truth - worktree .planning/ is working copy
worktree_enforcement:
  required: true
  reason: "Prevents direct edits to root .planning/ on main branch"
  workflow:
    - step: 1
      action: "Verify worktree directory"
      command: "pwd | grep worktrees"
      fail_message: "ERROR: Must be in a worktree directory (e.g., .claude/worktrees/phase-05/)"
    - step: 2
      action: "Verify worktree branch"
      command: "git branch --show-current"
      expected_pattern: "worktree-phase-.*"
      fail_message: "ERROR: Must be on a worktree branch (e.g., worktree-phase-05)"
    - step: 3
      action: "Edit planning docs in worktree"
      path: ".planning/phases/06-live-trading/"
      note: "Do NOT edit .planning/ in root repository"
    - step: 4
      action: "Sync to root before merge"
      command: "Skill(\"superpowers:gsd-worktree-workflow --sync-to-root\")"
      when: "Before merging worktree branch to main"
    - step: 5
      action: "Verify before merge"
      command: "gsd:verify"
      when: "After plan completion, before merge"
requirements_addressed: REQ-030, REQ-031, REQ-032, REQ-033
must_haves:
  truths:
    - All broker tests pass (mvn test -pl broker)
    - All API tests pass (mvn test -pl api)
    - KiteConnectClientTest has 15+ test methods
    - KillSwitchServiceTest has 10+ test methods
    - CapitalTrackerTest has 12+ test methods
    - BrokerServiceFactoryTest has 12+ test methods
    - AdminControllerTest has 8+ test methods
    - JaCoCo coverage for broker module >= 80%
    - JaCoCo coverage for api module >= 80%
  artifacts:
    - broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java
    - broker/src/test/java/com/swingtrade/broker/risk/KillSwitchServiceTest.java
    - broker/src/test/java/com/swingtrade/broker/risk/CapitalTrackerTest.java
    - broker/src/test/java/com/swingtrade/broker/factory/BrokerServiceFactoryTest.java
    - broker/src/test/java/com/swingtrade/broker/service/LiveTradingServiceTest.java
    - broker/src/test/java/com/swingtrade/broker/service/DryRunServiceTest.java
    - api/src/test/java/com/swingtrade/api/controller/AdminControllerTest.java
    - api/src/test/java/com/swingtrade/api/dto/KillSwitchRequestTest.java
  key_links:
    - Tests use Mockito for dependency mocking
    - Tests verify risk control enforcement
    - Tests cover success and failure scenarios
    - AdminControllerTest mocks KillSwitchService
    - AdminControllerTest tests POST /api/admin/kill-switch endpoint
---

# Phase 6 Plan 02: Live Trading Test Suite

**Objective:** Create comprehensive unit tests for Kite Connect integration, Kill Switch, and Capital Management.

## Tasks

### Task 1: Create KiteConnectClientTest and KillSwitchServiceTest

<task type="auto">
<read_first>
- broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java (existing stub)
- broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java
- broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java
</read_first>

<action>
1. Create KiteConnectClientTest.java with 15+ tests:
   - @ExtendWith(MockitoExtension.class)
   - testPlaceOrder_success - mocks KiteConnect.placeOrder()
   - testPlaceOrder_apiException - tests error handling
   - testCancelOrder_success
   - testCancelOrder_orderNotFound
   - testGetPortfolio_success
   - testGetPortfolio_apiException
   - testGetPositions_success
   - testGetMarketPrice_success
   - testTestConnection_success
   - testSetAccessToken_success
   - testGenerateLoginUrl
   - testTokenRefresh
   - testRetryLogic_onTransientError
   - testApiKeyValidation
   - testEnvironmentConfiguration

2. Create KillSwitchServiceTest.java with 10+ tests:
   - testEnableKillSwitch_setsActive
   - testDisableKillSwitch_setsInactive
   - testIsActive_initiallyFalse
   - testEnableThenDisable
   - testPersistence_toDatabase
   - testThreadSafety_concurrentAccess
   - testLogging_onEnable
   - testLogging_onDisable
   - testSerialization
   - testDeserialization
</action>

<verify>
# Verify KiteConnectClientTest
test -f broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java && echo "KiteConnectClientTest exists" || echo "KiteConnectClientTest missing"
grep -c "@Test" broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java | xargs -I {} test {} -ge 15 && echo "15+ tests found" || echo "Less than 15 tests"
grep -q "@ExtendWith(MockitoExtension.class)" broker/src/test/java/com/swingtrade/broker/kite/KiteConnectClientTest.java && echo "MockitoExtension found" || echo "MockitoExtension missing"

# Verify KillSwitchServiceTest
test -f broker/src/test/java/com/swingtrade/broker/risk/KillSwitchServiceTest.java && echo "KillSwitchServiceTest exists" || echo "KillSwitchServiceTest missing"
grep -c "@Test" broker/src/test/java/com/swingtrade/broker/risk/KillSwitchServiceTest.java | xargs -I {} test {} -ge 10 && echo "10+ tests found" || echo "Less than 10 tests"
grep -q "testEnableKillSwitch_setsActive" broker/src/test/java/com/swingtrade/broker/risk/KillSwitchServiceTest.java && echo "testEnableKillSwitch_setsActive found" || echo "testEnableKillSwitch_setsActive missing"
</verify>

<done>
- KiteConnectClientTest.java has 15+ @Test methods
- KillSwitchServiceTest.java has 10+ @Test methods
- Both use @ExtendWith(MockitoExtension.class)
- mvn test -pl broker -Dtest=KiteConnectClientTest,KillSwitchServiceTest passes
</done>
</task>

### Task 2: Create AdminControllerTest and KillSwitchRequestTest

<task type="auto">
<read_first>
- api/src/test/java/com/swingtrade/api/controller/AdminControllerTest.java (new)
- api/src/main/java/com/swingtrade/api/controller/AdminController.java
- api/src/main/java/com/swingtrade/api/dto/KillSwitchRequest.java
- broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java
</read_first>

<action>
1. Create KillSwitchRequestTest.java with 6+ tests:
   - testEnabledField_exists
   - testReasonField_exists
   - testRecord_immutable
   - testJsonSerialization
   - testJsonDeserialization
   - testValidation_enabledRequired

2. Create AdminControllerTest.java with 8+ tests:
   - @ExtendWith(SpringExtension.class)
   - @AutoConfigureMockMvc
   - testKillSwitchEndpoint_enablesSwitch
   - testKillSwitchEndpoint_disablesSwitch
   - testKillSwitchEndpoint_withReason
   - testKillSwitchStatusEndpoint_returnsActive
   - testKillSwitchStatusEndpoint_returnsInactive
   - testUnauthorizedAccess_throwsException
   - testInvalidJson_returnsBadRequest
   - testControllerInjection_valid
</action>

<verify>
# Verify KillSwitchRequestTest
test -f api/src/test/java/com/swingtrade/api/dto/KillSwitchRequestTest.java && echo "KillSwitchRequestTest exists" || echo "KillSwitchRequestTest missing"
grep -c "@Test" api/src/test/java/com/swingtrade/api/dto/KillSwitchRequestTest.java | xargs -I {} test {} -ge 6 && echo "6+ tests found" || echo "Less than 6 tests"

# Verify AdminControllerTest
test -f api/src/test/java/com/swingtrade/api/controller/AdminControllerTest.java && echo "AdminControllerTest exists" || echo "AdminControllerTest missing"
grep -c "@Test" api/src/test/java/com/swingtrade/api/controller/AdminControllerTest.java | xargs -I {} test {} -ge 8 && echo "8+ tests found" || echo "Less than 8 tests"
grep -q "@AutoConfigureMockMvc" api/src/test/java/com/swingtrade/api/controller/AdminControllerTest.java && echo "@AutoConfigureMockMvc found" || echo "@AutoConfigureMockMvc missing"
grep -q "testKillSwitchEndpoint_enablesSwitch" api/src/test/java/com/swingtrade/api/controller/AdminControllerTest.java && echo "testKillSwitchEndpoint_enablesSwitch found" || echo "testKillSwitchEndpoint_enablesSwitch missing"
</verify>

<done>
- KillSwitchRequestTest.java has 6+ @Test methods
- AdminControllerTest.java has 8+ @Test methods
- AdminControllerTest uses @AutoConfigureMockMvc
- mvn test -pl api -Dtest=AdminControllerTest,KillSwitchRequestTest passes
</done>
</task>

### Task 3: Create CapitalTrackerTest and BrokerServiceFactoryTest

<task type="auto">
<read_first>
- broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java
- broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
</read_first>

<action>
1. Create CapitalTrackerTest.java with 12+ tests:
   - testInitialCapital_is50000
   - testMaxPositions_is3
   - testMaxPerPosition_is10000
   - testEnforceLimits_positionCountExceeded
   - testEnforceLimits_capitalExceeded
   - testEnforceLimits_validOrder
   - testGetCurrentPositions_empty
   - testGetCurrentPositions_withPositions
   - testGetAvailableCapital
   - testDeployCapital_updatesTracking
   - testReleaseCapital_updatesTracking
   - testThreadSafety_concurrentUpdates

2. Create BrokerServiceFactoryTest.java with 12+ tests:
   - testCreateService_paperMode
   - testCreateService_liveMode
   - testCreateService_dryRunMode
   - testSwitchMode_paperToLive
   - testSwitchMode_liveToPaper
   - testSwitchMode_dryRunToLive
   - testValidateKiteConfig_valid
   - testValidateKiteConfig_missingApiKey
   - testValidateKiteConfig_missingAccessToken
   - testGetActiveService_paperMode
   - testGetActiveService_liveMode
   - testGetActiveService_dryRunMode
</action>

<verify>
# Verify CapitalTrackerTest
test -f broker/src/test/java/com/swingtrade/broker/risk/CapitalTrackerTest.java && echo "CapitalTrackerTest exists" || echo "CapitalTrackerTest missing"
grep -c "@Test" broker/src/test/java/com/swingtrade/broker/risk/CapitalTrackerTest.java | xargs -I {} test {} -ge 12 && echo "12+ tests found" || echo "Less than 12 tests"
grep -q "testEnforceLimits_positionCountExceeded" broker/src/test/java/com/swingtrade/broker/risk/CapitalTrackerTest.java && echo "testEnforceLimits_positionCountExceeded found" || echo "testEnforceLimits_positionCountExceeded missing"

# Verify BrokerServiceFactoryTest
test -f broker/src/test/java/com/swingtrade/broker/factory/BrokerServiceFactoryTest.java && echo "BrokerServiceFactoryTest exists" || echo "BrokerServiceFactoryTest missing"
grep -c "@Test" broker/src/test/java/com/swingtrade/broker/factory/BrokerServiceFactoryTest.java | xargs -I {} test {} -ge 12 && echo "12+ tests found" || echo "Less than 12 tests"
grep -q "testSwitchMode_paperToLive" broker/src/test/java/com/swingtrade/broker/factory/BrokerServiceFactoryTest.java && echo "testSwitchMode_paperToLive found" || echo "testSwitchMode_paperToLive missing"
</verify>

<done>
- CapitalTrackerTest.java has 12+ @Test methods
- BrokerServiceFactoryTest.java has 12+ @Test methods
- mvn test -pl broker -Dtest=CapitalTrackerTest,BrokerServiceFactoryTest passes
</done>
</task>

### Task 4: Create LiveTradingServiceTest and DryRunServiceTest

<task type="auto">
<read_first>
- broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java
- broker/src/main/java/com/swingtrade/broker/service/DryRunService.java
</read_first>

<action>
1. Create LiveTradingServiceTest.java with 15+ tests:
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

2. Create DryRunServiceTest.java with 12+ tests:
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
</action>

<verify>
# Verify LiveTradingServiceTest
test -f broker/src/test/java/com/swingtrade/broker/service/LiveTradingServiceTest.java && echo "LiveTradingServiceTest exists" || echo "LiveTradingServiceTest missing"
grep -c "@Test" broker/src/test/java/com/swingtrade/broker/service/LiveTradingServiceTest.java | xargs -I {} test {} -ge 15 && echo "15+ tests found" || echo "Less than 15 tests"
grep -q "testPlaceOrder_killSwitchActive" broker/src/test/java/com/swingtrade/broker/service/LiveTradingServiceTest.java && echo "testPlaceOrder_killSwitchActive found" || echo "testPlaceOrder_killSwitchActive missing"

# Verify DryRunServiceTest
test -f broker/src/test/java/com/swingtrade/broker/service/DryRunServiceTest.java && echo "DryRunServiceTest exists" || echo "DryRunServiceTest missing"
grep -c "@Test" broker/src/test/java/com/swingtrade/broker/service/DryRunServiceTest.java | xargs -I {} test {} -ge 12 && echo "12+ tests found" || echo "Less than 12 tests"
grep -q "testPlaceOrder_doesNotExecute" broker/src/test/java/com/swingtrade/broker/service/DryRunServiceTest.java && echo "testPlaceOrder_doesNotExecute found" || echo "testPlaceOrder_doesNotExecute missing"
</verify>

<done>
- LiveTradingServiceTest.java has 15+ @Test methods
- DryRunServiceTest.java has 12+ @Test methods
- mvn test -pl broker -Dtest=LiveTradingServiceTest,DryRunServiceTest passes
</done>
</task>

### Task 5: Run All Broker and API Tests and Verify Coverage

<task type="auto">
<read_first>
- broker/pom.xml
- api/pom.xml
- target/site/jacoco/index.html (after running tests)
</read_first>

<action>
1. Run all broker module tests:
   mvn test -pl broker

2. Run all API module tests:
   mvn test -pl api

3. Verify all tests pass:
   - All broker tests (KiteConnectClientTest, KillSwitchServiceTest, CapitalTrackerTest, BrokerServiceFactoryTest, LiveTradingServiceTest, DryRunServiceTest)
   - All API tests (AdminControllerTest, KillSwitchRequestTest)
   - All existing tests

4. Generate JaCoCo coverage reports:
   mvn jacoco:report -pl broker,api

5. Verify coverage:
   - Broker module has 80%+ code coverage
   - API module has 80%+ code coverage
   - Check target/site/jacoco/com.swingtrade.broker/index.html
   - Check target/site/jacoco/com.swingtrade.api/index.html
</action>

<verify>
# Run all broker tests
cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade
mvn test -pl broker -q

# Run all API tests
mvn test -pl api -q

# Check test results
grep -q "BUILD SUCCESS" target/surefire-reports/*.txt && echo "All tests passed" || echo "Tests failed"

# Generate coverage reports
mvn jacoco:report -pl broker,api -q

# Verify coverage (check jacoco report for 80%+)
test -f broker/target/site/jacoco/com.swingtrade.broker/index.html && echo "Broker Jacoco report exists" || echo "Broker Jacoco report missing"
test -f api/target/site/jacoco/com.swingtrade.api/index.html && echo "API Jacoco report exists" || echo "API Jacoco report missing"
</verify>

<done>
- mvn test -pl broker passes with all tests
- mvn test -pl api passes with all tests
- All new test classes pass (8 total)
- JaCoCo reports generated in broker/target/site/jacoco/ and api/target/site/jacoco/
- Broker module has 80%+ code coverage
- API module has 80%+ code coverage
</done>
</task>
