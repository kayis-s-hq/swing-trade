---
name: swing-trade-data-quality-audit
description: Run automated data quality checks on the swing-trade TimescaleDB database to detect gaps, anomalies, and duplicates. Use this skill whenever data ingestion issues occur, signal generation fails unexpectedly, or you need to validate data integrity before trading operations. This skill is critical for maintaining reliable market data and should be triggered proactively when signal counts drop, data pipeline errors appear, or before executing automated trades.
---

# SwingTrade Data Quality Audit Skill

## Overview

This skill automates data quality validation for the swing-trade system's TimescaleDB database. It checks for common data issues that can cause signal generation failures, incorrect trading signals, or unreliable backtesting results.

## When to Use This Skill

Trigger this skill when:
- Signal generation count drops unexpectedly
- Data ingestion errors appear in logs
- Backtest results seem inconsistent
- Before implementing auto-trade features
- After manual database interventions
- Weekly data health checks (scheduled)
- When investigating trading signal anomalies

## Audit Components

### 1. Data Gap Detection

Check for missing candle data in the OHLCV time series:

```sql
-- Find gaps in daily candles
SELECT symbol,
       date_range(start_date, end_date) AS expected_dates,
       array_agg(date) AS actual_dates,
       (date_range.start_date - actual_dates) AS missing_dates
FROM ohlcv_candles
GROUP BY symbol
HAVING COUNT(actual_dates) < COUNT(expected_dates);
```

**Gap Criteria:**
- Missing trading days (weekdays only, exclude weekends/holidays)
- Gaps > 3 consecutive days = CRITICAL
- Gaps 1-2 days = WARNING

### 2. Duplicate Detection

Identify duplicate candle entries:

```sql
SELECT symbol, date, COUNT(*) as duplicate_count
FROM ohlcv_candles
GROUP BY symbol, date
HAVING COUNT(*) > 1;
```

### 3. Anomaly Detection

Find price anomalies using statistical methods:

```sql
-- Price jumps > 20% day-over-day
SELECT symbol, date, close, LAG(close) OVER (PARTITION BY symbol ORDER BY date) as prev_close,
       ABS((close - LAG(close) OVER (PARTITION BY symbol ORDER BY date)) / LAG(close) OVER (PARTITION BY symbol ORDER BY date)) * 100 as price_change_pct
FROM ohlcv_candles
WHERE ABS((close - LAG(close) OVER (PARTITION BY symbol ORDER BY date)) / LAG(close) OVER (PARTITION BY symbol ORDER BY date)) * 100 > 20;
```

**Anomaly Criteria:**
- Price change > 20% = FLAG for review
- Volume spike > 3x average = FLAG for review
- ATR > 5% of price = FLAG for review

### 4. Signal Data Validation

Validate signals against candle data:

```sql
-- Check signals have corresponding candle data
SELECT s.symbol, s.signal_date, s.signal_type, s.confidence
FROM signals s
LEFT JOIN ohlcv_candles c ON s.symbol = c.symbol AND s.signal_date = c.date
WHERE c.date IS NULL;
```

### 5. Position-Trade Consistency

Verify position and trade data consistency:

```sql
-- Check for positions without trades
SELECT p.*
FROM positions p
LEFT JOIN trades t ON p.position_id = t.position_id
WHERE t.position_id IS NULL;
```

## Audit Report Format

The skill generates a comprehensive report with the following structure:

```
# Data Quality Audit Report - [Timestamp]

## Executive Summary
- Total stocks analyzed: [count]
- Data coverage: [percentage]
- Critical issues: [count]
- Warnings: [count]

## Critical Issues
[List of critical data gaps or anomalies]

## Warnings
[List of minor issues]

## Recommendations
[Actionable steps to fix issues]

## Detailed Findings
[Per-stock breakdown]
```

## Automated Remediation Options

When issues are found, the skill can:

1. **Auto-retry data ingestion** for missing symbols
2. **Flag stocks for manual review** in the watchlist
3. **Generate remediation tickets** for the backlog
4. **Skip affected stocks** in signal generation (with warning)

## Integration with Other Systems

- **Telegram/Signal notifications**: Alert on critical issues
- **GSD backlog**: Create tickets for manual remediation
- **Monitoring dashboards**: Export metrics for Grafana/Prometheus

## Example Usage

```
# Run full audit
/skill: swing-trade-data-quality-audit

# Run audit for specific symbol
/skill: swing-trade-data-quality-audit --symbol RELIANCE

# Run audit and generate remediation plan
/skill: swing-trade-data-quality-audit --generate-plan
```

## Dependencies

- PostgreSQL/TimescaleDB connection
- Flyway migrations (for schema validation)
- DataIngestionService (for remediation)

## Performance Considerations

- Audit runs should complete within 5 minutes
- Use TimescaleDB continuous aggregates for faster queries
- Cache results for 1 hour to avoid redundant audits
