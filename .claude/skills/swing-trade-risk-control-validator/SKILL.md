---
name: swing-trade-risk-control-validator
description: Validate and test risk control parameters and circuit breaker thresholds for the swing-trade system. Use this skill when modifying risk parameters, before deploying auto-trade features, after significant market volatility, or when position limits need adjustment. This skill is critical for trading system safety and should be triggered proactively whenever risk management settings change to ensure capital protection mechanisms are properly configured.
---

# SwingTrade Risk Control Validator Skill

## Overview

This skill automates the validation and testing of risk control mechanisms in the swing-trade system. It ensures that circuit breakers, position limits, and other risk controls are properly configured and effective at protecting capital.

## When to Use This Skill

Trigger this skill when:
- Modifying risk parameter thresholds
- Before deploying auto-trade features
- After significant market volatility events
- When position limits need adjustment
- Quarterly risk management review
- After implementing new risk controls
- Before increasing trading capital

## Risk Control Components

### 1. Kill Switch Validation

```java
public class KillSwitchValidator {
    // Validate kill switch configuration
    public ValidationResult validateKillSwitch(KillSwitchConfig config) {
        return ValidationResult.builder()
            .check("kill_switch_active")
            .message("Kill switch is " + (config.isActive() ? "ACTIVE" : "INACTIVE"))
            .severity(config.isActive() ? "CRITICAL" : "INFO")
            .build();
    }
}
```

**Validation Criteria:**
- Kill switch must be explicitly enabled/disabled (no default)
- Emergency contact must be configured when active
- Kill switch status must be logged to monitoring

### 2. Daily Loss Circuit Breaker

```java
public class DailyLossCircuitBreakerValidator {
    // Validate daily loss threshold
    public ValidationResult validateDailyLossThreshold(DailyLossConfig config) {
        double threshold = config.getThresholdPercent();
        double currentLoss = getCurrentDailyLoss();

        return ValidationResult.builder()
            .check("daily_loss_threshold")
            .message("Threshold: " + threshold + "%, Current: " + currentLoss + "%")
            .severity(threshold <= 0 || threshold >= 10 ? "CRITICAL" : "INFO")
            .recommendation("Recommended range: 2-5%")
            .build();
    }
}
```

**Validation Criteria:**
- Threshold must be between 1% and 10%
- Threshold should be proportional to total capital
- Daily reset time must be configured (IST 00:00)

### 3. Position Limit Validation

```java
public class PositionLimitValidator {
    // Validate position limits
    public ValidationResult validatePositionLimits(PositionLimitConfig config) {
        int maxPositions = config.getMaxConcurrentPositions();
        int currentPositions = getCurrentPositionCount();

        return ValidationResult.builder()
            .check("position_limit")
            .message("Max: " + maxPositions + ", Current: " + currentPositions)
            .severity(maxPositions < 1 || maxPositions > 20 ? "CRITICAL" : "INFO")
            .recommendation("Recommended: 5-10 positions for diversification")
            .build();
    }
}
```

**Validation Criteria:**
- Max positions between 3 and 20
- Capital per position limit (default 20%)
- Sector concentration limits (optional)

### 4. Position Size Validator

```java
public class PositionSizeValidator {
    // Validate position sizing
    public ValidationResult validatePositionSize(PositionSizeConfig config) {
        double maxCapitalPercent = config.getMaxCapitalPercent();
        double atrMultiplier = config.getAtrMultiplier();

        return ValidationResult.builder()
            .check("position_size")
            .message("Max capital: " + maxCapitalPercent + "%, ATR multiplier: " + atrMultiplier)
            .severity(maxCapitalPercent > 30 || atrMultiplier < 1.5 ? "WARNING" : "INFO")
            .recommendation("Recommended: 15-25% capital, 2x ATR stop")
            .build();
    }
}
```

**Validation Criteria:**
- Capital per position: 10-30%
- ATR multiplier for stop loss: 1.5-3x
- Minimum position value (optional)

## Risk Control Test Scenarios

### Scenario 1: Circuit Breaker Trigger Test

```java
@Test
void testDailyLossCircuitBreaker_Triggered() {
    // Setup: Simulate daily loss > threshold
    setDailyLoss(-6.0); // 6% loss

    // Execute: Attempt to place trade
    TradeResult result = tradingEngine.placeOrder(request);

    // Verify: Trade should be blocked
    assertThat(result.isBlocked()).isTrue();
    assertThat(result.getReason()).isEqualTo("DAILY_LOSS_LIMIT_REACHED");
}
```

### Scenario 2: Position Limit Test

```java
@Test
void testPositionLimit_MaxReached() {
    // Setup: Max positions reached
    createPositions(10); // Max limit

    // Execute: Attempt to open new position
    TradeResult result = tradingEngine.placeOrder(request);

    // Verify: Trade should be blocked
    assertThat(result.isBlocked()).isTrue();
    assertThat(result.getReason()).isEqualTo("POSITION_LIMIT_REACHED");
}
```

### Scenario 3: Position Size Test

```java
@Test
void testPositionSize_ExceedsLimit() {
    // Setup: Position would exceed 20% capital
    PositionSizeConfig config = PositionSizeConfig.builder()
        .maxCapitalPercent(20.0)
        .build();
    BigDecimal entryValue = new BigDecimal("250000"); // 25% of 1M capital

    // Execute: Validate position size
    ValidationResult result = positionSizeValidator.validate(entryValue, config);

    // Verify: Should fail validation
    assertThat(result.isValid()).isFalse();
    assertThat(result.getReason()).isEqualTo("POSITION_SIZE_EXCEEDS_LIMIT");
}
```

## Risk Control Report Format

```
# Risk Control Validation Report - [Timestamp]

## Executive Summary
- Overall Status: [PASS/FAIL/WARNING]
- Critical Issues: [X]
- Warnings: [X]

## Kill Switch
- Status: [ACTIVE/INACTIVE]
- Configuration: [Valid/Invalid]
- Emergency Contact: [Configured/Not Configured]

## Daily Loss Circuit Breaker
- Threshold: [X]%
- Current Daily Loss: [X]%
- Status: [PASS/FAIL]
- Recommendation: [Adjust threshold if needed]

## Position Limits
- Max Concurrent Positions: [X]/[Y]
- Current Positions: [Z]
- Status: [PASS/FAIL]

## Position Size Limits
- Max Capital Per Position: [X]%
- ATR Multiplier: [X]x
- Status: [PASS/FAIL]

## Risk Control Test Results
| Test | Result | Duration |
|------|--------|----------|
| Circuit Breaker Trigger | PASS | 150ms |
| Position Limit | PASS | 50ms |
| Position Size | PASS | 30ms |

## Recommendations
1. [Actionable steps]
2. [Parameter adjustments]
3. [Additional controls to consider]

## Configuration Snapshot
[Full configuration for audit trail]
```

## Automated Remediation

When issues are found, the skill can:
1. **Auto-adjust thresholds** to recommended values
2. **Alert on critical misconfigurations**
3. **Generate remediation tickets** for manual review
4. **Suggest optimal parameters** based on historical data

## Example Usage

```bash
# Full risk control validation
/skill: swing-trade-risk-control-validator

# Validate specific component
/skill: swing-trade-risk-control-validator --component daily-loss

# Test with simulated market conditions
/skill: swing-trade-risk-control-validator --simulate -10-percent-loss

# Generate full report
/skill: swing-trade-risk-control-validator --report --detailed

# Validate and auto-fix recommendations
/skill: swing-trade-risk-control-validator --auto-fix
```

## Dependencies

- PostgreSQL connection (for position data)
- Risk controls service access
- Trading engine (for simulation tests)

## Performance Considerations

- Validation should complete within 5 seconds
- Simulation tests should complete within 30 seconds
- Use cached position data where possible
- Avoid blocking trading operations during validation
