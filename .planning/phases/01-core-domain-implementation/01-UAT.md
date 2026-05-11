---
status: complete
phase: 01-core-domain-implementation
source: 01-core-domain-implementation-CONTEXT.md
started: 2026-04-11T11:23:00Z
updated: 2026-04-11T11:27:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Stock Model Structure
expected: |
  The Stock record has all required fields: symbol, exchange, name, sector, isin, lotSize, addedOn.
  Exchange enum has NSE and BSE with full names.
  Sector enum has all 17 sectors defined.
result: pass

### 2. Signal Model with Confidence Normalization
expected: |
  The Signal record has id, symbol, date, type, confidence, reasoning, entryPrice, stopLoss, target, riskReward, indicators, generatedAt.
  SignalType enum has BUY, SELL, HOLD with descriptions.
  The create() factory method normalizes confidence to 0.0-1.0 range.
result: pass

### 3. Position P&L Calculations
expected: |
  The Position record has id, symbol, entryPrice, entryDate, quantity, stopLoss, target, status, entryReason, currentPrice.
  PositionStatus enum has OPEN, CLOSED, STOPPED, TARGET_HIT.
  createWithRisk() calculates stopLoss as entry - 2*ATR and target as entry + 2.5*risk.
  calculateUnrealizedPnL() and calculatePnLPercent() work correctly.
result: pass

### 4. Trade Lifecycle Management
expected: |
  The Trade record has id, positionId, symbol, entryDate, exitDate, entryPrice, exitPrice, quantity, totalPnL, durationDays, tradeStatus, entryReason, exitReason, fees.
  TradeStatus enum has OPEN, CLOSED, STOPPED, TARGET_HIT, TIME_STOP.
  open() creates a new open trade with null exit fields.
  close() calculates totalPnL, durationDays, and sets appropriate status.
result: pass

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]

## Test Results

```
mvn test -pl core
Tests run: 306, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
