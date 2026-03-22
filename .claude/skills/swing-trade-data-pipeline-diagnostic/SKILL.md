---
name: swing-trade-data-pipeline-diagnostic
description: Diagnose data pipeline failures with detailed error reporting for the swing-trade system. Use this skill when data ingestion fails, signal generation errors occur, data quality issues arise, or when investigating pipeline performance problems. This skill is essential for maintaining data reliability and should be triggered proactively whenever any data pipeline component shows errors or unexpected behavior.
---

# SwingTrade Data Pipeline Diagnostic Skill

## Overview

This skill provides comprehensive diagnostic capabilities for the swing-trade data pipeline. It identifies root causes of data ingestion failures, signal generation errors, and data quality issues with detailed reporting.

## When to Use This Skill

Trigger this skill when:
- Data ingestion fails or shows errors
- Signal generation count drops unexpectedly
- Data quality issues appear (gaps, duplicates)
- Pipeline performance degrades
- External API integration fails
- Database write errors occur
- Cache inconsistencies detected

## Pipeline Components

### 1. Data Ingestion Pipeline

```
External API (Upstox) → DataFetcher → Validator → Transformer → Database (TimescaleDB)
```

**Diagnostic Checks:**
- API connectivity and rate limits
- Response time and error rates
- Data validation failures
- Duplicate detection
- Database write errors

### 2. Signal Generation Pipeline

```
Database (Candles) → SignalEngine → TechnicalAnalysis → SentimentCheck → Database (Signals)
```

**Diagnostic Checks:**
- Candle data availability
- Technical indicator calculation errors
- Sentiment API response
- Signal suppression reasons
- Database write errors

### 3. Data Quality Pipeline

```
Raw Data → QualityChecks → AnomalyDetection → Remediation → Clean Data
```

**Diagnostic Checks:**
- Data completeness
- Price anomaly detection
- Volume spike detection
- Gap identification
- Duplicate records

## Diagnostic Workflow

### Phase 1: Symptom Collection

```
1. Identify the failing component
   - Data ingestion
   - Signal generation
   - Data quality

2. Gather error information
   - Error messages
   - Error frequency
   - Affected symbols

3. Check recent changes
   - Code deployments
   - Configuration changes
   - External API changes
```

### Phase 2: Component Testing

```
For each component:

1. Connectivity test
   - Can reach external API?
   - Can reach database?
   - Can reach cache?

2. Data test
   - Is input data available?
   - Is output data being written?
   - Are transformations working?

3. Performance test
   - Response times
   - Throughput
   - Error rates
```

### Phase 3: Root Cause Analysis

```
1. Trace the error path
   - Where did it start?
   - How did it propagate?
   - What was the impact?

2. Identify contributing factors
   - External dependencies
   - Resource constraints
   - Configuration issues

3. Determine root cause
   - Single point of failure?
   - Cascading failure?
   - Resource exhaustion?
```

### Phase 4: Remediation Planning

```
1. Immediate fix
   - Restart service?
   - Clear cache?
   - Retry failed operations?

2. Short-term fix
   - Configuration adjustment?
   - Data recovery?
   - Rate limit adjustment?

3. Long-term fix
   - Code change?
   - Architecture improvement?
   - Monitoring enhancement?
```

## Diagnostic Report Format

```
# Data Pipeline Diagnostic Report - [Timestamp]

## Executive Summary
- Overall Status: [HEALTHY/DEGRADED/FAILED]
- Affected Components: [list]
- Impact: [scope of impact]

## Data Ingestion Status
### Upstox API
- Connectivity: [UP/DOWN]
- Response Time: [X]ms
- Error Rate: [X]%
- Rate Limit Status: [OK/EXCEEDED]

### Database Write
- Success Rate: [X]%
- Failed Writes: [X]
- Error Types: [list]

### Data Quality
- Records Ingested: [X]
- Validation Failures: [X]
- Duplicates Detected: [X]
- Gaps Identified: [X]

## Signal Generation Status
### Candle Data
- Symbols with Data: [X]/[X]
- Data Freshness: [X] hours
- Missing Data: [list]

### Technical Analysis
- Successful Calculations: [X]
- Calculation Errors: [X]
- Error Types: [list]

### Sentiment Analysis
- API Status: [UP/DOWN]
- Response Time: [X]ms
- Suppressed Signals: [X]
- Suppression Reasons: [breakdown]

## Error Analysis
| Error Type | Count | First Occurrence | Last Occurrence |
|------------|-------|------------------|-----------------|
| API_TIMEOUT | 15 | 10:30 | 11:45 |
| DB_WRITE_FAIL | 8 | 10:35 | 11:30 |

## Root Cause Analysis
### Primary Cause
[Detailed explanation]

### Contributing Factors
1. [Factor 1]
2. [Factor 2]

### Impact Assessment
- Signals missed: [X]
- Data gaps: [X]
- Affected symbols: [list]

## Remediation Actions
### Immediate (Done)
- [Action 1]
- [Action 2]

### Short-term (Planned)
- [Action 1]
- [Action 2]

### Long-term (Recommended)
- [Action 1]
- [Action 2]

## Prevention Recommendations
1. [Recommendation 1]
2. [Recommendation 2]
```

## Diagnostic Commands

```sql
-- Check data ingestion status
SELECT symbol, MAX(date) as last_data_date, COUNT(*) as record_count
FROM ohlcv_candles
GROUP BY symbol
HAVING MAX(date) < CURRENT_DATE - INTERVAL '1 day';

-- Check signal generation status
SELECT symbol, signal_date, signal_type, confidence, warning_flag
FROM signals
WHERE signal_date = CURRENT_DATE;

-- Check for duplicate candles
SELECT symbol, date, COUNT(*) as duplicate_count
FROM ohlcv_candles
GROUP BY symbol, date
HAVING COUNT(*) > 1;

-- Check database connection pool
SELECT * FROM pg_stat_activity
WHERE datname = 'swingtrade_db';

-- Check cache status
SELECT * FROM sentiment_cache WHERE expiry_time < NOW();
```

## Example Usage

```bash
# Full pipeline diagnostic
/skill: swing-trade-data-pipeline-diagnostic

# Diagnostic specific component
/skill: swing-trade-data-pipeline-diagnostic --component ingestion

# Diagnostic with auto-remediation
/skill: swing-trade-data-pipeline-diagnostic --auto-fix

# Generate detailed report
/skill: swing-trade-data-pipeline-diagnostic --report --detailed

# Check specific date range
/skill: swing-trade-data-pipeline-diagnostic --date 2024-01-15
```

## Dependencies

- Database access (PostgreSQL/TimescaleDB)
- External API access (Upstox)
- Cache access (Redis)
- Log file access

## Performance Considerations

- Diagnostics should complete within 2 minutes
- Query diagnostics should use read replicas where possible
- Avoid blocking operations during trading hours
- Cache diagnostic results for 5 minutes
