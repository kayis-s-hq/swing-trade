---
name: swing-trade-performance-report-generator
description: Generate comprehensive daily and weekly performance reports for the swing-trade system including P&L analysis, trade attribution, and strategy metrics. Use this skill for daily trading reviews, weekly strategy assessments, investor updates, or before making portfolio adjustments. This skill is essential for tracking trading performance and should be triggered proactively at the end of each trading day and every Sunday for weekly summaries.
---

# SwingTrade Performance Report Generator Skill

## Overview

This skill automates the generation of comprehensive trading performance reports for the swing-trade system. It aggregates data from positions, trades, and signals to provide actionable insights on trading performance, strategy effectiveness, and risk metrics.

## When to Use This Skill

Trigger this skill when:
- End of trading day (daily P&L summary)
- End of week (Sunday weekly review)
- Before portfolio rebalancing decisions
- After significant market events
- Monthly performance review
- When investigating performance anomalies
- For investor/stakeholder updates

## Report Types

### Daily Performance Report

Generated each trading day after market close:

```
# Daily Performance Report - [YYYY-MM-DD]

## P&L Summary
- Starting Capital: ₹[X]
- Ending Capital: ₹[X]
- Daily P&L: ₹[X] ([X]%)
- Unrealized P&L: ₹[X]
- Realized P&L: ₹[X]

## Position Summary
- Open Positions: [X]
- Positions Closed: [X]
- Stop Loss Hits: [X]
- Target Hits: [X]

## Top Performers
| Symbol | P&L | Return |
|--------|-----|--------|
| RELIANCE | +₹15,000 | +5.2% |

## Underperformers
| Symbol | P&L | Return |
|--------|-----|--------|
| TATASTEEL | -₹8,000 | -2.8% |

## Signal Accuracy
- BUY Signals Generated: [X]
- Trades Executed: [X]
- Win Rate (today): [X]%
```

### Weekly Performance Report

Generated every Sunday:

```
# Weekly Performance Report - Week [X] [YYYY]

## Weekly Summary
- Starting Capital: ₹[X]
- Ending Capital: ₹[X]
- Weekly P&L: ₹[X] ([X]%)
- Cumulative YTD P&L: ₹[X] ([X]%)

## Trade Statistics
- Total Trades: [X]
- Winning Trades: [X] ([X]%)
- Losing Trades: [X] ([X]%)
- Average Win: ₹[X]
- Average Loss: ₹[X]
- Profit Factor: [X]

## Risk Metrics
- Max Drawdown (week): [X]%
- Average Position Size: [X]%
- Sector Exposure: [breakdown]

## Strategy Performance
| Strategy | Trades | Win Rate | P&L |
|----------|--------|----------|-----|
| EMA Crossover | 10 | 60% | +₹50,000 |
| RSI Oversold | 5 | 80% | +₹25,000 |

## Sector Analysis
| Sector | Positions | P&L |
|--------|-----------|-----|
| Banking | 3 | +₹30,000 |
| IT | 2 | -₹10,000 |

## Recommendations
1. [Actionable insights]
2. [Position adjustments]
3. [Risk management changes]
```

## Data Sources

### Positions Data

```sql
SELECT
    p.symbol,
    p.direction,
    p.entry_price,
    p.current_price,
    p.quantity,
    (p.current_price - p.entry_price) * p.quantity * p.direction AS unrealized_pl,
    CASE
        WHEN p.status = 'CLOSED' THEN (p.exit_price - p.entry_price) * p.quantity * p.direction
        ELSE 0
    END AS realized_pl
FROM positions p;
```

### Trades Data

```sql
SELECT
    t.symbol,
    t.entry_date,
    t.exit_date,
    t.entry_price,
    t.exit_price,
    t.quantity,
    t.stop_loss,
    t.target,
    t.profit_loss,
    t.exit_reason
FROM trades t
WHERE t.entry_date BETWEEN [start] AND [end];
```

### Signal Data

```sql
SELECT
    s.symbol,
    s.signal_date,
    s.signal_type,
    s.confidence,
    CASE
        WHEN t.profit_loss > 0 THEN 'WIN'
        WHEN t.profit_loss < 0 THEN 'LOSS'
        ELSE 'NEUTRAL'
    END AS outcome
FROM signals s
LEFT JOIN trades t ON s.symbol = t.symbol AND s.signal_date = t.entry_date;
```

## Performance Metrics Calculated

### Return Metrics

```
Total Return = (Ending Capital - Starting Capital) / Starting Capital
Daily Return = (Today's P&L) / Starting Capital
Cumulative Return = Sum of all daily returns
```

### Risk Metrics

```
Max Drawdown = (Peak - Trough) / Peak
Sharpe Ratio = (Return - Risk Free Rate) / Standard Deviation
Sortino Ratio = (Return - Risk Free Rate) / Downside Deviation
```

### Trade Metrics

```
Win Rate = Winning Trades / Total Trades
Profit Factor = Gross Wins / Gross Losses
Average Win/Loss = Total Wins / Wins / (Total Losses / Losses)
```

## Report Generation Workflow

```
1. Connect to database
   ↓
2. Fetch positions data (open and closed)
   ↓
3. Fetch trades data for period
   ↓
4. Fetch signals data for period
   ↓
5. Calculate P&L metrics
   ↓
6. Calculate risk metrics
   ↓
7. Aggregate by sector/strategy
   ↓
8. Generate report markdown
   ↓
9. Send notification (Telegram/Signal)
   ↓
10. Save report to vault/docs
```

## Report Storage

Reports are saved to:
- `docs/reports/daily/[YYYY-MM-DD].md`
- `docs/reports/weekly/[YYYY]-W[XX].md`
- `docs/reports/monthly/[YYYY-MM].md`

## Notification Integration

Reports can be sent via:
- Telegram bot (configurable channel)
- Signal webhook (configurable endpoint)
- Email (configurable recipients)
- Slack webhook (configurable channel)

## Example Usage

```bash
# Generate daily report for today
/skill: swing-trade-performance-report-generator --type daily

# Generate weekly report for current week
/skill: swing-trade-performance-report-generator --type weekly

# Generate custom period report
/skill: swing-trade-performance-report-generator --start 2024-01-01 --end 2024-01-31 --type monthly

# Send report via Telegram
/skill: swing-trade-performance-report-generator --notify telegram

# Generate report with detailed trade log
/skill: swing-trade-performance-report-generator --detailed-log
```

## Dependencies

- PostgreSQL/TimescaleDB connection
- Telegram/Signal API credentials
- Report template engine

## Performance Considerations

- Daily reports should generate in < 30 seconds
- Weekly reports should generate in < 1 minute
- Monthly reports should generate in < 2 minutes
- Use database indexes on trade dates and symbols
