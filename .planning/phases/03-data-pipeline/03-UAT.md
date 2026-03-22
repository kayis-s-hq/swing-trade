---
status: testing
phase: 03-data-pipeline
source: DataIngestionService.java, MarketDataClient.java
started: 2026-03-22T00:00:00Z
updated: 2026-03-22T00:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Auto-Ingest Data Scheduling
expected: |
  The system has a scheduled job that runs daily at 16:30 IST on weekdays (Monday-Friday) to ingest end-of-day OHLCV data for all Nifty 500 stocks. This runs after market close.
result: pass

### 2. Backfill Historical Data
expected: |
  User can trigger a backfill for a specific stock covering a configurable number of years of historical data.
result: pass

### 3. Process Stock Data Range
expected: |
  User can process stock data for a specific date range, fetching and saving candles for each trading day.
result: pass

### 4. Skip Existing Candles
expected: |
  When processing data, the system checks if a candle already exists and skips it to avoid duplicates.
result: pass

### 5. Data Quality Validation
expected: |
  User can validate data quality for a stock, which reports expected vs actual trading days, identifies gaps, and detects price anomalies.
result: pass

### 6. Get Latest Candle
expected: |
  User can retrieve the most recent candle for a stock.
result: pass

### 7. Get Recent Candles
expected: |
  User can retrieve the last N days of candles for a stock.
result: pass

### 8. Price Anomaly Detection
expected: |
  The system detects invalid price data where High < Open/Close or Low > Open/Close.
result: pass

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
