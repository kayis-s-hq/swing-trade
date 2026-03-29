---
phase: 6
plan: 01
type: implementation
depends_on: []
files_modified:
  - broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java
  - broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
  - broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java
  - broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java
  - broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java
  - broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java
  - api/src/main/java/com/swingtrade/api/controller/AdminController.java
  - api/src/main/java/com/swingtrade/api/dto/KillSwitchRequest.java
  - broker/src/main/resources/application.properties
autonomous: true
requirements_addressed: REQ-030, REQ-031, REQ-032, REQ-033
must_haves:
  truths:
    - KiteConnectClient.java implements all Kite Connect API methods with real SDK calls
    - KillSwitchService.java exists with enable/disable/isActive methods
    - CapitalTracker.java exists with enforceLimits method
    - AdminController.java exists with POST /api/admin/kill-switch endpoint
    - BrokerServiceFactory switches to LiveTradingService when mode=live
    - application.properties has kite.api-key and kite.access-token placeholders
  artifacts:
    - broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java
    - broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java
    - broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java
    - broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
    - api/src/main/java/com/swingtrade/api/controller/AdminController.java
    - api/src/main/java/com/swingtrade/api/dto/KillSwitchRequest.java
  key_links:
    - KillSwitchService wired into RiskControlsService.validateOrder
    - CapitalTracker called before every order placement
    - BrokerServiceFactory uses KillSwitchService.isActive() before live mode execution
    - AdminController injects KillSwitchService
    - POST /api/admin/kill-switch calls KillSwitchService.enableKillSwitch()
---

# Phase 6 Plan 01: Live Trading Implementation

**Objective:** Implement Kite Connect integration, Kill Switch, and Capital Management for live trading.

## Tasks

### Task 1: Implement KiteConnectClient with Real SDK

<task type="auto">
<read_first>
- broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java (current stub)
- broker/src/main/java/com/swingtrade/broker/kite/KiteConfig.java
</read_first>

<action>
1. Replace stub implementation with real Zerodha KiteConnect SDK:
   - Add dependency: com.zerodha:kiteconnect:5.0.0 (or latest)
   - Implement placeOrder() with KiteConnect.placeOrder()
   - Implement cancelOrder() with KiteConnect.cancelOrder()
   - Implement getPortfolio() with KiteConnect.getPortfolio()
   - Implement getPositions() with KiteConnect.getHoldings()
   - Implement getMarketPrice() with KiteConnect.quote()
   - Implement testConnection() by fetching user profile

2. Add error handling:
   - Catch KiteApiException and convert to BusinessException
   - Add retry logic for transient failures (exponential backoff)
   - Log all API calls with request/response details

3. Add OAuth token management:
   - Store access token in secure location
   - Implement token refresh mechanism
   - Validate token before each API call
</action>

<verify>
# Verify KiteConnectClient implementation
cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade
mvn compile -pl broker -q

# Check SDK import exists
grep -q "import com.zerodha.kiteconnect.KiteConnect" broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java && echo "SDK import found" || echo "SDK import missing"

# Check all required methods implemented
grep -q "public OrderResponse placeOrder" broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java && echo "placeOrder found" || echo "placeOrder missing"
grep -q "public boolean cancelOrder" broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java && echo "cancelOrder found" || echo "cancelOrder missing"
grep -q "public Portfolio getPortfolio" broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java && echo "getPortfolio found" || echo "getPortfolio missing"
grep -q "public List<Position> getPositions" broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java && echo "getPositions found" || echo "getPositions missing"
grep -q "public BigDecimal getMarketPrice" broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java && echo "getMarketPrice found" || echo "getMarketPrice missing"
</verify>

<done>
- KiteConnectClient.java imports com.zerodha.kiteconnect.KiteConnect
- All 5 required methods implemented (placeOrder, cancelOrder, getPortfolio, getPositions, getMarketPrice)
- KiteApiException handling with retry logic
- Maven compile succeeds with no errors
</done>
</task>

### Task 2: Implement KillSwitchService and CapitalTracker

<task type="auto">
<read_first>
- broker/src/main/java/com/swingtrade/broker/risk/RiskControls.java
- broker/src/main/resources/application.properties
</read_first>

<action>
1. Create KillSwitchService.java:
   - Field: private boolean active = false
   - Method: enableKillSwitch() - sets active=true, logs event
   - Method: disableKillSwitch() - sets active=false, logs event
   - Method: isActive() - returns boolean
   - Persist state to database (kill_switch table)
   - Add @Component annotation

2. Create CapitalTracker.java:
   - Field: private final BigDecimal INITIAL_CAPITAL = new BigDecimal("50000")
   - Field: private final int MAX_POSITIONS = 3
   - Field: private final BigDecimal MAX_PER_POSITION = new BigDecimal("10000") (20% of 50K)
   - Method: enforceLimits(List<Position>, BigDecimal orderValue) returns RiskCheckResult
   - Method: getCurrentPositions() returns List<Position>
   - Method: getAvailableCapital() returns BigDecimal
   - Track deployed capital per position
</action>

<verify>
# Verify KillSwitchService exists
test -f broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java && echo "KillSwitchService exists" || echo "KillSwitchService missing"
grep -q "public void enableKillSwitch" broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java && echo "enableKillSwitch found" || echo "enableKillSwitch missing"
grep -q "public void disableKillSwitch" broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java && echo "disableKillSwitch found" || echo "disableKillSwitch missing"
grep -q "public boolean isActive" broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java && echo "isActive found" || echo "isActive missing"

# Verify CapitalTracker exists
test -f broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java && echo "CapitalTracker exists" || echo "CapitalTracker missing"
grep -q "private final BigDecimal INITIAL_CAPITAL" broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java && echo "INITIAL_CAPITAL found" || echo "INITIAL_CAPITAL missing"
grep -q "private final int MAX_POSITIONS" broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java && echo "MAX_POSITIONS found" || echo "MAX_POSITIONS missing"
grep -q "public RiskCheckResult enforceLimits" broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java && echo "enforceLimits found" || echo "enforceLimits missing"
</verify>

<done>
- KillSwitchService.java created with enable/disable/isActive methods
- CapitalTracker.java created with enforceLimits method
- INITIAL_CAPITAL=50000, MAX_POSITIONS=3, MAX_PER_POSITION=10000 configured
- Maven compile succeeds
</done>
</task>

### Task 3: Implement LiveTradingService for BrokerServiceFactory Routing

<task type="auto">
<read_first>
- broker/src/main/java/com/swingtrade/broker/service/PaperTradingServiceImpl.java
- broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
- broker/src/main/java/com/swingtrade/broker/service/BrokerService.java
</read_first>

<action>
1. Create LiveTradingService.java:
   - Implement BrokerService interface
   - Inject KiteConnectClient
   - Inject KillSwitchService
   - Inject CapitalTracker
   - Implement placeOrder() - delegates to KiteConnectClient, validates risk controls
   - Implement cancelOrder() - delegates to KiteConnectClient
   - Implement getPortfolio() - delegates to KiteConnectClient
   - Implement getPositions() - delegates to KiteConnectClient
   - Implement closePosition() - delegates to KiteConnectClient
   - Implement getMaxConcurrentPositions() - returns 3
   - Implement getMaxCapitalPerPosition() - returns config value (10000)

2. Update BrokerServiceFactory.java:
   - Add switchMode(BrokerMode) method
   - Add switchMode(String modeName) method
   - Implement factory pattern:
     - PAPER -> PaperTradingServiceImpl
     - LIVE -> LiveTradingService
     - DRY_RUN -> DryRunService
   - In switchToLiveMode(): validate kite.api-key and kite.access-token configured
   - Inject KillSwitchService to check isActive() before allowing LIVE mode switch
</action>

<verify>
# Verify LiveTradingService exists
test -f broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java && echo "LiveTradingService exists" || echo "LiveTradingService missing"
grep -q "implements BrokerService" broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java && echo "implements BrokerService found" || echo "implements BrokerService missing"
grep -q "private final KiteConnectClient" broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java && echo "KiteConnectClient injection found" || echo "KiteConnectClient injection missing"
grep -q "public OrderResponse placeOrder" broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java && echo "placeOrder found" || echo "placeOrder missing"
grep -q "public int getMaxConcurrentPositions" broker/src/main/java/com/swingtrade/broker/service/LiveTradingService.java && echo "getMaxConcurrentPositions found" || echo "getMaxConcurrentPositions missing"

# Verify BrokerServiceFactory routing
grep -q "public void switchMode" broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java && echo "switchMode found" || echo "switchMode missing"
grep -q "case LIVE" broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java && echo "LIVE case found" || echo "LIVE case missing"
grep -q "new LiveTradingService" broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java && echo "LiveTradingService instantiation found" || echo "LiveTradingService instantiation missing"
</verify>

<done>
- LiveTradingService.java created implementing BrokerService interface
- LiveTradingService delegates to KiteConnectClient for all broker operations
- BrokerServiceFactory.java has switchMode() method with PAPER/LIVE/DRY_RUN routing
- BrokerServiceFactory validates kill switch before allowing LIVE mode switch
- Maven compile succeeds
</done>
</task>

### Task 4: Wire Kill Switch and Capital Tracker into Risk Controls

<task type="auto">
<read_first>
- broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java
- broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
</read_first>

<action>
1. Update RiskControlsService.java:
   - Inject KillSwitchService
   - Inject CapitalTracker
   - In validateOrder(Order):
     - Check kill switch: if killSwitch.isActive() throw KillSwitchActiveException
     - Check capital limits: RiskCheckResult result = capitalTracker.enforceLimits(positions, orderValue)
     - If !result.isValid(), throw RiskLimitExceededException

2. Update BrokerServiceFactory.java:
   - Inject KillSwitchService
   - In switchToLiveMode(): validate kill switch is not active before allowing switch
   - Document mode switching requirements

3. Update application.properties:
   - broker.kill-switch-enabled=true
   - broker.initial-capital=50000
   - broker.max-concurrent-positions=3
   - broker.max-capital-per-position=10000
</action>

<verify>
# Verify RiskControlsService integration
grep -q "private final KillSwitchService" broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java && echo "KillSwitchService injection found" || echo "KillSwitchService injection missing"
grep -q "private final CapitalTracker" broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java && echo "CapitalTracker injection found" || echo "CapitalTracker injection missing"
grep -q "killSwitchService.isActive()" broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java && echo "kill switch check found" || echo "kill switch check missing"
grep -q "capitalTracker.enforceLimits" broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java && echo "capital tracker check found" || echo "capital tracker check missing"

# Verify application.properties
grep -q "broker.kill-switch-enabled=true" broker/src/main/resources/application.properties && echo "kill-switch-enabled found" || echo "kill-switch-enabled missing"
grep -q "broker.max-concurrent-positions=3" broker/src/main/resources/application.properties && echo "max-concurrent-positions found" || echo "max-concurrent-positions missing"
grep -q "broker.max-capital-per-position=10000" broker/src/main/resources/application.properties && echo "max-capital-per-position found" || echo "max-capital-per-position missing"
</verify>

<done>
- RiskControlsService injects KillSwitchService and CapitalTracker
- validateOrder() checks kill switch and capital limits
- application.properties has all required configuration
- Maven compile succeeds
</done>
</task>

### Task 5: Create AdminController for Kill Switch API Endpoint

<task type="auto">
<read_first>
- api/src/main/java/com/swingtrade/api/controller/
- broker/src/main/java/com/swingtrade/broker/risk/KillSwitchService.java
</read_first>

<action>
1. Create KillSwitchRequest.java DTO:
   - Record with fields: enabled (boolean), reason (String)
   - Add @Validated annotation
   - Add @JsonProperty annotations for JSON serialization

2. Create AdminController.java:
   - @RestController
   - @RequestMapping("/api/admin")
   - Inject KillSwitchService
   - POST /kill-switch:
     - Accept KillSwitchRequest
     - Call killSwitchService.enableKillSwitch() or disableKillSwitch()
     - Return ApiResponse with status
     - Add @PreAuthorize("hasRole('ADMIN')") for security
   - GET /kill-switch/status:
     - Return current kill switch status
     - Return {active: boolean, enabledAt: timestamp}

3. Wire AdminController into API module:
   - Ensure controller is scanned by Spring (in api module)
   - Add configuration for admin role if needed
</action>

<verify>
# Verify KillSwitchRequest DTO
test -f api/src/main/java/com/swingtrade/api/dto/KillSwitchRequest.java && echo "KillSwitchRequest exists" || echo "KillSwitchRequest missing"
grep -q "record KillSwitchRequest" api/src/main/java/com/swingtrade/api/dto/KillSwitchRequest.java && echo "KillSwitchRequest record found" || echo "KillSwitchRequest record missing"
grep -q "boolean enabled" api/src/main/java/com/swingtrade/api/dto/KillSwitchRequest.java && echo "enabled field found" || echo "enabled field missing"

# Verify AdminController
test -f api/src/main/java/com/swingtrade/api/controller/AdminController.java && echo "AdminController exists" || echo "AdminController missing"
grep -q "@RestController" api/src/main/java/com/swingtrade/api/controller/AdminController.java && echo "@RestController found" || echo "@RestController missing"
grep -q '@RequestMapping("/api/admin")' api/src/main/java/com/swingtrade/api/controller/AdminController.java && echo "@RequestMapping found" || echo "@RequestMapping missing"
grep -q "public void killSwitch" api/src/main/java/com/swingtrade/api/controller/AdminController.java && echo "killSwitch endpoint found" || echo "killSwitch endpoint missing"
grep -q "private final KillSwitchService" api/src/main/java/com/swingtrade/api/controller/AdminController.java && echo "KillSwitchService injection found" || echo "KillSwitchService injection missing"
</verify>

<done>
- KillSwitchRequest.java created with enabled and reason fields
- AdminController.java created with POST /api/admin/kill-switch endpoint
- AdminController.java created with GET /api/admin/kill-switch/status endpoint
- AdminController injects KillSwitchService
- Maven compile succeeds
</done>
</task>
