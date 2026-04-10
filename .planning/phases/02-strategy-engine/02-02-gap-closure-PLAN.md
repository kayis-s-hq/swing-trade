---
phase: 02-strategy-engine
plan: 02
type: execute
wave: 1
depends_on: []
files_modified:
  - strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java
  - strategy/src/main/java/com/swingtrade/strategy/BacktestResult.java
autonomous: true
requirements:
  - REQ-010
user_setup: []
gap_closure: true

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
      path: ".planning/phases/02-strategy-engine/"
      note: "Do NOT edit .planning/ in root repository"
    - step: 4
      action: "Sync to root before merge"
      command: "Skill(\"superpowers:gsd-worktree-workflow --sync-to-root\")"
      when: "Before merging worktree branch to main"
    - step: 5
      action: "Verify before merge"
      command: "gsd:verify"
      when: "After plan completion, before merge"
gap_closure: true
must_haves:
  truths:
    - "BacktestEngine calculates Sharpe ratio with annualized risk-free rate"
    - "BacktestEngine calculates Max drawdown as percentage from equity curve"
    - "BacktestEngine calculates Average trade duration in bars"
    - "BacktestEngine enforces 20% capital per position rule"
    - "BacktestEngine enforces max 5 concurrent positions"
  artifacts:
    - path: "strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java"
      provides: "Implemented performance metrics"
      contains: "calculateSharpeRatio, calculateMaxDrawdown, calculateAvgTradeDuration"
    - path: "strategy/src/main/java/com/swingtrade/strategy/BacktestResult.java"
      provides: "Position sizing fields"
      contains: "capitalPerPosition, maxConcurrentPositions"
  key_links:
    - from: "strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java"
      to: "tradingRecord.getTrades()"
      via: "extract returns for Sharpe ratio"
      pattern: "getTrades\(\)\.(get|stream)"
    - from: "strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java"
      to: "equity curve calculation"
      via: "track peak equity, calculate trough drawdown"
      pattern: "peakEquity.*drawdown"
    - from: "strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java"
      to: "trade entry/exit bars"
      via: "duration = exitBarIndex - entryBarIndex"
      pattern: "barIndex\(\)|getIndex\(\)"
---

<objective>
Implement missing performance metrics (Sharpe ratio, Max drawdown, Avg trade duration) and position sizing rules in BacktestEngine.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/phases/02-strategy-engine/02-UAT.md

# Gap Source: 02-UAT.md Test 4
- BacktestEngine returns 0.0 for Sharpe ratio, Max drawdown, Avg trade duration
- These are placeholder stubs with comments indicating "requires more work"
- Position sizing rules (20% capital, max 5 positions) not implemented

# Existing Implementation
- Total P&L and Win rate are already correctly implemented
- Uses TA4J 0.16 BacktestExecutor with BaseTradingRecord
- TradingRecord contains trades with entry/exit information
- RISK_FREE_RATE constant already exists (6% annual)

# Implementation Details Needed
- Sharpe ratio: (avg_return - risk_free_rate) / std_dev(returns), annualized
- Max drawdown: track equity curve, find peak-trough differences as percentage
- Avg trade duration: sum(entry-exit bar differences) / total trades
- Position sizing: check before opening new position (20% capital, max 5)
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement Sharpe ratio calculation</name>
  <files>strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java</files>
  <action>
    Implement calculateSharpeRatio() with proper return calculation:

    Algorithm:
    1. Get all trades from tradingRecord (tradingRecord.getTrades())
    2. For each trade, calculate return: (exit_value - entry_value) / entry_value
    3. Calculate average return across all trades
    4. Calculate standard deviation of returns
    5. Sharpe ratio = (avg_return - risk_free_rate) / std_dev
    6. Annualize: multiply by sqrt(252) for daily data

    Implementation steps:
    1. Get List&lt;Trade&gt; from tradingRecord.getTrades()
    2. Calculate individual trade returns using getPositionEntry()/getPositionExit() or trade.getIndex()
    3. Use java.util.stream to calculate mean and standard deviation
    4. Apply formula: (avgReturn - RISK_FREE_RATE) / stdDev * Math.sqrt(252)
    5. Handle edge cases:
       - No trades: return 0.0
       - Single trade: return 0.0 (can't calculate std dev)
       - Zero std dev: return 0.0 (no volatility, but also no variability)

    Code reference:
    - TradingTrade interface: trade.getIndex(), trade.getEntryBar(), trade.getExitBar()
    - Use double[] array for returns, then Arrays.stream(arr).average(), Arrays.stream(arr).map(x->x-mean).reduce...

    Note: Position sizing is handled separately in Task 4.
  </action>
  <verify>
    <automated>grep -A 20 "private double calculateSharpeRatio" strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java | head -25</automated>
    <manual>Verify Sharpe ratio uses RISK_FREE_RATE, calculates from trade returns, annualizes with sqrt(252)</manual>
  </verify>
  <done>
    - calculateSharpeRatio() calculates actual Sharpe ratio from trade returns
    - Uses RISK_FREE_RATE constant (6% annual)
    - Annualizes result by multiplying by sqrt(252)
    - Returns meaningful non-zero values when trades exist
  </done>
</task>

<task type="auto">
  <name>Task 2: Implement Max drawdown calculation</name>
  <files>strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java</files>
  <action>
    Implement calculateMaxDrawdown() to track equity curve:

    Algorithm:
    1. Start with initial equity = 100000 (or calculate from series data)
    2. Track peak equity value as we iterate through trades
    3. For each trade exit, calculate equity and compare to peak
    4. Drawdown = (peak - current_equity) / peak * 100
    5. Track maximum drawdown across all points

    Implementation steps:
    1. Get tradingRecord.getTrades() list
    2. Sort trades by bar index (entry order)
    3. Track current_equity starting from initial capital
    4. Track peak_equity = initial capital
    5. For each trade:
       - Calculate trade P&L
       - Update current_equity
       - If current_equity > peak_equity: peak_equity = current_equity
       - Calculate drawdown = (peak_equity - current_equity) / peak_equity * 100
       - Update max_drawdown if current drawdown > max
    6. Return max_drawdown as percentage

    Code reference:
    - Trade class: trade.getEntryBar(), trade.getExitBar(), getProfitLoss()
    - Iterate through trades in chronological order
    - Track max_drawdown initialized to 0.0

    Edge cases:
    - No trades: return 0.0
    - All profitable trades: return 0.0 (no drawdown from peak)
    - First trade is loss: calculate drawdown from initial peak
  </action>
  <verify>
    <automated>grep -A 25 "private double calculateMaxDrawdown" strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java | head -30</automated>
    <manual>Verify Max drawdown tracks peak equity, calculates trough differences as percentage</manual>
  </verify>
  <done>
    - calculateMaxDrawdown() calculates actual drawdown from equity curve
    - Tracks peak equity and current equity through trades
    - Returns meaningful non-zero values when drawdowns occur
    - Returns 0.0 when no drawdowns (all profitable trades)
  </done>
</task>

<task type="auto">
  <name>Task 3: Implement Average trade duration calculation</name>
  <files>strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java</files>
  <action>
    Implement calculateAvgTradeDuration() using bar indices:

    Algorithm:
    1. Get all completed trades from tradingRecord
    2. For each trade, calculate duration = exit_bar_index - entry_bar_index
    3. Sum all durations and divide by total trade count
    4. Return average duration in bars (trading days)

    Implementation steps:
    1. Get List&lt;Trade&gt; from tradingRecord.getTrades()
    2. Filter for completed trades (trade.isClosed() or getExitBar() != null)
    3. For each closed trade:
       - entryIndex = trade.getEntryBar().getIndex()
       - exitIndex = trade.getExitBar().getIndex()
       - duration = exitIndex - entryIndex
    4. Sum all durations and divide by count
    5. Return average

    Code reference:
    - Trade interface: getEntryBar(), getExitBar()
    - Bar interface: getIndex()
    - Use stream().filter(Trade::isClosed).mapToInt().average()

    Edge cases:
    - No trades or no closed trades: return 0.0
    - All trades still open: return 0.0 (can't calculate duration for open trades)
  </action>
  <verify>
    <automated>grep -A 15 "private double calculateAvgTradeDuration" strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java | head -20</automated>
    <manual>Verify Avg trade duration calculates exitBarIndex - entryBarIndex, averages across closed trades</manual>
  </verify>
  <done>
    - calculateAvgTradeDuration() calculates actual average duration in bars
    - Uses trade.getEntryBar().getIndex() and trade.getExitBar().getIndex()
    - Only counts closed/completed trades
    - Returns meaningful non-zero values when trades exist
  </done>
</task>

<task type="auto">
  <name>Task 4: Implement position sizing rules</name>
  <files>strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java</files>
  <action>
    Add position sizing enforcement to backtest engine:

    Rules:
    1. Capital per position: 20% of total capital
    2. Max concurrent positions: 5

    Implementation:
    1. Add class-level constants:
       - PRIVATE static final double CAPITAL_PER_POSITION_PCT = 0.20;
       - PRIVATE static final int MAX_CONCURRENT_POSITIONS = 5;

    2. In runBacktestWithExecution(), add position tracking before creating TradingRecord:
       - Track current position count
       - Track cumulative capital allocated
       - Before each new position entry:
         * If current_positions >= MAX: skip this entry (don't open position)
         * If allocated_capital >= 80% of total: skip this entry

    3. For TA4J integration:
       - TA4J's Rule interface controls entry signals
       - Create custom Rule that checks position constraints
       - Or filter signals before passing to BacktestExecutor

    4. Alternative simpler approach:
       - Create wrapper Strategy that filters signals based on position limits
       - Pass filtered strategy to BacktestExecutor

    Recommended approach:
    Since BacktestExecutor uses TA4J's internal state management, the cleanest approach is to:
    a) Create a position-constrained version of the strategy before backtesting
    b) Or post-process trades to remove positions that violate constraints

    For simplicity, implement in post-processing:
    1. Run backtest normally (get all trades)
    2. Filter out trades that would exceed position limits
    3. Recalculate metrics from filtered trades

    Note: This is a complex integration with TA4J. Consider documenting this as a known limitation if not easily implementable.
  </action>
  <verify>
    <automated>grep -n "CAPITAL_PER_POSITION\|MAX_CONCURRENT_POSITIONS" strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java</automated>
    <manual>Verify position sizing constants exist and are used to filter positions</manual>
  </verify>
  <done>
    - CAPITAL_PER_POSITION_PCT = 0.20 (20%) constant defined
    - MAX_CONCURRENT_POSITIONS = 5 constant defined
    - Position sizing logic filters backtest results or enforces limits during execution
    - If implementation complexity too high, document as known limitation and provide TODO
  </done>
</task>

</tasks>

<verification>
Build and verify backtest engine:
<automated>cd strategy && mvn clean install -DskipTests</automated>

Run existing backtest tests (if any):
<automated>cd strategy && mvn test -Dtest=DefaultBacktestEngineTest 2>/dev/null | tail -20</automated>

Manual verification:
<automated>
cat strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java | grep -A 3 "calculateSharpeRatio\|calculateMaxDrawdown\|calculateAvgTradeDuration"
</automated>
</verification>

<success_criteria>
- [ ] calculateSharpeRatio() returns non-zero Sharpe ratio for trades with returns
- [ ] calculateSharpeRatio() uses RISK_FREE_RATE and annualizes with sqrt(252)
- [ ] calculateMaxDrawdown() calculates actual drawdown percentage from equity curve
- [ ] calculateAvgTradeDuration() calculates average bars between entry and exit
- [ ] Position sizing constants (20% capital, max 5 positions) defined
- [ ] Position sizing enforced in backtest execution or post-processing
- [ ] mvn clean install succeeds
- [ ] All existing tests still pass
</success_criteria>

<output>
After completion, update 02-UAT.md Test 4 status to "pass" or "partial" based on implementation completeness.
</output>
