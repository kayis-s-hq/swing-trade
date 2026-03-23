---
status: testing
phase: 05-api-layer
source: [05-api-layer-SUMMARY.md]
started: 2026-03-22T00:00:00Z
updated: 2026-03-22T00:00:00Z
---

## Current Test

<!-- OVERWRITE each test - shows where we are -->

number: 1
name: Place Trade Order
expected: |
  POST /api/trades with body {"symbol": "RELIANCE", "quantity": 100, "entryReason": "Technical breakout"} should return OrderResponse with status "ACCEPTED" or "REJECTED" and order confirmation details.
awaiting: user response

## Tests

### 1. Place Trade Order
expected: POST /api/trades with body {"symbol": "RELIANCE", "quantity": 100, "entryReason": "Technical breakout"} should return OrderResponse with status "ACCEPTED" or "REJECTED" and order confirmation details.
result: pending

### 2. Get Portfolio Overview
expected: |
  GET /api/portfolio should return PortfolioResponse with total value, P&L (realized + unrealized), and positions count.
result: pending

### 3. List All Positions
expected: |
  GET /api/positions should return list of all positions (open + closed) with basic position details.
result: pending

### 4. Get Position Details
expected: |
  GET /api/positions/{id} should return detailed position information including entry price, current price, P&L calculations.
result: pending

### 5. Close Position Manually
expected: |
  POST /api/positions/{id}/close should close an open position and return confirmation with exit details.
result: pending

### 6. Get Latest Signals
expected: |
  GET /api/signals/latest should return latest trading signals for all symbols with confidence scores and risk parameters.
result: pending

### 7. Get Signals by Symbol
expected: |
  GET /api/signals/{symbol} should return signals filtered for specific symbol.
result: pending

### 8. List Signals with Filters
expected: |
  GET /api/signals?signalType=BUY&minConfidence=0.7&limit=20 should return filtered signals matching all query parameters.
result: pending

### 9. Get Performance Metrics
expected: |
  GET /api/performance (or embedded in portfolio) should return comprehensive metrics: win rate, total trades, profit factor, max drawdown, Sharpe ratio.
result: pending

### 10. Get Scan Results
expected: |
  GET /api/scan should return current scan results with signal distribution (BUY/SELL/HOLD counts), confidence distribution, and sector breakdown.
result: pending

### 11. Trigger Manual Scan
expected: |
  POST /api/scan should trigger manual signal generation across Nifty 500 stocks and return scan results.
result: pending

### 12. Health Check Endpoint
expected: |
  GET /api/health should return HealthStatus indicating system is running with database and service connectivity.
result: pending

### 13. Error Handling
expected: |
  Invalid requests (e.g., POST /api/trades with missing fields, invalid symbol) should return appropriate HTTP error codes (400, 404) with ErrorResponse containing timestamp, status, error message.
result: pending

## Summary

total: 13
passed: 0
issues: 0
pending: 13
skipped: 0

## Gaps

[none yet]
