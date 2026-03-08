# Phase 2: Strategy Engine - Context

**Gathered:** 2026-03-08
**Status:** Ready for planning

---

## Phase Boundary

Technical analysis and signal generation for swing trading. This phase implements:
- TechnicalIndicators service (EMA, SMA, RSI, MACD, ATR, VolumeMA)
- DefaultStrategy with multi-factor signal generation
- BacktestEngine with realistic execution simulation

New capabilities (additional strategies, UI for backtesting) belong in other phases.

---

## Implementation Decisions

### Technical Indicator Coverage

- **Core 5 + VolumeMA** - EMA, SMA, RSI, MACD, ATR, and VolumeMA
- VolumeMA is essential for volume spike detection in DefaultStrategy
- Defer Stochastic, Bollinger Bands, VWAP, ADX to future enhancement

### Indicator Test Data

- **Known input/output** for unit tests (TA4J documentation examples)
- **Synthetic data** for strategy tests (controlled uptrend/downtrend/sideways scenarios)
- Deterministic, reproducible tests

### Indicator Edge Cases

- **Explicit tests** for insufficient data (return null), NaN behavior, null inputs
- Critical for financial calculations where edge cases matter

### Indicator Input Types

- **Both BigDecimal and double** - BigDecimal for domain model compatibility, double for TA4j
- Provide conversion helpers internally

### Strategy Rule Configuration

- **Hybrid approach** - Core constants in code, advanced tuning via application.properties
- Keep defaults working out-of-box, allow power users to tune

### Strategy Customization

- **Parameter tuning only** - Users can tweak periods/thresholds but not core logic
- Balance between flexibility and proven strategy integrity

### Strategy Variants

- **Single default** - One proven strategy for Phase 2
- Keep scope manageable, add variants later if needed

### Strategy Validation

- **Runtime validation** - Check parameter ranges at startup
- Fail fast with clear error messages if invalid config

### Signal Generation Approach

- **Hybrid** - TA4j for rules, custom layer for signal creation
- Use TA4j's powerful rule system, but wrap in domain Signal model

### Backtest Execution Realism

- **Full simulation** - Commission + slippage + partial fills + order queue
- Realistic backtests prevent overfitting and false confidence

### Backtest Metrics

- **Extended metrics** - Total P&L, trade count, win rate, Sharpe ratio, max drawdown, avg trade duration, profit factor
- Comprehensive view of strategy performance

### Backtest Position Sizing

- **Fixed % of capital** - 20% per position (matches broker constraints)
- Consistent with Phase 4 broker risk controls

---

## Claude's Discretion

- Exact TA4J indicator implementation patterns
- Slippage calculation methodology
- Partial fill simulation approach
- Order queue management details

---

## Existing Code Insights

### Reusable Assets

- **TA4J library** - Battle-tested technical analysis library with comprehensive indicator suite
- **Domain models** - Stock, OhlcvCandle, Signal, Position, Trade from Phase 1
- **BaseBarSeries pattern** - Already used in TechnicalIndicators for TA4J integration

### Established Patterns

- **Spring Component injection** - TechnicalIndicators, DefaultStrategy use @Component/@Service
- **Factory methods** - Domain models use create(), open(), close() patterns
- **Record types** - OhlcvCandle, Signal use immutable records

### Integration Points

- **Data module** - OhlcvCandleRepository provides historical price data for backtesting
- **Broker module** - Position sizing (20%) and max positions (5) constraints apply
- **API module** - SignalResponse DTO for signal output

---

## Specific Ideas

No specific requirements — open to standard approaches following TA4J best practices.

---

## Deferred Ideas

- Additional strategy variants (conservative, aggressive presets)
- Stochastic Oscillator, Bollinger Bands, VWAP, ADX indicators
- Backtesting UI for historical analysis
- Pluggable strategy interface for custom strategies

---

*Phase: 02-strategy-engine*
*Context gathered: 2026-03-08*
