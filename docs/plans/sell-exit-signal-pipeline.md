# SELL/Exit Signal Generation for the Live Signal Pipeline

Adds SELL/exit signal generation to the live pipeline (`PriceActionSignalEngine` ->
`SignalPipeline.generatePrimarySignal` -> `JobOrchestratorService`'s SIGNAL stage), wires
broker close-out for held positions, extends `BacktestEngine` to support the same exit rules
(opt-in, backward compatible), and deletes the dead `SwingTradingStrategy` class.

## 0. Phase Status

| Phase | Status | Result | Timestamp |
|---|---|---|---|
| 1. SELL/exit confluence in `PriceActionSignalEngine` | [x] PASS | 8/8 tests green (`./gradlew :strategy:test --tests PriceActionSignalEngineTest`); full `:strategy:test` also green | 2026-08-27 |
| 2. `SignalPipeline` position-held lookup (persist-only path) | [x] PASS | `SignalPipelineTest` green (all 5 tests, incl. new `SellSignalPositionLookup` nested class); full `:api:test` also green | 2026-08-27 |
| 3. Broker close-out for held positions | [x] PASS (unit) / [ ] BLOCKED (integration, infra) | Unit: `SignalPipelineTest` green (all 8 tests, incl. new `SellSignalBrokerCloseOut` nested class); full `:api:test` also green. Integration: `SignalPipelineSellExitIntegrationTest` written (`@SpringBootTest` + real TestContainers Postgres, no mocks) and compiles cleanly against the `integrationTest` source set (required a real classpath-wiring fix in root `build.gradle.kts` — the source set previously couldn't see main/test classes at all). Could not be executed in this sandbox: the only reachable Docker daemon is the pi-node host via `ssh://` (unsupported scheme in docker-java/TestContainers 1.21.3 — latest available) or via an SSH-tunneled TCP socket to the same daemon (reaches it, but TestContainers' strategy-discovery probe is rejected by the pi-node's Docker Engine 29.x with `client version 1.32 is too old, minimum supported API version is 1.40`). Needs either a docker-java/TestContainers version bump investigation or a CI environment with a compatible Docker daemon. See final report for options. | 2026-08-27 |
| 4. `BacktestEngine` support for signal-driven exits | [x] PASS | `BacktestEngineTest` green (all 4 new `SignalDrivenExit` tests + all 12 existing tests mechanically updated with `, false`); full `:strategy:test` and `:api:test` also green. `docs/backtesting.md` updated with the SIGNAL_EXIT rule (opt-in, cross-referenced to `PriceActionSignalEngine`). | 2026-08-27 |
| 5. Delete `SwingTradingStrategy` (dead code) | [x] PASS | Grep confirmed no other references and no test file existed; deleted `strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java`; `:strategy:compileJava :api:compileJava` and `:strategy:test :api:test` both green after deletion. `TechnicalIndicators` kept unchanged (has other live consumers, verified). | 2026-08-27 |

## 1. Feature Map

| Feature | Tested? | Test type |
|---|---|---|
| `PriceActionSignalEngine.analyze()` returns SELL on any-1-of-3 exit confluence | Yes | Unit (Mockito, synthetic candles) |
| `PriceActionSignalEngine.analyze()` still returns HOLD when 0-of-3 exit conditions fire | Yes | Unit |
| `SignalPipeline.generatePrimarySignal()` looks up held position for every SELL | Yes | Unit (Mockito) |
| Non-held symbol + SELL -> signal persisted, no broker action | Yes | Unit (Mockito) |
| Held symbol + SELL -> `PositionService.closePosition()` invoked | Yes | Unit (Mockito) |
| Held symbol + SELL -> position actually CLOSED in DB (full pipeline) | Yes | Integration (`@SpringBootTest`, TestContainers Postgres, real beans) |
| Close-out failure does not block SELL signal persistence | Yes | Unit (Mockito) |
| `BacktestEngine` SIGNAL_EXIT exit reason (opt-in via `BacktestConfig.signalExitEnabled`) | Yes | Unit (Mockito `CandleStore`/`WatchlistStore`, real `PriceActionSignalEngine`) |
| `BacktestEngine` existing TREND_BREAK/TIME_STOP/STOP_LOSS/TARGET_HIT unaffected by default | Yes | Unit (existing tests, `signalExitEnabled=false`) |
| `SwingTradingStrategy` removed, `TechnicalIndicators` retained (has other consumers) | N/A (deletion) | Compile-time (module still builds) |

## 2. Phase Breakdown

### Phase 1 — SELL/exit confluence in `PriceActionSignalEngine`

**Design (derived from the actual code, confirmed against `StrategyParams`/`PriceActionSignalEngine`):**

Entry (BUY) requires **all 4** of: `price > EMA20 > EMA50`, `RSI in [50, 65]`, `volume > 1.5x
VolumeMA20`, `price` within 3% of the 252-day high (`PriceActionSignalEngine.analyze()`,
lines 139-167). The exit rule is the natural, mirrored inverse of the *trend + RSI* half of
that confluence (per locked-in decision #3), evaluated only when the entry confluence does
**not** hold (BUY and SELL are mutually exclusive by construction — condition A/C are
literal negations of two of the four entry sub-conditions, so they can never be true at the
same time entry is satisfied):

- **A** — `close < EMA20` (immediate, no streak — unlike the existing `BacktestEngine`
  `TREND_BREAK` rule, which requires 2 consecutive days; this is intentionally looser per
  decision #1, "enter carefully, exit quickly")
- **B** — `EMA20 < EMA50` (structural trend violation)
- **C** — `RSI < 50` (the same lower bound `StrategyParams.RSI_LOWER` that gates entry)

Any **one** of A/B/C -> SELL. None of them -> HOLD (existing behavior, message unchanged).

**What to test** — `strategy/src/test/java/com/swingtrade/strategy/PriceActionSignalEngineTest.java`

New `@Nested @DisplayName("ExitRules") class ExitRules`:

1. `analyze_closeBelowEma20_returnsSellSignal()`
   - Build candles with `buildZigzagUptrendCandles(299, 100.0, 0.5, 0.75, 1_000_000L)` (same
     base as the existing BUY test — guarantees RSI in the 50-65 band and `EMA20 > EMA50`
     going in), then append **one** final candle whose close is ~3% below the prior close
     (isolates condition A without moving RSI below 50 or inverting EMA20/EMA50 in a single
     day — verify when running; if RSI or EMA20/EMA50 also flip, reduce the drop magnitude).
   - Assert `result.type() == SignalType.SELL`.
   - Assert `result.reasoning()` starts with `"Exit rule triggered"` and contains
     `"Close < EMA20"`.

2. `analyze_rsiDropsBelowFifty_returnsSellSignal()` (replaces
   `analyze_withRsiOutOfRange_returnsHoldSignal`, which asserted HOLD for a monotonically
   declining series — under the new rules that series now satisfies condition C, and likely
   A too, so it must SELL, not HOLD)
   - Reuse `buildTrendingCandles(300, 100.0, -0.05, 1_000_000L)` unchanged.
   - Assert `result.type() == SignalType.SELL`.
   - Assert `result.reasoning()` contains `"RSI < 50"`.

3. `analyze_crashBelowWeeklyHigh_alsoTripsCloseBelowEma20_returnsSellSignal()` (replaces
   `analyze_withPriceFarFromWeeklyHigh_returnsHoldSignal` — the crash-to-50 final candle in
   that test necessarily also trips condition A, so it must SELL, not HOLD, under the new
   rules)
   - Keep the exact same candle construction (crash final candle to price=50).
   - Assert `result.type() == SignalType.SELL`.
   - Assert `result.reasoning()` contains `"Close < EMA20"`.

4. `analyze_withoutVolumeSurge_returnsHoldSignal()` (existing test, **enhance, don't
   replace** — this is the "0-of-3 exit conditions trigger" case from the suggested
   breakdown: mild continual uptrend, no volume surge, so entry fails, but trend/RSI stay
   intact so no exit condition fires either)
   - Add `assertThat(result.type()).isEqualTo(SignalType.HOLD);` (not currently asserted).
   - Add `assertThat(result.rsi()).isGreaterThan(65.0);` to make explicit that RSI sitting
     *above* the entry band (not just outside it) still correctly HOLDs rather than SELLs —
     condition C only fires below 50.

**Why these fail now:** `SignalType` is only ever `BUY` or `HOLD` in `analyze()` today (line
163: `SignalType type = enoughRulesPassed ? SignalType.BUY : SignalType.HOLD;`) — `SELL` is
never produced, so assertions on `SignalType.SELL` fail, and the reasoning text
`"Exit rule triggered"` does not exist anywhere in the class.

**Switch to fix** — `strategy/src/main/java/com/swingtrade/strategy/PriceActionSignalEngine.java`

Replace lines 158-167 (`int rulesPassed = ...` through the `reasoning` ternary) with:

```java
int rulesPassed = (trendAligned ? 1 : 0) + (rsiInRange ? 1 : 0) + (volumeSurge ? 1 : 0) + (nearWeeklyHigh ? 1 : 0);
// All 4 entry rules must hold — see docs/backtesting.md "Entry rules (same as the live
// signal engine)". BacktestEngine.tryEnter mirrors this threshold so the backtest never
// drifts from the live signal engine's rules.
boolean enoughRulesPassed = rulesPassed == 4;

SignalType type;
String reasoning;
if (enoughRulesPassed) {
    type = SignalType.BUY;
    reasoning = "All entry rules passed: %s".formatted(String.join("; ", passed));
} else {
    // Exit confluence: ANY 1 of 3 trend/RSI conditions fires a SELL — deliberately looser
    // than the strict "all 4 of 4" entry confluence ("enter carefully, exit quickly").
    // Conditions A and C are the literal inverse of two of the four entry sub-conditions
    // above, so BUY and SELL can never both be true for the same candle.
    List<String> exitPassed = new ArrayList<>();
    List<String> exitFailed = new ArrayList<>();

    boolean closeBelowEma20 = price.compareTo(ema20) < 0;
    recordRule(closeBelowEma20, exitPassed, exitFailed,
        "Close < EMA20 (close=%s, ema20=%s)".formatted(fmt(price), fmt(ema20)));

    boolean ema20BelowEma50 = ema20.compareTo(ema50) < 0;
    recordRule(ema20BelowEma50, exitPassed, exitFailed,
        "EMA20 < EMA50 (ema20=%s, ema50=%s)".formatted(fmt(ema20), fmt(ema50)));

    boolean rsiBelowLowerBound = rsi.compareTo(RSI_LOWER_BOUND) < 0;
    recordRule(rsiBelowLowerBound, exitPassed, exitFailed,
        "RSI < 50 (rsi=%s)".formatted(fmt(rsi)));

    if (closeBelowEma20 || ema20BelowEma50 || rsiBelowLowerBound) {
        type = SignalType.SELL;
        reasoning = "Exit rule triggered (%d of 3): %s".formatted(exitPassed.size(), String.join("; ", exitPassed));
    } else {
        type = SignalType.HOLD;
        reasoning = "Entry rules failed (%d of 4 passed): %s".formatted(rulesPassed, String.join("; ", failed));
    }
}
```

No other lines in the class change (`recordRule`, `fmt`, `buildBarSeries`, the metrics calls,
and the `SignalResult` construction at the bottom are all unaffected — `SignalResult` already
declares `type` as `SignalType`, which already includes `SELL`, per its own javadoc at line 30).

---

### Phase 2 — `SignalPipeline` position-held lookup (persist-only path for non-held symbols)

**What to test** — `api/src/test/java/com/swingtrade/api/service/SignalPipelineTest.java`

New `@Nested @DisplayName("SellSignalPositionLookup") class SellSignalPositionLookup`,
plus a new `@Mock private PositionStore positionStore;` field and updated constructor call
in the existing `setUp()`:

1. `sellSignal_symbolNotHeld_persistsSellSignal()`
   - Stub `priceActionEngine.analyze(...)` (actually `generatePrimarySignal` calls
     `priceActionEngine.analyze(symbol, chronologicalCandles)` per the existing code path) to
     return a `SignalResult` with `type = SignalType.SELL`.
   - Stub `positionStore.findBySymbol(SYMBOL)` -> `Optional.empty()`.
   - Stub `persistenceService.buildAndSaveWithWarning(...)` to return a saved SELL `Signal`.
   - Call `pipeline.generatePrimarySignal(SYMBOL)`.
   - `verify(positionStore).findBySymbol(SYMBOL)` — confirms the lookup happens for every
     SELL.
   - `verify(persistenceService).buildAndSaveWithWarning(eq(SYMBOL), any(), eq(SignalType.SELL), ...)`.

2. `sellSignal_symbolHeld_lookupDetectsHeldPosition_stillPersistsSignal()`
   - Same as above but `positionStore.findBySymbol(SYMBOL)` -> `Optional.of(<a Position
     fixture>)` (use `DomainObjectFactory` from `core/src/test/java/.../domain/fixtures/` if
     it has a `Position` builder; otherwise construct via `Position`'s existing factory
     method used elsewhere in the codebase).
   - Assert the signal is still persisted as SELL (Phase 2 does not close anything yet —
     that's Phase 3).

3. `buySignal_doesNotConsultPositionStore()` (regression guard)
   - `SignalResult` with `type = SignalType.BUY`; stub `sentimentGate.evaluate(...)` to
     `SentimentGate.SentimentVerdict.allow()`.
   - `verifyNoInteractions(positionStore)`.

4. `holdSignal_doesNotConsultPositionStore()` — same shape, `type = SignalType.HOLD`.

**Why these fail now:** `SignalPipeline`'s constructor takes exactly 4 args
(`CandleStore, PriceActionSignalEngine, SignalPersistenceService, SentimentGate`) — the test
file will not compile with a 5th `PositionStore` constructor arg until the production
constructor is updated, and `positionStore.findBySymbol(...)` is never called by any existing
code path.

**Switch to fix** — `api/src/main/java/com/swingtrade/api/service/SignalPipeline.java`

- Add import `com.swingtrade.domain.store.PositionStore`.
- Add field `private final PositionStore positionStore;` and constructor param, updating the
  constructor at lines 55-63:

```java
public SignalPipeline(CandleStore candleStore,
                      PriceActionSignalEngine priceActionEngine,
                      SignalPersistenceService persistenceService,
                      SentimentGate sentimentGate,
                      PositionStore positionStore) {
    this.candleStore = candleStore;
    this.priceActionEngine = priceActionEngine;
    this.persistenceService = persistenceService;
    this.sentimentGate = sentimentGate;
    this.positionStore = positionStore;
}
```

- In `generatePrimarySignal(String symbol)`, immediately after `result =
  priceActionEngine.analyze(symbol, chronologicalCandles);` (around line 94-98, after the
  `IllegalStateException` catch), insert:

```java
if (result.type() == Signal.SignalType.SELL) {
    boolean held = positionStore.findBySymbol(symbol).isPresent();
    if (held) {
        logger.info("""
            SELL signal for {} on {} — held position detected; broker close-out \
            wired in a later phase""", symbol, latestDate);
    } else {
        logger.debug("""
            SELL signal for {} on {} — no held position; persisting informational \
            SELL signal only""", symbol, latestDate);
    }
}
```

(No change to the sentiment-gate block or the final `persistenceService.buildAndSaveWithWarning(...)`
call — both already run unconditionally for non-BUY types today, so SELL is persisted for
free with no sentiment gating, matching decision #2.)

**Update existing test:** in `SignalPipelineTest.setUp()`, add
`@Mock private PositionStore positionStore;` and change the constructor call to
`pipeline = new SignalPipeline(candleStore, priceActionEngine, persistenceService, sentimentGate, positionStore);`.
The existing `persistsDetailedReasoningDistinctFromIndicators` test uses a HOLD result, so
`positionStore` is never stubbed/verified there — no `UnnecessaryStubbingException` risk.

---

### Phase 3 — Broker close-out for held positions (integration-level)

**What to test (unit, fast feedback)** — same file, new tests in `SellSignalPositionLookup`
(or a new `@Nested SellSignalBrokerCloseOut` class):

1. `sellSignal_symbolHeld_closesPositionViaPositionService()`
   - `positionStore.findBySymbol(SYMBOL)` -> `Optional.of(<Position fixture>)`.
   - `positionService.closePosition(SYMBOL, "SIGNAL_EXIT")` -> stub to return a non-null
     `PositionResponse`.
   - `verify(positionService).closePosition(SYMBOL, "SIGNAL_EXIT")`.

2. `sellSignal_symbolNotHeld_neverCallsPositionServiceClose()`
   - `positionStore.findBySymbol(SYMBOL)` -> `Optional.empty()`.
   - `verifyNoInteractions(positionService)`.

3. `sellSignal_closeThrows_stillPersistsSignal_doesNotPropagate()`
   - `positionStore.findBySymbol(SYMBOL)` -> `Optional.of(<Position fixture>)`.
   - `positionService.closePosition(...)` -> `thenThrow(new RuntimeException("db down"))`.
   - Assert `pipeline.generatePrimarySignal(SYMBOL)` does not throw
     (`assertThatCode(...).doesNotThrowAnyException()`), and
     `verify(persistenceService).buildAndSaveWithWarning(..., eq(SignalType.SELL), ...)` still
     fires.

**Why these fail now:** `SignalPipeline` has no `PositionService` dependency yet (compile
error), and the Phase 2 code only logs — it never calls any close mechanism.

**Switch to fix** — `api/src/main/java/com/swingtrade/api/service/SignalPipeline.java`

- Add import `com.swingtrade.api.service.PositionService` (same package, so technically no
  import needed) and field `private final PositionService positionService;`, 6th constructor
  arg:

```java
public SignalPipeline(CandleStore candleStore,
                      PriceActionSignalEngine priceActionEngine,
                      SignalPersistenceService persistenceService,
                      SentimentGate sentimentGate,
                      PositionStore positionStore,
                      PositionService positionService) {
    // ...existing assignments...
    this.positionService = positionService;
}
```

- Replace the Phase 2 log-only `if (held) { ... }` branch with:

```java
if (held) {
    try {
        positionService.closePosition(symbol, "SIGNAL_EXIT");
        logger.info("SELL signal closed held position for {} on {} (reason=SIGNAL_EXIT)", symbol, latestDate);
    } catch (Exception e) {
        logger.warn("""
            SELL signal for {} on {} failed to close held position — signal still \
            persisted for audit; position remains open: {}""", symbol, latestDate, e.getMessage());
    }
} else {
    logger.debug("""
        SELL signal for {} on {} — no held position; persisting informational \
        SELL signal only""", symbol, latestDate);
}
```

Note: `positionStore.findBySymbol()` (Phase 2) and `positionService.closePosition()`
(Phase 3, which internally re-reads via its own `positionStore.findBySymbol()` lookup) do
overlap in one extra DB read per SELL on a held symbol. This is a deliberate, small
inefficiency traded for keeping Phase 2 and Phase 3 independently testable/reviewable; not
worth optimizing away in this change.

**What to test (integration, real DB, no mocks)** — new file:
`api/src/integrationTest/java/com/swingtrade/api/service/SignalPipelineSellExitIntegrationTest.java`

Follow the `@SpringBootTest` + TestContainers Postgres pattern from
`api/src/test/java/com/swingtrade/api/DataPipelineE2ETest.java` /
`api/src/test/java/com/swingtrade/api/test/integration/ApiIntegrationTest.java` (do **not**
copy the `@Disabled`/H2 pattern from `DailySchedulerIntegrationTest.java` — it is disabled
for unrelated reasons and considerably more fragile). Autowire real
`SignalPipeline`, `PositionService`, `PositionRepository`, `CandleStore`, `StockStore` (no
`@MockBean`s for anything in the position-close path).

`sellSignal_forHeldPosition_actuallyClosesPositionInDb()`:

1. Seed 60+ chronological `OhlcvCandle`s for symbol `"TESTCO"` via `candleStore.save(...)`
   that deterministically satisfy exit condition A or C for the latest candle (reuse the
   Phase 1 candle-construction approach — e.g. a long uptrend followed by a >3% drop on the
   final candle) so `priceActionEngine.analyze()` returns SELL for the seeded data.
2. Open a real position for `"TESTCO"` via `positionService.createPosition(new
   TradeRequest("TESTCO", 10, TradeDirection.LONG, OrderType.MARKET) { price = <entry
   price> })` (mirrors how positions are actually opened in production — this creates both
   the in-memory engine position and the persisted `PositionEntity`, linked via
   `positionId`, which is required for `PaperTradingEngine.closePosition(Long, ...)` to
   resolve).
3. Capture the returned `PositionResponse.getId()` (DB position id).
4. Call `signalPipeline.generatePrimarySignal("TESTCO")`.
5. Assert `positionRepository.findById(positionId).orElseThrow().getStatus()` equals
   `"CLOSED"` — the real DB row, not a mock verification.
6. Assert a `Signal` row exists for `"TESTCO"` with `signalType == "SELL"` (via
   `SignalStore`/`SignalRepository`, whichever is autowired elsewhere in similar tests).

**Why this fails now:** before Phase 3's production change, no code path ever calls
`PositionService.closePosition(...)` from the signal-generation flow, so the seeded position
remains `"OPEN"` after `generatePrimarySignal()` runs.

---

### Phase 4 — `BacktestEngine` support for signal-driven exits (opt-in, backward compatible)

**Design:** Add `ExitReason.SIGNAL_EXIT` and a `BacktestConfig.signalExitEnabled` boolean
flag. **Default `false`** in `BacktestConfig.defaults()` — this is a deliberate safety choice:
production code (`BacktestController`, `BacktestScorer`, `JobOrchestratorService.stageBacktest`)
exclusively calls `BacktestConfig.defaults()`, so today's backtest behavior is completely
unchanged by default. The flag exists specifically so a future comparison run (decision #6 —
"current approach vs. new approach") can flip it on. When enabled, the SIGNAL_EXIT check
reuses `PriceActionSignalEngine.RSI_LOWER_BOUND` directly (same "never drift from the live
engine" pattern `tryEnter()` already uses for entry), and is checked in priority order
**after** `STOP_LOSS`/`TARGET_HIT` (the independent floor, decision #4) and **before**
`TREND_BREAK`/`TIME_STOP`.

**What to test** — `strategy/src/test/java/com/swingtrade/strategy/BacktestEngineTest.java`

New `@Nested @DisplayName("SignalDrivenExit") class SignalDrivenExit`:

1. `signalExit_closeBelowEma20_exitsNextDayAtClose()`
   - `buildEntrySetupCandles()` + one candle the day after entry whose close drops ~3% below
     the prior close (same shape as Phase 1's condition-A candle).
   - `BacktestConfig config = new BacktestConfig(0.0, 0.0, 0.01, 500_000.0, 5, 100.0, 100.0, 20, true);`
     (huge `atrMultiplierStop`/`rewardRiskRatio` so STOP_LOSS/TARGET_HIT can never preempt —
     same technique as the existing `trendBreakExit` test).
   - Assert `trade.exitReason() == ExitReason.SIGNAL_EXIT`, `trade.holdingDays() == 1`.

2. `signalExit_rsiDropsBelowFifty_exitsAtClose()`
   - Post-entry candles with a mild negative drift that eases RSI below 50 while trend
     structure stays intact (verify when running; tune drift magnitude if it also trips
     condition A first — either is an acceptable SIGNAL_EXIT trigger for this test as long as
     `reason == SIGNAL_EXIT`).
   - Same wide-stop/target config as above; assert `ExitReason.SIGNAL_EXIT`.

3. `signalExit_takesPriorityOverTrendBreak_whenEnabled()`
   - Reuse the exact candle construction from the existing `trendBreakExit` test.
   - `signalExitEnabled = true` this time.
   - Assert `trade.exitReason() == ExitReason.SIGNAL_EXIT` and `trade.holdingDays() == 1`
     (not `TREND_BREAK`/2) — documents that the looser signal-driven rule preempts the old
     2-day streak rule once enabled.

4. `signalExit_disabledByDefault_doesNotChangeExistingBehavior()`
   - `BacktestConfig.defaults()` on the Phase 4-test-1 candle sequence.
   - Assert `trade.exitReason() != ExitReason.SIGNAL_EXIT` (falls through to whatever the
     old chain produces — TIME_STOP or TREND_BREAK depending on candle count).

**Why these fail now:** `ExitReason` has no `SIGNAL_EXIT` value, `BacktestConfig` has no
`signalExitEnabled` field (compile error on the 9-arg constructor call), and `simulate()`
never evaluates the trend/RSI exit conditions.

**Update all 12 existing `new BacktestConfig(...)` call sites** in `BacktestEngineTest.java`
(lines 86, 103, 121, 138, 160, 179, 193, 215, 233, 253, 309, 325) — append a trailing
`, false` to each (mechanical, zero behavior change since default-off is the whole point).

**Switch to fix:**

`strategy/src/main/java/com/swingtrade/strategy/ExitReason.java` — add `SIGNAL_EXIT` and
update the javadoc priority-order list:

```java
package com.swingtrade.strategy;

/**
 * Reason a backtest position was closed. Checked in this priority order each bar:
 * {@link #STOP_LOSS} then {@link #TARGET_HIT} then {@link #SIGNAL_EXIT} (if
 * {@link BacktestConfig#signalExitEnabled()}) then {@link #TREND_BREAK} then {@link #TIME_STOP}.
 */
public enum ExitReason {
    STOP_LOSS,
    TARGET_HIT,
    SIGNAL_EXIT,
    TIME_STOP,
    TREND_BREAK
}
```

`strategy/src/main/java/com/swingtrade/strategy/BacktestConfig.java` — add the field and
default:

```java
public record BacktestConfig(
    double slippagePct,
    double brokeragePerTrade,
    double riskPerTradePct,
    double initialCapital,
    int maxConcurrentPositions,
    double atrMultiplierStop,
    double rewardRiskRatio,
    int maxHoldingDays,
    boolean signalExitEnabled
) {

    public static BacktestConfig defaults() {
        return new BacktestConfig(
                0.001, 20.0, 0.01,
                500_000.0, 5, 2.0,
                2.5, 20, false
        );
    }
}
```

`strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java` — in `simulate()`
(lines 206-228), fetch `ema50`/`rsi` at index `i` and add the new branch:

```java
if (open != null) {
    BigDecimal low = numToBigDecimal(lowPrice.getValue(i));
    BigDecimal high = numToBigDecimal(highPrice.getValue(i));
    BigDecimal close = numToBigDecimal(closePrice.getValue(i));
    BigDecimal ema20Val = numToBigDecimal(ema20.getValue(i));
    BigDecimal ema50Val = numToBigDecimal(ema50.getValue(i));
    BigDecimal rsiVal = numToBigDecimal(rsi.getValue(i));

    int streak = close.compareTo(ema20Val) < 0 ? open.belowEma20Streak + 1 : 0;
    BigDecimal exitPrice = null;
    ExitReason reason = null;

    boolean signalExitTriggered = close.compareTo(ema20Val) < 0
        || ema20Val.compareTo(ema50Val) < 0
        || rsiVal.compareTo(PriceActionSignalEngine.RSI_LOWER_BOUND) < 0;

    if (low.compareTo(open.stopLoss()) <= 0) {
        reason = ExitReason.STOP_LOSS;
        exitPrice = open.stopLoss();
    } else if (high.compareTo(open.target()) >= 0) {
        reason = ExitReason.TARGET_HIT;
        exitPrice = open.target();
    } else if (config.signalExitEnabled() && signalExitTriggered) {
        reason = ExitReason.SIGNAL_EXIT;
        exitPrice = close;
    } else if (streak >= 2) {
        reason = ExitReason.TREND_BREAK;
        exitPrice = close;
    } else if ((i - open.entryIndex()) >= config.maxHoldingDays()) {
        reason = ExitReason.TIME_STOP;
        exitPrice = close;
    }
    // ...rest of the block (closeTrade / re-wrap OpenPosition) unchanged...
}
```

**Docs (non-TDD, update alongside):** `docs/backtesting.md` — add a `SIGNAL_EXIT` line to
the "Exit rules" list (between `TARGET_HIT` and `TREND_BREAK`), noting it's opt-in via
`BacktestConfig.signalExitEnabled` (default `false`) and lists the same 3 conditions as
Phase 1's `PriceActionSignalEngine` exit rule, with a cross-reference so the two rule sets
are never described inconsistently.

---

### Phase 5 — Delete `SwingTradingStrategy` (dead code)

**Verified before deletion (this session):**
- `grep -rln "SwingTradingStrategy"` across the whole backend returns only
  `strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java` itself — no
  other class references it (already `@Deprecated`, already disconnected, confirmed by its
  own javadoc: "no longer wired into the production signal pipeline").
- No `SwingTradingStrategyTest.java` exists anywhere in the repo — nothing to delete there.
- `TechnicalIndicators` (its only dependency) **has other live consumers** —
  `api/src/main/java/com/swingtrade/api/service/TechnicalAnalysisService.java` and
  `api/src/main/java/com/swingtrade/api/service/FundamentalScorer.java` both inject it — so
  per decision #7, `TechnicalIndicators.java`,
  `strategy/src/test/java/com/swingtrade/strategy/TechnicalIndicatorsTest.java`,
  `TechnicalIndicatorsDouble.java`, and `AdvancedTechnicalIndicators.java` are all **kept
  unchanged**.

**Action (no test to write — this is pure deletion of confirmed-dead code, not a behavior
change under test):**

1. Delete `strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java`.
2. Run `./gradlew :strategy:compileJava :api:compileJava` to confirm nothing else references
   it (should already be guaranteed by the grep above, but compilation is the real proof).
3. Run `./gradlew :strategy:test :api:test` to confirm no test suite references it either.

## 3. Files Summary

| Action | File | Type |
|---|---|---|
| Modify | `strategy/src/main/java/com/swingtrade/strategy/PriceActionSignalEngine.java` | Production |
| Modify | `strategy/src/test/java/com/swingtrade/strategy/PriceActionSignalEngineTest.java` | Test |
| Modify | `api/src/main/java/com/swingtrade/api/service/SignalPipeline.java` | Production |
| Modify | `api/src/test/java/com/swingtrade/api/service/SignalPipelineTest.java` | Test |
| Add | `api/src/integrationTest/java/com/swingtrade/api/service/SignalPipelineSellExitIntegrationTest.java` | Test |
| Modify | `strategy/src/main/java/com/swingtrade/strategy/ExitReason.java` | Production |
| Modify | `strategy/src/main/java/com/swingtrade/strategy/BacktestConfig.java` | Production |
| Modify | `strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java` | Production |
| Modify | `strategy/src/test/java/com/swingtrade/strategy/BacktestEngineTest.java` | Test |
| Modify | `docs/backtesting.md` | Docs |
| Delete | `strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java` | Production |
| No change | `strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java` | Production (has other consumers — verified) |
| No change | `core/src/main/java/com/swingtrade/domain/Signal.java` | Domain (already supports `SELL`) |
| No change | `data/src/main/java/com/swingtrade/data/entity/SignalEntity.java` | Entity (`signal_type` is a plain varchar — no DB constraint to update) |

## 4. Verification

RED/GREEN per phase:

```bash
cd backend

# Phase 1
./gradlew :strategy:test --tests "PriceActionSignalEngineTest"

# Phase 2 + 3 (unit)
./gradlew :api:test --tests "SignalPipelineTest"

# Phase 3 (integration — requires Docker for TestContainers Postgres)
./gradlew :api:integrationTest --tests "SignalPipelineSellExitIntegrationTest"

# Phase 4
./gradlew :strategy:test --tests "BacktestEngineTest"

# Phase 5 — after deletion, confirm the modules still compile and all tests still pass
./gradlew :strategy:compileJava :api:compileJava
./gradlew :strategy:test :api:test

# Full suite + coverage + module boundaries at the end
./gradlew test
./gradlew :api:test --tests "com.swingtrade.api.arch.ModuleBoundaryTest"
./gradlew jacocoTestCoverageVerification
```

Manual/live verification (after all phases land):

1. Start the stack (`./dev-stack.sh start`) and trigger a job run
   (`POST /api/job/runs` or the OrchestratorView UI) against a watchlist that includes a
   symbol you already hold a paper position in.
2. Confirm via `GET /api/signals/{symbol}` that a `SELL` signal type is now possible (not
   just BUY/HOLD).
3. Confirm via `GET /api/positions/{symbol}` that a held position whose latest candle trips
   the exit confluence flips to `CLOSED` after the run.
4. Confirm via `GET /api/positions/{symbol}` for a **non-held** symbol that a SELL signal was
   generated (`GET /api/signals/{symbol}`) but no position was created.
5. `POST /api/backtest/run?symbol=<symbol>&exchange=NSE` still returns identical results to
   before this change (default config, `signalExitEnabled=false`).
