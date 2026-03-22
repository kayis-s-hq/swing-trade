---
name: swing-trade-position-rebalancer
description: Automated position rebalancing with partial exits and trailing stops for the swing-trade system. Use this skill when managing existing positions, optimizing exit strategies, implementing trailing stop logic, or when executing partial profit-taking at target levels. This skill is essential for advanced position management and should be triggered proactively when positions approach targets, when implementing time-based exits, or when market conditions change.
---

# SwingTrade Position Rebalancer Skill

## Overview

This skill automates position rebalancing decisions including partial exits, trailing stops, and time-based exits. It optimizes position management beyond simple stop loss/target exits.

## When to Use This Skill

Trigger this skill when:
- Positions approach target levels
- Implementing trailing stop strategies
- Executing partial profit-taking
- Time-based exit decisions
- Market regime changes
- Portfolio rebalancing

## Partial Exit Strategy

```java
public class PartialExitStrategy {
    // Exit 50% at target, keep 50% with reduced risk
    public PositionAction executePartialExit(Position position, MarketData marketData) {
        if (marketData.getCurrentPrice() >= position.getTarget()) {
            BigDecimal exitQuantity = position.getQuantity().divide(BigDecimal.valueOf(2));
            BigDecimal exitPrice = marketData.getCurrentPrice();
            BigDecimal realizedProfit = exitPrice.subtract(position.getEntryPrice())
                .multiply(exitQuantity)
                .multiply(position.getDirection() == LONG ? BigDecimal.ONE : BigDecimal.valueOf(-1));

            // Update position
            Position updatedPosition = position.withQuantity(exitQuantity)
                .withStopLoss(position.getEntryPrice()) // Move to breakeven
                .withTarget(position.getTarget().multiply(BigDecimal.valueOf(1.5)));

            return PositionAction.builder()
                .action(PARTIAL_EXIT)
                .exitQuantity(exitQuantity)
                .exitPrice(exitPrice)
                .realizedProfit(realizedProfit)
                .remainingPosition(updatedPosition)
                .build();
        }
        return null;
    }
}
```

## Trailing Stop Logic

```java
public class TrailingStopManager {
    // Trailing stop: 5% below highest price
    public StopLoss updateTrailingStop(Position position, MarketData marketData) {
        BigDecimal highestPrice = getHighestPrice(position.getSymbol(), position.getEntryDate(), marketData.getCurrentDate());
        BigDecimal trailingStop = highestPrice.multiply(BigDecimal.valueOf(0.95));

        // Only move stop up, never down
        if (trailingStop.compareTo(position.getStopLoss()) > 0) {
            return trailingStop;
        }
        return position.getStopLoss();
    }

    // ATR-based trailing stop
    public StopLoss updateATRTrailingStop(Position position, MarketData marketData) {
        BigDecimal atr = technicalIndicators.calculateATR(position.getSymbol(), 14);
        BigDecimal highestPrice = getHighestPrice(position.getSymbol(), position.getEntryDate(), marketData.getCurrentDate());
        BigDecimal trailingStop = highestPrice.subtract(atr.multiply(BigDecimal.valueOf(1.5)));

        if (trailingStop.compareTo(position.getStopLoss()) > 0) {
            return trailingStop;
        }
        return position.getStopLoss();
    }
}
```

## Time-Based Exit

```java
public class TimeBasedExit {
    // Exit if position hasn't moved in 10 days
    public boolean shouldExitByTime(Position position, LocalDate today) {
        long holdingDays = ChronoUnit.DAYS.between(position.getEntryDate(), today);

        // Check if thesis is still valid
        if (holdingDays >= 10 && !isThesisVerified(position)) {
            return true; // Time stop
        }

        // Check if target reached quickly (early exit)
        if (holdingDays <= 3 && isTargetReached(position)) {
            return true; // Quick profit
        }

        return false;
    }

    private boolean isThesisVerified(Position position) {
        // Check if price moved in expected direction
        BigDecimal currentPrice = getCurrentPrice(position.getSymbol());
        BigDecimal move = currentPrice.subtract(position.getEntryPrice())
            .abs()
            .divide(position.getEntryPrice(), 4, RoundingMode.HALF_UP);

        return move.compareTo(BigDecimal.valueOf(0.02)) >= 0; // At least 2% move
    }
}
```

## Position Rebalancing Workflow

```
1. Fetch all open positions
   ↓
2. Check for target hits
   ↓
3. Check for stop loss hits
   ↓
4. Check for partial exit opportunities
   ↓
5. Update trailing stops
   ↓
6. Check time-based exits
   ↓
7. Execute rebalancing decisions
   ↓
8. Log all actions
   ↓
9. Send notifications
```

## Rebalancing Report Format

```
# Position Rebalancing Report - [Timestamp]

## Position Summary
- Total Open Positions: [X]
- Positions Closed: [X]
- Positions Partially Closed: [X]
- Positions Updated: [X]

## Exits
### Target Exits
| Symbol | Entry | Exit | P&L | Return |
|--------|-------|------|-----|--------|
| RELIANCE | 2500 | 2750 | +₹25,000 | +10% |

### Stop Loss Exits
| Symbol | Entry | Exit | P&L | Return |
|--------|-------|------|-----|--------|
| TATASTEEL | 1200 | 1164 | -₹3,600 | -3% |

### Partial Exits
| Symbol | Entry | Partial Exit | Realized P&L | Remaining |
|--------|-------|--------------|--------------|-----------|
| INFY | 1400 | 50% @ 1500 | +₹5,000 | 50% @ 1550 |

### Time-Based Exits
| Symbol | Entry Days | Reason | P&L |
|--------|------------|--------|-----|
| HDFC | 12 | Thesis unverified | +₹1,000 |

## Stop Loss Updates
| Symbol | Old Stop | New Stop | Type |
|--------|----------|----------|------|
| RELIANCE | 2400 | 2520 | Trailing |
| INFY | 1350 | 1420 | ATR Trailing |

## Recommendations
1. [Position adjustments]
2. [Risk management changes]
3. [New entry opportunities]
```

## Rebalancing Rules Engine

```java
public class RebalancingRulesEngine {
    public List<PositionAction> evaluateRules(List<Position> positions, MarketData marketData) {
        List<PositionAction> actions = new ArrayList<>();

        for (Position position : positions) {
            // Rule 1: Full exit at target
            if (marketData.getCurrentPrice() >= position.getTarget()) {
                actions.add(fullExit(position, marketData));
            }
            // Rule 2: Partial exit at 1.5x target
            else if (marketData.getCurrentPrice() >= position.getTarget().multiply(BigDecimal.valueOf(1.5))) {
                actions.add(partialExit(position, marketData));
            }
            // Rule 3: Trailing stop update
            else if (shouldUpdateTrailingStop(position)) {
                actions.add(updateTrailingStop(position, marketData));
            }
            // Rule 4: Time-based exit
            else if (shouldExitByTime(position, LocalDate.now())) {
                actions.add(timeExit(position, marketData));
            }
        }

        return actions;
    }
}
```

## Example Usage

```bash
# Full rebalancing check
/skill: swing-trade-position-rebalancer

# Check specific position
/skill: swing-trade-position-rebalancer --symbol RELIANCE

# Execute partial exits only
/skill: swing-trade-position-rebalancer --action partial-exit

# Update trailing stops only
/skill: swing-trade-position-rebalancer --action trailing-stop

# Generate rebalancing report
/skill: swing-trade-position-rebalancer --report

# Dry run (show actions without executing)
/skill: swing-trade-position-rebalancer --dry-run
```

## Dependencies

- Position manager access
- Trading engine
- Market data service
- Technical indicators service

## Performance Considerations

- Rebalancing should complete within 30 seconds
- Use batch operations for multiple positions
- Cache highest price calculations
- Avoid blocking trading operations
