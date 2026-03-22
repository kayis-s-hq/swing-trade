---
name: swing-trade-auto-trade-executor
description: Auto-execute high-confidence trading signals with risk validation for the swing-trade system. Use this skill when implementing auto-trade features, testing automated execution strategies, validating risk parameters before live auto-trading, or when executing trades based on verified high-confidence signals. This skill is critical for automated trading and should be triggered proactively when implementing the auto-trade pipeline or when manually executing high-confidence signals.
---

# SwingTrade Auto-Trade Executor Skill

## Overview

This skill automates the execution of trading signals based on predefined criteria, including confidence thresholds, risk validation, and position capacity checks. It bridges the gap between signal generation and trade execution.

## When to Use This Skill

Trigger this skill when:
- Implementing auto-trade features
- Executing high-confidence signals automatically
- Testing automated execution strategies
- Validating risk parameters before live auto-trading
- Manually triggering auto-execute for specific signals

## Auto-Execute Criteria

```java
public class AutoExecuteCriteria {
    // Minimum confidence threshold
    private static final double MIN_CONFIDENCE = 0.7;

    // Maximum position size
    private static final double MAX_POSITION_PERCENT = 0.20;

    // Required sentiment (not NEGATIVE)
    private static final Set<SentimentType> ACCEPTABLE_SENTIMENTS =
        Set.of(POSITIVE, NEUTRAL);

    // Available capital check
    private boolean hasEnoughCapital(BigDecimal entryValue) {
        return entryValue.compareTo(totalCapital.multiply(BigDecimal.valueOf(MAX_POSITION_PERCENT))) <= 0;
    }

    public boolean shouldAutoExecute(Signal signal, PositionCapacity capacity) {
        // Check confidence
        if (signal.getConfidence() < MIN_CONFIDENCE) {
            return false;
        }

        // Check sentiment
        SentimentResult sentiment = sentimentService.getSentiment(signal.getSymbol(), signal.getSignalDate());
        if (!ACCEPTABLE_SENTIMENTS.contains(sentiment.getSentiment())) {
            return false;
        }

        // Check position capacity
        BigDecimal entryValue = signal.getEntryPrice().multiply(BigDecimal.valueOf(DEFAULT_QUANTITY));
        if (!hasEnoughCapital(entryValue)) {
            return false;
        }

        // Check risk controls
        if (!riskControlsService.canExecuteTrade()) {
            return false;
        }

        return true;
    }
}
```

## Execution Workflow

```
1. Fetch pending signals
   ↓
2. Filter by auto-execute criteria
   ↓
3. Validate risk controls
   ↓
4. Check position capacity
   ↓
5. Calculate position size
   ↓
6. Execute trade
   ↓
7. Log execution
   ↓
8. Send notification
```

## Risk Validation

```java
public class AutoTradeRiskValidator {
    public ValidationResult validateAutoTrade(TradeRequest request) {
        List<RiskCheck> checks = new ArrayList<>();

        // Kill switch check
        checks.add(() -> !killSwitchService.isActive());

        // Daily loss check
        checks.add(() -> dailyLossService.isWithinLimit());

        // Position count check
        checks.add(() -> positionCountService.getCount() < MAX_POSITIONS);

        // Position size check
        checks.add(() -> positionSizeService.isValid(request.getEntryValue()));

        // Sector concentration check (optional)
        checks.add(() -> sectorConcentrationService.isValid(request.getSymbol()));

        // Execute all checks
        return ValidationResult.builder()
            .passes(checks.stream().allMatch(RiskCheck::check))
            .failedChecks(checks.stream().filter(c -> !c.check()).collect(Collectors.toList()))
            .build();
    }
}
```

## Position Sizing

```java
public class AutoTradePositionSizer {
    public PositionSize calculateSize(Signal signal, MarketData marketData) {
        BigDecimal entryPrice = signal.getEntryPrice();
        BigDecimal atr = technicalIndicators.calculateATR(signal.getSymbol(), 14);
        BigDecimal stopLoss = entryPrice.subtract(atr.multiply(BigDecimal.valueOf(2)));
        BigDecimal target = entryPrice.add(entryPrice.subtract(stopLoss).multiply(BigDecimal.valueOf(2.5)));

        // Calculate quantity based on risk (1% of capital)
        BigDecimal riskPerTrade = totalCapital.multiply(BigDecimal.valueOf(0.01));
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss);
        BigDecimal quantity = riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);

        return PositionSize.builder()
            .symbol(signal.getSymbol())
            .entryPrice(entryPrice)
            .stopLoss(stopLoss)
            .target(target)
            .quantity(quantity)
            .riskRewardRatio(BigDecimal.valueOf(2.5))
            .build();
    }
}
```

## Execution Report Format

```
# Auto-Trade Execution Report - [Timestamp]

## Execution Summary
- Signals Processed: [X]
- Auto-Executed: [X]
- Skipped (Low Confidence): [X]
- Skipped (Sentiment): [X]
- Skipped (Risk): [X]

## Executed Trades
| Symbol | Signal Type | Confidence | Entry Price | Quantity | Stop Loss | Target |
|--------|-------------|------------|-------------|----------|-----------|--------|
| RELIANCE | BUY | 0.85 | 2500 | 100 | 2400 | 2750 |
| INFY | BUY | 0.78 | 1400 | 150 | 1350 | 1525 |

## Risk Validation
- Kill Switch: PASS
- Daily Loss Limit: PASS (Current: -2.3%, Limit: -5%)
- Position Count: PASS (4/10)
- Position Size: PASS (Max: 18%)

## Notifications Sent
- Telegram: [X]
- Signal: [X]
- Email: [X]

## Performance Tracking
- Total Capital Deployed: ₹[X]
- Potential Risk: ₹[X]
- Expected Return: ₹[X]
```

## Example Usage

```bash
# Execute all eligible signals
/skill: swing-trade-auto-trade-executor

# Execute with custom confidence threshold
/skill: swing-trade-auto-trade-executor --min-confidence 0.8

# Dry run (validate without executing)
/skill: swing-trade-auto-trade-executor --dry-run

# Execute specific symbol
/skill: swing-trade-auto-trade-executor --symbol RELIANCE

# Generate execution report
/skill: swing-trade-auto-trade-executor --report
```

## Safety Features

### Circuit Breakers

```java
public class AutoTradeCircuitBreaker {
    // Immediate halt conditions
    public boolean shouldHalt() {
        return killSwitchService.isActive()
            || dailyLossService.exceededLimit()
            || positionCountService.atLimit()
            || marketVolatilityService.exceedsThreshold();
    }

    // Graceful degradation
    public AutoTradeMode getMode() {
        if (dailyLossService.isNearLimit()) {
            return AutoTradeMode.REDUCED; // Only high confidence
        }
        if (positionCountService.isNearLimit()) {
            return AutoTradeMode.CONSTRAINED; // Smaller positions
        }
        return AutoTradeMode.FULL;
    }
}
```

### Execution Limits

| Limit Type | Default | Configurable |
|------------|---------|--------------|
| Max trades/day | 5 | Yes |
| Max capital/day | 50% | Yes |
| Max position size | 20% | Yes |
| Min confidence | 0.7 | Yes |
| Max concurrent | 10 | Yes |

## Dependencies

- Trading engine access
- Risk controls service
- Position manager
- Notification services

## Performance Considerations

- Execution should complete within 30 seconds
- Use async execution for non-critical operations
- Implement retry logic for transient failures
- Log all executions for audit trail
