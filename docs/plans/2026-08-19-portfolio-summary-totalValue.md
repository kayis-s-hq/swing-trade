# TDD Plan: Portfolio Summary totalValue Fix

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: PerformanceResponse.of() populates totalPnL | [x] PASS | All 6 tests pass. Added `totalValue` field + 5 new params to `of()` factory. Fixed winRate calculation (was multiplying instead of dividing). | 2026-08-19 16:40 IST |
| 2: PerformanceService returns totalValue (null) | [x] PASS | 1 test passes. Null portfolio returns null totalValue. | 2026-08-19 16:42 IST |
| 3: PerformanceService returns totalValue (computed) | [x] PASS | 5 tests pass. Added `calculateTotalValue()`, `calculateProfitFactor()`. Fixed mock `brokerType="PAPER"` for PositionEntity. | 2026-08-19 16:45 IST |
| 4: Frontend getPortfolioSummary uses real totalValue | [x] PASS | `client.ts:253` uses `perf.totalPnL ?? 0`; `client.ts:259` uses `toNum(perf.totalValue)`. | 2026-08-19 16:47 IST |
| 5: DashboardView currency Rs. | [x] PASS | DashboardView lines 236, 241: `$` → `Rs.`. PortfolioView line 138: `$` → `Rs.`. | 2026-08-19 16:48 IST |

---

## Problem

`getPortfolioSummary()` in `client.ts:259` returns `totalValue: 0` with comment "Would need a separate endpoint or derive from positions". The backend `PerformanceResponse.of()` factory does NOT populate `totalPnL` or `totalValue` — they remain null. The frontend tries to read `perf.totalPnL` (null) and hardcodes `totalValue: 0`.

## Root Cause

`PerformanceResponse.of()` only sets 6 fields. `totalPnL`, `totalValue`, `averageWin`, `averageLoss`, `profitFactor` are never set. The `PerformanceService.getPortfolioPerformance()` method calls `of()` with only the 6 params and never populates the rest.

## Fix Scope

1. **Backend**: Add `totalPnL` and `totalValue` to `PerformanceResponse.of()` factory; populate in `PerformanceService.getPortfolioPerformance()`
2. **Frontend**: Use `data.totalValue` instead of `0`; use `data.totalPnl` instead of `totalPnL`
3. **Frontend**: Fix `$` → `Rs.` in DashboardView lines 236, 241

---

## Phase 1: PerformanceResponse.of() populates totalPnL

**File**: `backend/api/src/main/java/com/swingtrade/api/dto/PerformanceResponseTest.java` (new)

**Test methods**:
```java
class OfFactory {
    @Test
    void of_populatesTotalPnL() {
        // Given: totalPnL = 50000
        // When: PerformanceResponse.of(..., 50000, ...)
        // Then: response.getTotalPnL() == 50000
    }

    @Test
    void of_populatesTotalValue() {
        // Given: totalValue = 1050000
        // When: PerformanceResponse.of(..., 1050000, ...)
        // Then: response.getTotalValue() == 1050000
    }

    @Test
    void of_populatesAverageWin() {
        // Given: averageWin = 3000
        // Then: response.getAverageWin() == 3000
    }

    @Test
    void of_populatesAverageLoss() {
        // Given: averageLoss = 2000
        // Then: response.getAverageLoss() == 2000
    }

    @Test
    void of_populatesProfitFactor() {
        // Given: profitFactor = 1.5
        // Then: response.getProfitFactor() == 1.5
    }
}
```

**Factory signature change** (PerformanceResponse.java line 209-215):
```java
// FROM:
public static PerformanceResponse of(
    BigDecimal totalReturn, BigDecimal annualizedReturn, BigDecimal sharpeRatio,
    BigDecimal maxDrawdown, Integer totalTrades, Integer winningTrades)

// TO:
public static PerformanceResponse of(
    BigDecimal totalReturn, BigDecimal annualizedReturn, BigDecimal sharpeRatio,
    BigDecimal maxDrawdown, Integer totalTrades, Integer winningTrades,
    BigDecimal totalPnL, BigDecimal totalValue, BigDecimal averageWin,
    BigDecimal averageLoss, BigDecimal profitFactor)
```

**Why it will fail**: Factory doesn't accept the new parameters yet.

---

## Phase 2: PerformanceService returns totalValue (null)

**File**: `backend/api/src/main/java/com/swingtrade/api/service/PerformanceServiceTest.java` (new)

**Test methods**:
```java
class GetPortfolioPerformance {
    @Test
    void whenNoPortfolio_totalValueIsNull() {
        // Given: paperTradingEngine.getPortfolio() returns null
        // When: performanceService.getPortfolioPerformance()
        // Then: response.getTotalValue() == null
    }
}
```

**Why it will fail**: `getPortfolioPerformance()` doesn't call `getTotalPnL()` or compute `totalValue`.

---

## Phase 3: PerformanceService returns totalValue (computed)

**File**: Same test class, same `GetPortfolioPerformance` nested class

**Test methods**:
```java
@Test
void whenPortfolioExists_totalValueIsComputed() {
    // Given: portfolio with currentCapital=1000000, one open position with unrealizedPnL=50000
    // When: performanceService.getPortfolioPerformance()
    // Then: response.getTotalValue() == 1050000
    // And:  response.getTotalPnL() == engine.getTotalPnL()
}

@Test
void whenPortfolioExists_totalPnLFromEngine() {
    // Given: engine with getTotalPnL() = 75000
    // When: performanceService.getPortfolioPerformance()
    // Then: response.getTotalPnL() == 75000
}

@Test
void whenPortfolioExists_averageWinFromClosedPositions() {
    // Given: closed positions with avg win = 3000
    // Then: response.getAverageWin() == 3000
}

@Test
void whenPortfolioExists_averageLossFromClosedPositions() {
    // Given: closed positions with avg loss = 2000
    // Then: response.getAverageLoss() == 2000
}

@Test
void whenPortfolioExists_profitFactorComputed() {
    // Given: closed positions with grossWin=9000, grossLoss=6000
    // Then: response.getProfitFactor() == 1.5
}
```

**Code changes**:

`PerformanceService.getPortfolioPerformance()` (line 38-48):
```java
public PerformanceResponse getPortfolioPerformance() {
    PerformanceStats stats = getPerformanceStats();
    BigDecimal totalPnL = calculateTotalPnL();
    BigDecimal totalValue = calculateTotalValue();
    BigDecimal avgWin = calculateAvgWin(fetchClosedPositions());
    BigDecimal avgLoss = calculateAvgLoss(fetchClosedPositions());
    BigDecimal profitFactor = calculateProfitFactor(fetchClosedPositions());

    return PerformanceResponse.of(
        stats.getTotalReturn(),
        stats.getAnnualizedReturn(),
        stats.getSharpeRatio(),
        stats.getMaxDrawdown(),
        stats.getTotalTrades(),
        stats.getWinningTrades(),
        totalPnL,
        totalValue,
        avgWin,
        avgLoss,
        profitFactor
    );
}
```

New private methods:
```java
private BigDecimal calculateTotalValue() {
    var portfolio = paperTradingEngine.getPortfolio();
    if (portfolio == null) return null;
    return portfolio.getTotalValue();
}

private BigDecimal calculateProfitFactor(List<PositionEntity> closed) {
    if (closed.isEmpty()) return BigDecimal.ZERO;
    BigDecimal grossWins = closed.stream()
        .filter(e -> e.getRealizedPnL() != null && e.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
        .map(PositionEntity::getRealizedPnL)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal grossLosses = closed.stream()
        .filter(e -> e.getRealizedPnL() != null && e.getRealizedPnL().compareTo(BigDecimal.ZERO) < 0)
        .map(e -> e.getRealizedPnL().abs())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (grossLosses.compareTo(BigDecimal.ZERO) == 0) return grossWins.compareTo(BigDecimal.ZERO) > 0
        ? BigDecimal.valueOf(999) : BigDecimal.ZERO;
    return grossWins.divide(grossLosses, 2, RoundingMode.HALF_UP);
}
```

**Why it will fail**: Method doesn't compute totalValue or pass it to factory.

---

## Phase 4: Frontend uses real totalValue

**File**: `dashboard/src/api/client.ts` line 259

**Change**:
```typescript
// FROM:
totalValue: 0, // Would need a separate endpoint or derive from positions

// TO:
totalValue: toNum(perf.totalValue),
```

**File**: `dashboard/src/api/client.ts` line 253

**Change**:
```typescript
// FROM:
const totalPnL = toNum(perf.totalPnL)

// TO:
const totalPnL = toNum(perf.totalPnL ?? 0)
```

**Why it will fail**: `perf.totalValue` is currently null from the backend. After Phase 3 fix, it will return real values.

---

## Phase 5: DashboardView currency Rs.

**File**: `dashboard/src/views/DashboardView.vue`

**Lines 236, 241**:
```vue
// FROM:
{ title: 'Value', value: `$${(portfolioSummary.value?.totalValue ?? 0).toLocaleString()}` },
value: `$${(portfolioSummary.value?.totalPnl ?? 0).toLocaleString()}`,

// TO:
{ title: 'Value', value: `Rs.${(portfolioSummary.value?.totalValue ?? 0).toLocaleString()}` },
value: `Rs.${(portfolioSummary.value?.totalPnl ?? 0).toLocaleString()}`,
```

**File**: `dashboard/src/views/PortfolioView.vue` line 138

```vue
// FROM:
{{ trade.currentPrice ? '$' + trade.currentPrice : '—' }}

// TO:
{{ trade.currentPrice ? 'Rs.' + trade.currentPrice : '—' }}
```

---

## Files Summary

| Action | File | Type |
|--------|------|------|
| Create | `backend/api/src/test/java/com/swingtrade/api/dto/PerformanceResponseTest.java` | Unit |
| Create | `backend/api/src/test/java/com/swingtrade/api/service/PerformanceServiceTest.java` | Unit |
| Modify | `backend/api/src/main/java/com/swingtrade/api/dto/PerformanceResponse.java` (+2 params to `of()`) | Source |
| Modify | `backend/api/src/main/java/com/swingtrade/api/service/PerformanceService.java` (populate totalValue/totalPnL) | Source |
| Modify | `dashboard/src/api/client.ts` (line 259, 253) | Frontend |
| Modify | `dashboard/src/views/DashboardView.vue` (lines 236, 241) | Frontend |
| Modify | `dashboard/src/views/PortfolioView.vue` (line 138) | Frontend |

---

## Verification

```bash
# Phase 1: RED
./gradlew :api:test --tests=PerformanceResponseTest  # fails — factory doesn't accept new params

# Phase 1: GREEN
# Add params to PerformanceResponse.of()

./gradlew :api:test --tests=PerformanceResponseTest  # pass

# Phase 2-3: RED
./gradlew :api:test --tests=PerformanceServiceTest  # fails — no totalValue computation

# Phase 2-3: GREEN
# Add calculateTotalValue() and pass values to factory

./gradlew :api:test  # all pass
./gradlew :api:checkstyleMain :api:checkstyleTest  # style clean
```

---

## Mock Map

| Dependency | Mock or Real | Rationale |
|-----------|-------------|-----------|
| `PaperTradingEngine` | Mock | Controls portfolio state and PnL values |
| `PositionRepository` | Mock | Needed for `fetchClosedPositions()` in avgWin/avgLoss/profitFactor |
| `Portfolio` | Real | Already tested in `PortfolioTest`; use real object |
| `PositionEntity` | Real (via Mockito) | For closed position aggregation tests |

## Test Design Notes

- Use `Portfolio` constructor with `BigDecimal("1000000")` for initial capital
- Build `Position` records using same pattern as `PortfolioTest.makePosition()`
- `PerformanceResponse` is a plain DTO — no mocks needed for it
- All BigDecimal comparisons use exact equality (no floating point)