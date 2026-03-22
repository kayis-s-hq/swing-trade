# Phase 2: Strategy Engine

**Phase ID:** 02
**Status:** ✅ Complete
**Date:** 2026-03-08

---

## Objective

Implement technical analysis and signal generation for the swing trading system.

---

## Deliverables

| ID | Component | Status | File |
|----|-----------|--------|------|
| 2.1 | TechnicalIndicators | ✅ Complete | `strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java` |
| 2.2 | DefaultStrategy | ✅ Complete | `strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java` |
| 2.3 | SignalEngine | ✅ Complete | `strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java` |
| 2.4 | BacktestEngine | ✅ Complete | `strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java` |

---

## Implementation Details

### 2.1 TechnicalIndicators Service

**Purpose:** Calculate technical indicators for signal generation.

**Indicators Implemented:**
- **EMA** - Exponential Moving Average (TA4J)
- **SMA** - Simple Moving Average (TA4J)
- **RSI** - Relative Strength Index (TA4J)
- **MACD** - Moving Average Convergence Divergence (TA4J)
- **ATR** - Average True Range (TA4J)
- **Volume MA** - Volume moving average

**Integration:** Uses TA4J library for robust technical analysis calculations.

---

### 2.2 DefaultStrategy

**Purpose:** Multi-factor signal generation logic.

**Signal Generation Logic:**
1. **EMA Crossover Detection**
   - Short-term EMA crosses above long-term EMA = Bullish
   - Short-term EMA crosses below long-term EMA = Bearish

2. **RSI Analysis**
   - RSI < 30 = Oversold (buy signal)
   - RSI > 70 = Overbought (sell signal)

3. **Volume Spike Detection**
   - Volume > 2x Volume MA = Significant interest
   - Confirms price movement validity

4. **Confidence Calculation**
   - All factors aligned = High confidence (0.8-1.0)
   - Partial alignment = Medium confidence (0.5-0.8)
   - Conflicting signals = Low confidence (0.0-0.5)

**Signal Types:**
- **BUY** - Enter long position
- **SELL** - Exit long position / Enter short
- **HOLD** - Maintain current position

---

### 2.3 SignalEngine

**Purpose:** Signal orchestration and scheduling.

**Features:**
- **Scheduled Generation** - Auto-generates signals at 17:00 IST (weekday)
- **Manual Trigger** - API endpoint for manual signal generation
- **Nifty 500 Coverage** - Processes all Nifty 500 stocks
- **Caching** - Redis cache for signal results

**Configuration:**
```properties
signal.enabled=true
signal.schedule=0 17 * * MON-FRI
signal.stocks=nifty500
```

---

### 2.4 BacktestEngine

**Purpose:** Historical backtesting and performance analysis.

**Features:**
- **Date Range Execution** - Runs strategy over specified period
- **Trade Count** - Accurate trade counting
- **Equity Calculation** - Portfolio equity tracking
- **Performance Metrics**
  - Total P&L
  - Win rate
  - Sharpe ratio
  - Max drawdown
  - Average trade duration

**Position Sizing:**
- 20% capital per position
- Max 5 concurrent positions

---

## Completion Criteria

- [x] Technical indicators implemented (5+)
- [x] Signal generation produces correct BUY/SELL/HOLD signals
- [x] Backtest engine calculates accurate performance metrics
- [x] Scheduled signal generation working (17:00 IST)
- [x] TA4J integration complete
- [x] Multi-factor logic implemented

---

## Next Steps

Proceed to **Phase 3: Data Pipeline** for OHLCV data ingestion and storage.
