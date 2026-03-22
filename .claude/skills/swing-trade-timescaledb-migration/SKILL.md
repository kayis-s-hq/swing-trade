---
name: swing-trade-timescaledb-migration
description: Create TimescaleDB hypertable migrations with proper chunk intervals and continuous aggregates. Use this skill whenever adding new time-series tables, modifying existing time-series schemas, or optimizing query performance for the swing-trade system. This skill is critical for maintaining optimal database performance and should be triggered whenever new OHLCV-related tables are created or when query performance degrades.
---

# SwingTrade TimescaleDB Migration Skill

## Overview

This skill automates the creation of TimescaleDB-optimized database migrations for the swing-trade system. It ensures proper hypertable configuration, chunk intervals, and continuous aggregates for optimal time-series query performance.

## When to Use This Skill

Trigger this skill when:
- Adding new time-series tables (OHLCV, positions, trades)
- Modifying existing hypertable configurations
- Optimizing query performance for time-range queries
- Creating new continuous aggregates for summaries
- Changing data retention policies
- Before deploying schema changes to production

## TimescaleDB Best Practices

### Hypertable Configuration

```sql
-- Optimal chunk interval for daily candles
SELECT create_hypertable(
    'ohlcv_candles',
    'date',
    chunk_time_interval => INTERVAL '30 days',
    if_not_exists => TRUE
);

-- Optimal chunk interval for intraday data
SELECT create_hypertable(
    'ohlcv_candles_intraday',
    'timestamp',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);
```

### Chunk Interval Guidelines

| Data Type | Frequency | Recommended Chunk | Retention |
|-----------|-----------|-------------------|-----------|
| Daily OHLCV | 1 day | 30 days | 7+ years |
| Intraday | 1 minute | 1 day | 30 days |
| Tick data | 1 second | 1 hour | 7 days |
| Signals | 1 day | 90 days | 3+ years |
| Positions | Event-based | N/A | 5+ years |

### Indexing Strategy

```sql
-- Always add composite indexes for common query patterns
CREATE INDEX CONCURRENTLY idx_ohlcv_symbol_date
ON ohlcv_candles (symbol, date DESC);

-- Partial indexes for recent data optimization
CREATE INDEX CONCURRENTLY idx_ohlcv_recent
ON ohlcv_candles (symbol, date)
WHERE date >= CURRENT_DATE - INTERVAL '90 days';
```

## Migration File Structure

### Naming Convention

```
V[Sequence]__[Description].sql
```

Examples:
- `V5__create_signal_aggregates.sql`
- `V6__optimize_ohlcv_hypertable.sql`
- `V7__create_trade_history_tables.sql`

### Migration Template

```sql
-- ============================================
-- Migration: V[Sequence]__[Description]
-- Date: [YYYY-MM-DD]
-- Author: [Your Name]
-- ============================================

BEGIN;

-- Step 1: Create hypertable if not exists
SELECT create_hypertable(
    '[table_name]',
    '[time_column]',
    chunk_time_interval => INTERVAL '[interval]',
    if_not_exists => TRUE
);

-- Step 2: Add additional time columns (optional)
SELECT add_column(
    '[table_name]',
    '[column_name] [data_type]'
);

-- Step 3: Create indexes
CREATE INDEX CONCURRENTLY idx_[table]_[columns]
ON [table_name] ([columns]);

-- Step 4: Create continuous aggregate (optional)
CREATE MATERIALIZED VIEW [view_name]
WITH (timescaledb.contiguous = true)
AS
SELECT [time_column], [aggregate_functions]
FROM [table_name]
GROUP BY [time_column];

-- Step 5: Add retention policy (optional)
SELECT add_retention_policy(
    '[table_name]',
    INTERVAL '[retention_period]'
);

COMMIT;
```

## Continuous Aggregate Patterns

### Daily Summary Aggregate

```sql
CREATE MATERIALIZED VIEW ohlcv_candles_daily_agg
WITH (timescaledb.contiguous = true)
AS
SELECT
    symbol,
    time_bucket('1 day', date) AS bucket,
    first(close, date) AS open,
    max(high) AS high,
    min(low) AS low,
    last(close, date) AS close,
    sum(volume) AS volume,
    avg(volume) AS avg_volume_20
FROM ohlcv_candles
GROUP BY symbol, bucket;

-- Refresh policy
SELECT add_job('REFRESH MATERIALIZED VIEW CONCURRENTLY ohlcv_candles_daily_agg',
               '0 0 * * *');
```

### Weekly Summary Aggregate

```sql
CREATE MATERIALIZED VIEW ohlcv_candles_weekly_agg
WITH (timescaledb.contiguous = true)
AS
SELECT
    symbol,
    time_bucket('1 week', date) AS bucket,
    first(close, date) AS open,
    max(high) AS high,
    min(low) AS low,
    last(close, date) AS close,
    sum(volume) AS volume
FROM ohlcv_candles
GROUP BY symbol, bucket;
```

### Monthly Summary Aggregate

```sql
CREATE MATERIALIZED VIEW ohlcv_candles_monthly_agg
WITH (timescaledb.contiguous = true)
AS
SELECT
    symbol,
    time_bucket('1 month', date) AS bucket,
    first(close, date) AS open,
    max(high) AS high,
    min(low) AS low,
    last(close, date) AS close,
    sum(volume) AS volume
FROM ohlcv_candles
GROUP BY symbol, bucket;
```

## Migration Validation

### Pre-Deployment Checks

```sql
-- Verify hypertable configuration
SELECT * FROM hypertable_dimension_info('ohlcv_candles');

-- Check chunk distribution
SELECT
    table_name,
    chunk_interval_start,
    chunk_interval_end,
    row_count
FROM _timescaledb_internal.chunk_metadata
WHERE table_name = 'ohlcv_candles';

-- Verify indexes
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'ohlcv_candles';
```

### Post-Deployment Verification

```sql
-- Test query performance
EXPLAIN ANALYZE
SELECT * FROM ohlcv_candles
WHERE symbol = 'RELIANCE'
  AND date BETWEEN '2024-01-01' AND '2024-12-31';

-- Verify continuous aggregate freshness
SELECT matview_refresh_timestamp
FROM matview_refresh_timestamps
WHERE matview_name = 'ohlcv_candles_daily_agg';
```

## Migration Report Format

```
# TimescaleDB Migration Report - V[Sequence]

## Migration Details
- File: V[Sequence]__[Description].sql
- Date: [YYYY-MM-DD]
- Hypertables Modified: [list]

## Changes Made
1. [Change 1]
2. [Change 2]

## Performance Impact
- Query improvement: [X]%
- Storage optimization: [X]%
- Chunk count: [X] chunks

## Validation Results
- Pre-deployment checks: PASS
- Post-deployment checks: PASS
- Query benchmarks: PASS

## Rollback Instructions
[Steps to rollback if needed]
```

## Example Usage

```bash
# Create new hypertable migration
/skill: swing-trade-timescaledb-migration --table trade_history --time-col trade_date

# Add continuous aggregate
/skill: swing-trade-timescaledb-migration --aggregate daily --source ohlcv_candles

# Optimize existing hypertable
/skill: swing-trade-timescaledb-migration --optimize ohlcv_candles --chunk-interval 60-days

# Generate migration report
/skill: swing-trade-timescaledb-migration --report V4__create_trades_table
```

## Dependencies

- TimescaleDB extension (>= 2.0)
- PostgreSQL (>= 14)
- Flyway for migration management

## Performance Considerations

- Use `CONCURRENTLY` for index creation to avoid lock contention
- Schedule migrations during low-traffic periods
- Test chunk interval changes on staging first
- Monitor chunk growth to prevent oversized chunks
