---
phase: 05-testing-foundation
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - core/src/test/java/com/swingtrade/domain/StockTest.java
  - core/src/test/java/com/swingtrade/domain/OhlcvCandleTest.java
  - core/src/test/java/com/swingtrade/domain/SignalTest.java
  - core/src/test/java/com/swingtrade/domain/PositionTest.java
  - core/src/test/java/com/swingtrade/domain/TradeTest.java
  - core/src/test/java/com/swingtrade/domain/SentimentResultTest.java
autonomous: true
requirements:
  - REQ-101
user_setup:
  - service: "Test Setup"
    why: "Running core domain unit tests"
    env_vars: []
    dashboard_config: []

# WORKTREE WORKFLOW ENFORCEMENT
# CRITICAL: This plan MUST be executed in a git worktree environment
# Root .planning/ is source of truth - worktree .planning/ is working copy
worktree_enforcement:
  required: true
  reason: "Prevents direct edits to root .planning/ on main branch"
  workflow:
    - step: 1
      action: "Verify worktree directory"
      command: "pwd | grep worktrees"
      fail_message: "ERROR: Must be in a worktree directory (e.g., .claude/worktrees/phase-05/)"
    - step: 2
      action: "Verify worktree branch"
      command: "git branch --show-current"
      expected_pattern: "worktree-phase-.*"
      fail_message: "ERROR: Must be on a worktree branch (e.g., worktree-phase-05)"
    - step: 3
      action: "Edit planning docs in worktree"
      path: ".planning/phases/05-testing-foundation/"
      note: "Do NOT edit .planning/ in root repository"
    - step: 4
      action: "Sync to root before merge"
      command: "Skill(\"superpowers:gsd-worktree-workflow --sync-to-root\")"
      when: "Before merging worktree branch to main"
    - step: 5
      action: "Verify before merge"
      command: "gsd:verify"
      when: "After plan completion, before merge"

must_haves:
  truths:
    - Stock constructor validation works for all fields
    - OhlcvCandle business methods (getRange, getChangePercent, isBullish, isBearish) calculate correctly
    - Signal factory method creates valid signals with normalized confidence
    - Position P&L calculations are accurate
    - Trade open/close lifecycle transitions work correctly
    - SentimentResult factory method validates and normalizes confidence
  artifacts:
    - path: "core/src/test/java/com/swingtrade/domain/StockTest.java"
      provides: "Stock domain model tests"
      min_lines: 80
    - path: "core/src/test/java/com/swingtrade/domain/OhlcvCandleTest.java"
      provides: "OhlcvCandle domain model tests"
      min_lines: 100
    - path: "core/src/test/java/com/swingtrade/domain/SignalTest.java"
      provides: "Signal domain model tests"
      min_lines: 90
    - path: "core/src/test/java/com/swingtrade/domain/PositionTest.java"
      provides: "Position domain model tests"
      min_lines: 120
    - path: "core/src/test/java/com/swingtrade/domain/TradeTest.java"
      provides: "Trade domain model tests"
      min_lines: 110
    - path: "core/src/test/java/com/swingtrade/domain/SentimentResultTest.java"
      provides: "SentimentResult domain model tests"
      min_lines: 80
  key_links:
    - from: "core/src/test/java/com/swingtrade/domain/StockTest.java"
      to: "core/src/main/java/com/swingtrade/domain/Stock.java"
      via: "test constructors, equals, hashCode, toString"
      pattern: "assert.*Stock"
    - from: "core/src/test/java/com/swingtrade/domain/PositionTest.java"
      to: "core/src/main/java/com/swingtrade/domain/Position.java"
      via: "calculateUnrealizedPnL, calculatePnLPercent"
      pattern: "calculate.*PnL"
---

<objective>
Create comprehensive unit tests for all core domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult) with 100% code coverage.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS.md (REQ-101)
@.planning/ROADMAP.md (Phase 5 goal)

# Domain Model Interfaces
<!-- Key types and contracts the executor needs -->

From core/src/main/java/com/swingtrade/domain/Stock.java:
```java
public record Stock(
    String symbol,
    Exchange exchange,
    String name,
    Sector sector,
    String isin,
    Integer lotSize,
    LocalDate addedOn
) {
    public enum Exchange { NSE, BSE }
    public enum Sector { AUTO, BANK, CHEMICAL, ... }
}
```

From core/src/main/java/com/swingtrade/domain/OhlcvCandle.java:
```java
public record OhlcvCandle(
    String symbol, LocalDate date, BigDecimal open, BigDecimal high,
    BigDecimal low, BigDecimal close, Long volume, BigDecimal adjClose
) {
    public static OhlcvCandle of(...);
    public BigDecimal getRange();
    public BigDecimal getChangePercent();
    public boolean isBullish();
    public boolean isBearish();
}
```

From core/src/main/java/com/swingtrade/domain/Signal.java:
```java
public record Signal(
    Long id, String symbol, LocalDate date, SignalType type,
    BigDecimal confidence, String reasoning, BigDecimal entryPrice,
    BigDecimal stopLoss, BigDecimal target, BigDecimal riskReward,
    String indicators, LocalDate generatedAt
) {
    public static Signal create(...);
    public boolean isBuySignal();
    public boolean isSellSignal();
    public boolean isHoldSignal();
}
```

From core/src/main/java/com/swingtrade/domain/Position.java:
```java
public record Position(
    Long id, String symbol, BigDecimal entryPrice, LocalDate entryDate,
    Integer quantity, BigDecimal stopLoss, BigDecimal target,
    PositionStatus status, String entryReason, BigDecimal currentPrice
) {
    public static Position createWithRisk(...);
    public BigDecimal calculateUnrealizedPnL(BigDecimal currentPrice);
    public BigDecimal calculatePnLPercent(BigDecimal currentPrice);
}
```

From core/src/main/java/com/swingtrade/domain/Trade.java:
```java
public record Trade(
    Long id, Long positionId, String symbol, LocalDate entryDate,
    LocalDate exitDate, BigDecimal entryPrice, BigDecimal exitPrice,
    Integer quantity, BigDecimal totalPnL, Integer durationDays,
    TradeStatus tradeStatus, String entryReason, String exitReason, BigDecimal fees
) {
    public static Trade open(...);
    public static Trade close(Trade trade, ...);
    public boolean isOpen();
    public boolean isProfitable();
    public boolean isLoss();
}
```

From core/src/main/java/com/swingtrade/domain/SentimentResult.java:
```java
public record SentimentResult(
    Long id, String symbol, LocalDate date, SentimentScore score,
    String summary, String rawContent, Double confidence, LocalDate analyzedAt
) {
    public static SentimentResult create(...);
    public boolean isPositive();
    public boolean isNeutral();
    public boolean isNegative();
    public boolean supportsEntry();
}
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create StockTest with constructor, equals, hashCode, toString</name>
  <files>core/src/test/java/com/swingtrade/domain/StockTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - Stock creates successfully with all fields
    - Exchange enum values are correct (NSE, BSE)
    - Sector enum has 17 values
    - equals() compares all fields correctly
    - hashCode() is consistent with equals()
    - toString() includes all fields
  </behavior>
  <action>
Create StockTest.java with:
- @Test methods for:
  - stock creation with valid NSE/BSE data
  - exchange fullName method
  - all 17 sector enum values
  - equals() true for identical stocks
  - equals() false for different symbols
  - hashCode consistency
  - toString() format verification

Use JUnit 5 + AssertJ. Test all enum values.
  </action>
  <verify>
    <automated>mvn test -pl core -Dtest=StockTest</automated>
  </verify>
  <done>StockTest.java exists with 15+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 2: Create OhlcvCandleTest with validation and business methods</name>
  <files>core/src/test/java/com/swingtrade/domain/OhlcvCandleTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - OhlcvCandle.of() creates candle with adjClose = close
    - getRange() = high - low
    - getChangePercent() = (close - open) / open * 100
    - isBullish() true when close > open
    - isBearish() true when close < open
    - Handles zero open price gracefully
  </behavior>
  <action>
Create OhlcvCandleTest.java with:
- @Test methods for:
  - factory method of() with auto-calculated adjClose
  - getRange() calculation for positive/negative ranges
  - getChangePercent() for bullish/bearish cases
  - isBullish() / isBearish() for all candle types
  - Edge case: zero open price returns 0% change
  - Decimal precision in calculations

Use JUnit 5 + AssertJ with BigDecimal assertions.
  </action>
  <verify>
    <automated>mvn test -pl core -Dtest=OhlcvCandleTest</automated>
  </verify>
  <done>OhlcvCandleTest.java exists with 12+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 3: Create SignalTest with factory and type checks</name>
  <files>core/src/test/java/com/swingtrade/domain/SignalTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - Signal.create() normalizes confidence to 0.0-1.0 range
    - Confidence of 1.5 becomes 1.0, -0.5 becomes 0.0
    - isBuySignal() returns true for BUY type
    - isSellSignal() returns true for SELL type
    - isHoldSignal() returns true for HOLD type
  </behavior>
  <action>
Create SignalTest.java with:
- @Test methods for:
  - create() with confidence clamping (outside 0-1 range)
  - create() with valid confidence in range
  - isBuySignal() / isSellSignal() / isHoldSignal()
  - SignalType enum values (BUY, SELL, HOLD)
  - Generated date defaults to LocalDate.now()
  - Null ID for newly created signals

Use JUnit 5 + AssertJ.
  </action>
  <verify>
    <automated>mvn test -pl core -Dtest=SignalTest</automated>
  </verify>
  <done>SignalTest.java exists with 10+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 4: Create PositionTest with P&L calculations</name>
  <files>core/src/test/java/com/swingtrade/domain/PositionTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - createWithRisk() calculates stopLoss = entry - (2 * ATR)
    - createWithRisk() calculates target = entry + (2.5 * risk)
    - calculateUnrealizedPnL() = (currentPrice - entryPrice) * quantity
    - calculatePnLPercent() = pnl / costBasis * 100
    - isOpen() true for OPEN status
    - isClosed() true for CLOSED/STOPPED/TARGET_HIT
  </behavior>
  <action>
Create PositionTest.java with:
- @Test methods for:
  - createWithRisk() with sample ATR value (verify stopLoss, target formulas)
  - calculateUnrealizedPnL() for profitable/unprofitable cases
  - calculatePnLPercent() for various price scenarios
  - PositionStatus enum values (OPEN, CLOSED, STOPPED, TARGET_HIT)
  - isOpen() / isClosed() state transitions
  - Zero quantity edge case

Use JUnit 5 + AssertJ with BigDecimal comparisons (use epsilon for floats).
  </action>
  <verify>
    <automated>mvn test -pl core -Dtest=PositionTest</automated>
  </verify>
  <done>PositionTest.java exists with 14+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 5: Create TradeTest with lifecycle and P&L</name>
  <files>core/src/test/java/com/swingtrade/domain/TradeTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - Trade.open() creates open trade with null exit fields
    - Trade.close() calculates totalPnL, durationDays, status
    - totalPnL = (exitPrice - entryPrice) * quantity
    - durationDays calculated via ChronoUnit.DAYS
    - isProfitable() true when totalPnL > 0
    - isLoss() true when totalPnL < 0
  </behavior>
  <action>
Create TradeTest.java with:
- @Test methods for:
  - Trade.open() creates OPEN status with null exitDate/exitPrice/totalPnL
  - Trade.close() for profitable trade (CLOSED status)
  - Trade.close() for loss trade (STOPPED status)
  - totalPnL calculation accuracy
  - durationDays calculation
  - isOpen() / isProfitable() / isLoss() predicates
  - Fee handling in P&L calculation

Use JUnit 5 + AssertJ. Test profit/loss scenarios.
  </action>
  <verify>
    <automated>mvn test -pl core -Dtest=TradeTest</automated>
  </verify>
  <done>TradeTest.java exists with 12+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 6: Create SentimentResultTest with score validation</name>
  <files>core/src/test/java/com/swingtrade/domain/SentimentResultTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - SentimentResult.create() normalizes confidence to 0.0-1.0
    - isPositive() returns true for POSITIVE score
    - isNeutral() returns true for NEUTRAL score
    - isNegative() returns true for NEGATIVE score
    - supportsEntry() returns true for POSITIVE or NEUTRAL, false for NEGATIVE
  </behavior>
  <action>
Create SentimentResultTest.java with:
- @Test methods for:
  - create() with confidence clamping (null, outside 0-1 range)
  - SentimentScore enum values (POSITIVE, NEUTRAL, NEGATIVE)
  - isPositive() / isNeutral() / isNegative() predicates
  - supportsEntry() logic (POSITIVE=true, NEUTRAL=true, NEGATIVE=false)
  - analyzedAt defaults to LocalDate.now()

Use JUnit 5 + AssertJ.
  </action>
  <verify>
    <automated>mvn test -pl core -Dtest=SentimentResultTest</automated>
  </verify>
  <done>SentimentResultTest.java exists with 10+ tests, all passing</done>
</task>

</tasks>

<verification>
Overall checks:
1. Run `mvn test -pl core` to verify all 6 test classes pass
2. Check coverage: `mvn jacoco:report -pl core` shows 100% line coverage
3. Verify test file structure follows JUnit 5 + AssertJ conventions
4. Ensure no integration tests (pure unit tests with no Spring context)
</verification>

<success_criteria>
- All 6 domain test classes created
- 69+ total tests across all classes
- 100% code coverage on core domain models
- All tests pass with `mvn test -pl core`
- No test dependencies on Spring context (pure unit tests)
</success_criteria>

<output>
After completion, create `.planning/phases/05-testing-foundation/05-01-core-domain-unit-tests-SUMMARY.md`
</output>
