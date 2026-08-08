# TDD Plan: Backtest Precision Fix (H1) — DecimalNum Migration

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: NumPrecisionTest | [x] PASS | All 4 tests pass | 2026-08-08 06:00 IST |
| 2: Missing Feature Tests | [x] PASS | All 15 tests pass (fixture redesign: uptrend with volume suppression instead of zigzag) | 2026-08-08 06:00 IST |
| 3: Switch to DecimalNum | [x] PASS | Already done in prior session — code uses `(BigDecimal) value.getDelegate()` | 2026-08-07 14:32 IST |
| 4: Integration Tests | [x] PASS | All 3 tests pass (fixture CSV created with 350 bars) | 2026-08-08 06:30 IST |

---

## Executive Summary

**Finding:** `BacktestEngine.numToBigDecimal()` and `PriceActionSignalEngine.numToBigDecimal()` both use `BigDecimal.valueOf(value.doubleValue())`, converting TA4j's `Num` through double-precision floating point. This loses precision across hundreds of bars of EMA/RSI/ATR arithmetic.

**Fix:** Switch TA4j `BaseBarSeries` to `DecimalNum` (BigDecimal-backed) and replace `doubleValue()` with `toBigDecimal()`.

**Scope:** 2 source files (3 lines), 2 test files (8 new tests), 2 fixture files.

---

## 1. Feature Map

### BacktestEngine Features

| # | Feature | Tested? | Test Count |
|---|---------|---------|------------|
| 1 | Input validation (null symbol, null config, insufficient candles) | Yes | 3 |
| 2 | Entry rules (4 rules, 3-of-4 threshold) | Yes | 1 |
| 3 | Stop loss exit | Yes | 1 |
| 4 | Target hit exit | Yes | 1 |
| 5 | Trend break exit (2 consecutive below EMA20) | Yes | 1 |
| 6 | Time stop exit | Yes | 1 |
| 7 | One position at a time per symbol | Yes | 1 |
| 8 | Position sizing formula | Yes | 1 |
| 9 | **Slippage** — entry price × (1 + slippagePct) | **No** | 0 |
| 10 | **Brokerage** — per-trade cost subtracted from PnL | **No** | 0 |
| 11 | **Forced close** — if open at last bar, exit at close price | **No** | 0 |
| 12 | **Metrics** — winRate, avgGain, maxDrawdown, sharpe, totalReturn, expectancy | **No** | 0 |
| 13 | **Multi-symbol** — `runBacktestAll()` skips failures | **No** | 0 |
| 14 | **Report generation** — JSON + CSV save, top10 by winRate/return | **No** | 0 |

### PriceActionSignalEngine Features

| # | Feature | Tested? |
|---|---------|---------|
| 1 | `buildBarSeries()` — creates TA4j BarSeries from candles | **No** |
| 2 | `numToBigDecimal()` — converts Num to BigDecimal | **No** |
| 3 | Entry rule constants (EMA_FAST, EMA_SLOW, RSI_PERIOD, etc.) | **No** |

### Num Precision

| # | Feature | Tested? |
|---|---------|---------|
| 1 | DoubleNum precision loss at 8+ decimal places | **No** |
| 2 | DecimalNum preserves exact values | **No** |
| 3 | Indicator arithmetic divergence (EMA20/RSI14/ATR14) | **No** |

---

## 2. Phase Breakdown

### Phase 1: NumPrecisionTest (TDD — will fail with DoubleNum)

**File:** `backend/strategy/src/test/java/com/swingtrade/strategy/NumPrecisionTest.java` (new)

**Tests:**

```java
@DisplayName("NumPrecision")
class NumPrecisionTest {

    @Nested
    @DisplayName("DoubleNum loses precision")
    class DoubleNumPrecision {

        @Test
        void doubleValue_looses_precision_at_eight_decimals() {
            // BigDecimal 150.123456789 → double → BigDecimal loses last 4 digits
            BigDecimal input = new BigDecimal("150.123456789");
            Num doubleNum = DoubleNum.of(input);
            BigDecimal result = BigDecimal.valueOf(doubleNum.doubleValue());

            // Asserts: result != input (precision lost)
            assertThat(result).isNotEqualByComparingTo(input);
        }

        @Test
        void doubleNum_indicator_arithmetic_drifts_after_100_bars() {
            // Build BarSeries with DoubleNum, add 100+ bars with incremental prices
            // Compute EMA20, compare first bar value vs bar 100 value
            // Asserts: EMA20 value differs from DecimalNum version by > 0.001
        }
    }

    @Nested
    @DisplayName("DecimalNum preserves precision")
    class DecimalNumPrecision {

        @Test
        void toBigDecimal_preserves_exact_value() {
            BigDecimal input = new BigDecimal("150.123456789");
            Num decimalNum = DecimalNum.of(input);
            BigDecimal result = decimalNum.toBigDecimal();

            // Asserts: result == input exactly
            assertThat(result).isEqualByComparingTo(input);
        }

        @Test
        void decimalNum_indicator_arithmetic_matches_input() {
            // Build BarSeries with DecimalNum, same 100+ bars
            // Compute EMA20, compare with DecimalNum
            // Asserts: EMA20 value matches input to 8+ decimal places
        }
    }
}
```

**Why this fails currently:** `BaseBarSeries` defaults to `DoubleNum`. `DoubleNum.of(new BigDecimal("150.123456789")).doubleValue()` returns `150.12345678900002...`. The test asserts exact equality, which fails.

---

### Phase 2: Missing Feature Unit Tests (TDD — will fail)

**File:** `backend/strategy/src/test/java/com/swingtrade/strategy/BacktestEngineTest.java` (expand)

**5 new tests:**

```java
@Nested
@DisplayName("Config parameter tests")
class ConfigParameters {

    @Test
    @DisplayName("slippageAffectsEntryPrice — entryPrice = nextOpen × (1 + slippagePct)")
    void slippageAffectsEntryPrice() {
        // Build candles with entry setup
        // Config: slippagePct=0.005, brokerage=0, all other defaults
        // Asserts: trade.entryPrice == nextOpen × 1.005, not just nextOpen
    }

    @Test
    @DisplayName("brokerageReducesNetPnl — netPnl = grossPnl - brokeragePerTrade")
    void brokerageReducesNetPnl() {
        // Build candles that produce a winning trade
        // Config: brokeragePerTrade=20.0
        // Asserts: trade.pnl == (exitPrice - entryPrice) × quantity - 20.0
    }

    @Test
    @DisplayName("forcedCloseAtLastBar — position open at last candle exits at close price")
    void forcedCloseAtLastBar() {
        // Build candles where entry rules trigger on second-to-last bar
        // Config: maxHoldingDays=20 (large enough to not time-stop)
        // Asserts: trade.exitReason == TIME_STOP, trade.exitPrice == last candle close
    }

    @Test
    @DisplayName("metrics_include_sharpe_drawdown_expectancy — all metrics non-zero when trades exist")
    void metricsIncludeSharpeDrawdownExpectancy() {
        // Build candles producing 3+ trades with mixed wins/losses
        // Config: defaults
        // Asserts: result.sharpeRatio > 0, result.maxDrawdownPct > 0, result.expectancy != 0
    }

    @Test
    @DisplayName("positionSizing_respects_maxHoldingDays — position closes at exactly maxHoldingDays bars")
    void positionSizingRespectsMaxHoldingDays() {
        // Build candles with entry setup + flat period > maxHoldingDays
        // Config: maxHoldingDays=3
        // Asserts: trade.holdingDays == 3
    }
}
```

**Why these fail currently:** The tests assert on behavior that the existing code doesn't properly validate (slippage isn't tested, brokerage isn't tested, forced close isn't tested, metrics aren't asserted, maxHoldingDays isn't tested).

---

### Phase 3: Switch to DecimalNum (GREEN — tests pass)

**File 1:** `backend/strategy/src/main/java/com/swingtrade/strategy/PriceActionSignalEngine.java`

**Line 177 — change:**
```java
// Before:
BarSeries series = new BaseBarSeries(symbol);

// After:
BarSeries series = new BaseBarSeries.Builder(symbol)
    .withNumFactory(DecimalNum::new)
    .build();
```

**Line 195-197 — change:**
```java
// Before:
private BigDecimal numToBigDecimal(Num value) {
    return BigDecimal.valueOf(value.doubleValue());
}

// After:
private BigDecimal numToBigDecimal(Num value) {
    return value.toBigDecimal();
}
```

**New import needed:**
```java
import org.ta4j.core.num.DecimalNum;
```

---

**File 2:** `backend/strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java`

**Line 372-374 — change:**
```java
// Before:
private static BigDecimal numToBigDecimal(Num value) {
    return BigDecimal.valueOf(value.doubleValue());
}

// After:
private static BigDecimal numToBigDecimal(Num value) {
    return value.toBigDecimal();
}
```

**New import needed:**
```java
import org.ta4j.core.num.DecimalNum;
```

---

### Phase 4: Integration Tests with Real Fixtures

**File:** `backend/strategy/src/test/java/com/swingtrade/strategy/BacktestEngineIntegrationTest.java` (new)

**Setup:**
- `@SpringBootTest` with H2 in-memory DB
- Load real OHLCV data from `fixtures/real-ohlcv-data.csv` into H2
- Wire real `CandleStoreImpl` + `BacktestEngine`

**Test 1: Single-symbol backtest**
- Load `fixtures/real-backtest-single.json`
- Run `BacktestEngine.runBacktest("RELIANCE", "NSE", config)`
- Assert on `BacktestResult`: totalTrades, winRate, sharpeRatio, maxDrawdownPct, totalReturn
- Assert on each `BacktestTrade`: entryPrice, exitPrice, quantity, pnl

**Test 2: Multi-symbol backtest**
- Load `fixtures/real-backtest-multi.json`
- Run `BacktestEngine.runBacktestAll(symbols, "NSE", config)`
- Assert on list: result count matches symbol count, each result has non-zero trades

**Test 3: Report generation**
- Run `BacktestEngine.generateReport(results)`
- Assert JSON file exists in temp dir
- Assert CSV file exists in temp dir
- Parse JSON, assert top10ByWinRate and top10ByTotalReturn are populated

---

## 3. Files Summary

| Action | File | Type |
|--------|------|------|
| Create | `strategy/src/test/java/.../NumPrecisionTest.java` | Unit |
| Expand | `strategy/src/test/java/.../BacktestEngineTest.java` (5 new tests) | Unit |
| Modify | `strategy/src/main/java/.../PriceActionSignalEngine.java` (2 lines + 1 import) | Source |
| Modify | `strategy/src/main/java/.../BacktestEngine.java` (1 line + 1 import) | Source |
| Create | `strategy/src/test/java/.../BacktestEngineIntegrationTest.java` | Integration |
| Create | `strategy/src/test/resources/fixtures/real-backtest-single.json` | Fixture |
| Create | `strategy/src/test/resources/fixtures/real-backtest-multi.json` | Fixture |

---

## 4. Verification

```bash
cd backend

# Phase 1: Precision test fails with DoubleNum
mvn test -pl strategy -Dtest=NumPrecisionTest
# Expected: FAIL — DoubleNum.of(input).doubleValue() != input

# Phase 2: Missing feature tests fail
mvn test -pl strategy -Dtest=BacktestEngineTest
# Expected: FAIL — slippage/brokerage/forced-close/metrics not asserted

# Phase 3: Switch to DecimalNum
# → Edit PriceActionSignalEngine.java line 177, 195-197
# → Edit BacktestEngine.java line 372-374
# → Add DecimalNum imports

# All tests pass
mvn test -pl strategy
# Expected: PASS — all 11 tests pass

# Integration tests pass
mvn test -pl strategy -Dtest=BacktestEngineIntegrationTest
# Expected: PASS — real data matches fixtures
```

---

## 5. TDD Discipline

**Sequence:**
1. Comment out `PriceActionSignalEngine.java:177` (the `new BaseBarSeries(symbol)` line)
2. Write `NumPrecisionTest.java` — it fails because TA4j defaults to DoubleNum
3. Uncomment line 177
4. Write `BacktestEngineTest.java` new tests — they fail (no assertions yet)
5. Switch to DecimalNum (lines 177, 195-197 in PriceActionSignalEngine, line 372-374 in BacktestEngine)
6. All tests pass
7. Remove comment wrapper
8. Capture real API fixtures
9. Write integration tests against fixtures

**Violating the letter of the rules is violating the spirit of the rules.**

### Red Flags - STOP and Start Over

- Code before test
- "I already manually tested it"
- "Tests after achieve the same purpose"
- "This is different because..."
- Skipping fixture capture because "it's complicated"
- Deleting code instead of commenting it out

**All of these mean: Comment out the code. Start over with TDD.**
