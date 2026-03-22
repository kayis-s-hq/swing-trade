---
name: swing-trade-signal-backtest-runner
description: Execute automated backtests on trading signals and strategy changes to validate performance before live deployment. Use this skill whenever implementing new technical indicators, modifying signal generation logic, testing new strategy parameters, or before deploying auto-trade features. This skill is essential for strategy validation and should be triggered proactively whenever trading logic changes to ensure performance improvements are real and not overfitted.
---

# SwingTrade Signal Backtest Runner Skill

## Overview

This skill automates the backtesting workflow for the swing-trade system, executing comprehensive performance analysis on trading strategies and signals. It validates strategy changes before deployment and provides detailed metrics for strategy optimization.

## When to Use This Skill

Trigger this skill when:
- Implementing new technical indicators or modifying existing ones
- Changing signal generation thresholds (e.g., RSI levels, EMA periods)
- Testing new strategy parameters (confidence thresholds, factor weights)
- Before deploying auto-trade features
- After significant market regime changes
- Weekly strategy performance review
- When signal accuracy appears to degrade

## Backtest Configuration

### Standard Backtest Parameters

```java
BacktestConfig {
    startDate: LocalDate (default: 1 year ago)
    endDate: LocalDate (default: today)
    initialCapital: BigDecimal (default: 100000)
    commissionRate: double (default: 0.001)
    maxConcurrentPositions: int (default: 5)
    capitalPerPosition: double (default: 0.20)
    slippageBps: int (default: 10)
}
```

### Performance Metrics Calculated

1. **Return Metrics**
   - Total return (%)
   - Annualized return (%)
   - CAGR (Compound Annual Growth Rate)

2. **Risk Metrics**
   - Sharpe ratio (risk-free rate: 6.5%)
   - Sortino ratio
   - Max drawdown (%)
   - Calmar ratio

3. **Trade Statistics**
   - Total trades
   - Win rate (%)
   - Profit factor
   - Average win/loss ratio
   - Largest win/loss

4. **Signal Quality**
   - Signal accuracy by type (BUY/SELL/HOLD)
   - Average holding period
   - Signal generation frequency

## Backtest Execution Flow

```
1. Load historical OHLCV data
   ↓
2. Replay signal generation (simulated)
   ↓
3. Execute trades based on signals
   ↓
4. Apply risk controls (position limits, circuit breakers)
   ↓
5. Calculate P&L with commissions/slippage
   ↓
6. Generate performance metrics
   ↓
7. Create detailed trade log
   ↓
8. Output backtest report
```

## Backtest Report Format

```
# Backtest Report - [Strategy Name]
## Period: [start] to [end]

### Performance Summary
- Total Return: [X]%
- Annualized Return: [X]%
- Sharpe Ratio: [X]
- Max Drawdown: [X]%
- Win Rate: [X]%

### Trade Statistics
- Total Trades: [X]
- Winning Trades: [X]
- Losing Trades: [X]
- Profit Factor: [X]

### Signal Analysis
- BUY Signals: [X] (accuracy: [X]%)
- SELL Signals: [X] (accuracy: [X]%)
- HOLD Signals: [X]

### Top Performing Stocks
| Symbol | Return | Trades | Win Rate |
|--------|--------|--------|----------|
| RELIANCE | +25% | 10 | 70% |

### Worst Performing Stocks
| Symbol | Return | Trades | Win Rate |
|--------|--------|--------|----------|
| TATASTEEL | -15% | 8 | 37% |

### Monthly Returns
| Month | Return |
|-------|--------|
| Jan | +5% |
| Feb | -2% |

### Recommendations
1. [Actionable insights]
2. [Parameter adjustments]
3. [Risk management changes]
```

## Comparative Backtesting

When comparing strategy versions:

```
# Backtest Comparison: v1 vs v2

## Metric Changes
| Metric | v1 | v2 | Change |
|--------|-----|-----|--------|
| Sharpe | 1.2 | 1.4 | +16% |
| Max DD | -15% | -12% | +20% |
| Win Rate | 55% | 58% | +5% |

## Recommendation: [Upgrade/Revert]
```

## Integration with Signal Generation

The backtest runner can:
1. **Validate new signals** before adding to production
2. **Test sentiment filtering** effectiveness
3. **Optimize confidence thresholds**
4. **Evaluate sector rotation** strategies

## Example Usage

```bash
# Standard backtest (last 1 year)
/skill: swing-trade-signal-backtest-runner

# Custom period backtest
/skill: swing-trade-signal-backtest-runner --start 2024-01-01 --end 2024-12-31

# Compare two strategy versions
/skill: swing-trade-signal-backtest-runner --compare v1 v2

# Backtest with custom capital
/skill: swing-trade-signal-backtest-runner --capital 500000

# Generate detailed trade log
/skill: swing-trade-signal-backtest-runner --detailed-log
```

## Dependencies

- TA4J library (for technical analysis)
- Historical OHLCV data (TimescaleDB)
- BacktestEngine implementation
- Performance metrics calculator

## Performance Considerations

- Backtests should complete within 10 minutes for 1 year of data
- Use parallel processing for multi-stock backtests
- Cache intermediate calculations where possible
- Consider sampling for very long backtests (5+ years)
