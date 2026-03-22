# /strategy — Document Strategy Iteration

## Purpose

Document strategy iteration or backtest result — track parameter changes and performance.

## What It Does

1. Prompts for strategy details: iteration number, parameter changes
2. Captures backtest results: win rate, Sharpe ratio, max drawdown
3. Analyzes LLM filter effectiveness: did it improve outcomes?
4. Creates structured note in `strategy/` folder

## Usage

Run `/strategy` when:
- You've made changes to strategy parameters
- A backtest completes and you want to record results
- You want to analyze whether LLM filtering improved signal quality

## Required Fields

- **Iteration**: Version number (e.g., #5, #6)
- **Parameters Changed**: What did you modify?
- **Backtest Period**: Date range tested
- **Results**: Win rate, Sharpe ratio, max drawdown, total return
- **LLM Impact**: Did the LLM filter help or hurt?

## Example Output

```
## Strategy Iteration #6

**Date**: 2026-03-22
**Previous**: Iteration #5

### Parameters Changed
- Added LLM sentiment filter (threshold: 0.65)
- Increased RSI oversold threshold from 30 to 35
- Extended holding period from 2-3 weeks to 2-4 weeks

### Backtest Results (90 days)
- **Win Rate**: 58% (vs 52% in #5)
- **Sharpe Ratio**: 1.4 (vs 1.1 in #5)
- **Max Drawdown**: -8.2% (vs -12.5% in #5)
- **Total Return**: +14.3% (vs +9.8% in #5)

### LLM Filter Impact
- **Signals Filtered**: 23% of technical signals rejected
- **Filtered Out Losses**: 67% of rejected signals would have lost money
- **Missed Gains**: 33% of rejected signals would have been profitable
- **Net Effect**: Positive — filter improved risk-adjusted returns

### Next Steps
- Test on 6-month period
- Adjust LLM threshold to 0.60 if drawdown exceeds -10%
```
