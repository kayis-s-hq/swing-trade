---
wave: 1
depends_on: []
files_modified:
  - broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java
  - broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
  - broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java
  - broker/src/main/resources/application.properties
autonomous: true
requirements_addressed: REQ-030, REQ-031, REQ-032, REQ-033
---

# Phase 6: Live Trading Verification

**Objective:** Verify Kite Connect integration is production-ready and document Zerodha API setup steps.

## Tasks

### Task 1: Verify KiteConnectClient Implementation

<read_first>
- broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java
- broker/src/main/java/com/swingtrade/broker/kite/KiteConfig.java
</read_first>

<action>
1. Review KiteConnectClient.java and verify all required methods are implemented:
   - placeOrder(Order) - Returns OrderResponse
   - cancelOrder(String orderId) - Returns boolean
   - getPortfolio() - Returns Portfolio
   - getPositions() - Returns List of Positions
   - getPosition(String positionId) - Returns Position
   - calculateProfitLoss(Position) - Returns BigDecimal
   - testConnection() - Returns boolean
   - getMarketPrice(String symbol) - Returns Double

2. Verify KiteConnectClient uses:
   - KiteConnect API client from zerodha/kiteconnect library
   - Proper error handling for API exceptions
   - OAuth token management
   - Retry logic for transient failures

3. Check that KiteConfig has all required properties:
   - kite.api-key
   - kite.access-token
   - kite.environment (live/sandbox)
</action>

<acceptance_criteria>
- KiteConnectClient.java contains all 7 required methods (placeOrder, cancelOrder, getPortfolio, getPositions, getPosition, calculateProfitLoss, testConnection)
- KiteConnectClient.java imports com.zerodha.kiteconnect.KiteConnect
- KiteConnectClient.java has try-catch blocks for KiteApiException
- KiteConfig.java has @ConfigurationProperties prefix="kite"
- KiteConfig.java has fields: apiKey, accessToken, environment
</acceptance_criteria>

### Task 2: Verify BrokerServiceFactory Mode Switching

<read_first>
- broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java
- broker/src/main/java/com/swingtrade/broker/config/BrokerMode.java
</read_first>

<action>
1. Review BrokerServiceFactory.java and verify:
   - switchMode(BrokerMode) method exists
   - switchMode(String modeName) method exists
   - Creates correct service based on broker.mode property
   - Validates KiteConnect config when switching to LIVE mode

2. Verify BrokerMode enum has:
   - PAPER, LIVE, DRY_RUN values
   - allowsExecution() method (LIVE only allows execution)
   - isSafeMode() method (PAPER and DRY_RUN are safe)
   - getDescription() method

3. Check that switchMode updates the active broker service instance
</action>

<acceptance_criteria>
- BrokerServiceFactory.java has switchMode(BrokerMode) method
- BrokerServiceFactory.java has switchMode(String modeName) method
- BrokerMode.java has PAPER, LIVE, DRY_RUN enum values
- BrokerMode.java has allowsExecution() returning true only for LIVE
- BrokerMode.java has isSafeMode() returning true for PAPER and DRY_RUN
- BrokerServiceFactory validates kite.api-key when switching to LIVE mode
</acceptance_criteria>

### Task 3: Verify RiskControlsService Integration

<read_first>
- broker/src/main/java/com/swingtrade/broker/service/RiskControlsService.java
- broker/src/main/java/com/swingtrade/broker/service/PositionLimitChecker.java
- broker/src/main/java/com/swingtrade/broker/service/PositionSizeValidator.java
- broker/src/main/java/com/swingtrade/broker/service/DailyLossCircuitBreaker.java
</read_first>

<action>
1. Review RiskControlsService.java and verify:
   - Pre-trade validation before order placement
   - Checks position count against max-concurrent-positions
   - Checks position size against max-capital-per-position
   - Checks daily loss against circuit breaker threshold
   - Checks kill-switch status before allowing orders

2. Verify each risk control component:
   - PositionLimitChecker: Validates open positions <= max
   - PositionSizeValidator: Validates position size <= max per position
   - DailyLossCircuitBreaker: Validates daily P&L > threshold
   - KillSwitch: Validates kill-switch-active is false

3. Check that RiskControlsService is wired into BrokerServiceFactory
</action>

<acceptance_criteria>
- RiskControlsService.java has validateOrder(Order) method
- PositionLimitChecker.java checks open positions count
- PositionSizeValidator.java validates position size in rupees
- DailyLossCircuitBreaker.java tracks daily P&L from first trade
- RiskControlsService has isKillSwitchActive() method
- application.properties has broker.kill-switch-enabled=true
</acceptance_criteria>

### Task 4: Document Zerodha API Setup Steps

<read_first>
- broker/src/main/resources/application.properties
- .planning/phases/06-live-trading/06-01-live-trading-CONTEXT.md
</read_first>

<action>
1. Create CONTEXT.md with Zerodha API setup documentation:
   - How to register for Zerodha Kite Connect
   - Getting API key from Zerodha dashboard
   - OAuth flow for access token
   - Environment configuration (live vs sandbox)
   - Security best practices for API keys

2. Update application.properties with placeholder values:
   - kite.api-key=[YOUR_ZERODHA_API_KEY]
   - kite.access-token=[YOUR_ACCESS_TOKEN_FROM_OAUTH]
   - kite.environment=live

3. Document the activation steps:
   - Step 1: Set broker.mode=dry_run for testing
   - Step 2: Configure kite.api-key and kite.access-token
   - Step 3: Test with broker.mode=dry_run (logs orders)
   - Step 4: Switch to broker.mode=live for real trading
</action>

<acceptance_criteria>
- 06-01-live-trading-CONTEXT.md exists with Zerodha setup documentation
- CONTEXT.md includes section "Zerodha Kite Connect Setup"
- CONTEXT.md has OAuth flow explanation
- CONTEXT.md has security best practices for API keys
- application.properties has placeholder values for kite.api-key and kite.access-token
</acceptance_criteria>

### Task 5: Verify Application Properties Configuration

<read_first>
- broker/src/main/resources/application.properties
</read_first>

<action>
1. Verify application.properties has all required broker configuration:
   - broker.mode=dry_run (default for safety)
   - broker.max-concurrent-positions=3 (live mode limit)
   - broker.max-capital-per-position=16666 (20% of 50K capital)
   - broker.daily-loss-circuit-breaker=2.0 (2% daily loss limit)
   - broker.kill-switch-enabled=true
   - kite.api-key=[placeholder]
   - kite.access-token=[placeholder]
   - kite.environment=live

2. Verify configuration is properly bound to @ConfigurationProperties classes
</action>

<acceptance_criteria>
- application.properties has broker.mode property
- application.properties has broker.max-concurrent-positions=3
- application.properties has broker.max-capital-per-position=16666
- application.properties has broker.daily-loss-circuit-breaker=2.0
- application.properties has broker.kill-switch-enabled=true
- application.properties has kite.api-key placeholder
- application.properties has kite.access-token placeholder
- application.properties has kite.environment=live
</acceptance_criteria>
