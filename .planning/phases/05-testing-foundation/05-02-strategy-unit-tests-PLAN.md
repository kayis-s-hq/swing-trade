---
phase: 05-testing-foundation
plan: 02
type: execute
wave: 2
depends_on:
  - 05-01
files_modified:
  - strategy/src/test/java/com/swingtrade/strategy/TechnicalIndicatorsTest.java
  - strategy/src/test/java/com/swingtrade/strategy/impl/DefaultStrategyTest.java
  - strategy/src/test/java/com/swingtrade/strategy/impl/DefaultBacktestEngineTest.java
autonomous: true
requirements:
  - REQ-102
user_setup:
  - service: "Test Setup"
    why: "Running strategy unit tests"
    env_vars: []
    dashboard_config: []

must_haves:
  truths:
    - TechnicalIndicators RSI calculation matches expected values for known inputs
    - TechnicalIndicators EMA/SMA calculations are accurate
    - TechnicalIndicators ATR calculation handles candle OHLCV data
    - DefaultStrategy entry rules implement 4-factor logic correctly
    - DefaultStrategy exit rules implement stop/target/time logic
    - BacktestEngine calculates all performance metrics (Sharpe, Drawdown, WinRate)
  artifacts:
    - path: "strategy/src/test/java/com/swingtrade/strategy/TechnicalIndicatorsTest.java"
      provides: "Technical indicators unit tests"
      min_lines: 150
    - path: "strategy/src/test/java/com/swingtrade/strategy/impl/DefaultStrategyTest.java"
      provides: "DefaultStrategy implementation tests"
      min_lines: 120
    - path: "strategy/src/test/java/com/swingtrade/strategy/impl/DefaultBacktestEngineTest.java"
      provides: "BacktestEngine unit tests"
      min_lines: 130
  key_links:
    - from: "strategy/src/test/java/com/swingtrade/strategy/TechnicalIndicatorsTest.java"
      to: "strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java"
      via: "calculateRSI, calculateEMA, calculateATR, calculateMACD"
      pattern: "calculate.*RSI|EMA|ATR"
    - from: "strategy/src/test/java/com/swingtrade/strategy/impl/DefaultStrategyTest.java"
      to: "strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java"
      via: "generateStrategy, shouldExecuteTrade"
      pattern: "CrossedUpIndicatorRule|UnderIndicatorRule|OverIndicatorRule"
---

<objective>
Create comprehensive unit tests for strategy module (TechnicalIndicators, DefaultStrategy, BacktestEngine) with 85%+ code coverage.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS.md (REQ-102)
@.planning/ROADMAP.md (Phase 5 goal)
@05-01-core-domain-unit-tests-SUMMARY.md (domain model test patterns)

# Strategy Interfaces
<!-- Key types and contracts the executor needs -->

From strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java:
```java
public class TechnicalIndicators {
    public Double calculateRSI(List<BigDecimal> closePrices, int period);
    public Double calculateRSIDouble(List<Double> closePrices, int period);
    public Double calculateEMA(List<BigDecimal> closePrices, int period);
    public Double calculateEMADouble(List<Double> closePrices, int period);
    public Double calculateSMA(List<BigDecimal> closePrices, int period);
    public Double calculateATR(List<CandleWithPrices> candles, int period);
    public Double calculateMACD(List<BigDecimal> closePrices, int fast, int slow, int signal);
    public StochasticValues calculateStochastic(List<CandleWithPrices> candles, int k, int d);
    public BollingerBands calculateBollingerBands(List<BigDecimal> closePrices, int period, double mult);
    public Double calculateVWAP(List<CandleWithPrices> candles);
    public Double calculateADX(List<CandleWithPrices> candles, int period);
}
```

From strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java:
```java
public class DefaultStrategy implements TradingStrategy {
    public org.ta4j.core.Strategy generateStrategy(BarSeries barSeries);
    public boolean shouldExecuteTrade(BarSeries barSeries, int index, TradingRecord tradingRecord);
}
```

From strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java:
```java
public interface BacktestEngine {
    BacktestResult runBacktest(Strategy strategy, BarSeries barSeries);
    BacktestResult runBacktestWithExecution(Strategy strategy, BarSeries barSeries, double commissionRate);
}
```

BacktestResult fields:
- totalPnL (double)
- tradeCount (int)
- winRate (double)
- sharpeRatio (double)
- maxDrawdown (double)
- avgTradeDuration (double)
- profitFactor (double)

# TA4J Test Patterns
<!-- How to mock TA4J components -->

Use org.ta4j.core.BaseBarSeries for test data:
```java
BarSeries series = new BaseBarSeries("test");
series.addBar(new BaseBar(Duration.ofDays(1), date, open, high, low, close, volume));
```

Create known input scenarios:
```java
// RSI known value: flat prices = RSI undefined (return null)
// Uptrend with RSI > 50: prices [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
List<Double> prices = List.of(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0);
Double rsi = indicators.calculateRSI(prices, 14);
assertThat(rsi).isNotNull();
assertThat(rsi).isGreaterThan(50);
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create TechnicalIndicatorsTest with known input/output tests</name>
  <files>strategy/src/test/java/com/swingtrade/strategy/TechnicalIndicatorsTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - calculateRSI returns correct value for known price series
    - calculateEMA/SMA match expected exponential/simple moving averages
    - calculateATR correctly computes Average True Range from OHLCV candles
    - calculateMACD returns positive value in uptrend, negative in downtrend
    - Edge cases: null/empty inputs return null
    - Period validation throws IllegalArgumentException for invalid periods
  </behavior>
  <action>
Create TechnicalIndicatorsTest.java with:
- @Test methods for each indicator method:
  - calculateRSI with: flat prices (null), uptrend (50-70), downtrend (30-50)
  - calculateEMA with known period values (compare to manual calc)
  - calculateSMA with simple price series
  - calculateATR with: high volatility candles, low volatility candles
  - calculateMACD: fast/slow periods, signal line crossover
  - calculateStochastic: K/D values from known high/low/close
  - calculateBollingerBands: upper/middle/lower band values
  - calculateVWAP with volume-weighted prices
  - calculateADX: trend strength values

Edge case tests:
- Null/empty lists return null
- Insufficient data (less than period) returns null
- Invalid period throws IllegalArgumentException

Use JUnit 5 + AssertJ with double comparisons (using epsilon for floats).
  </action>
  <verify>
    <automated>mvn test -pl strategy -Dtest=TechnicalIndicatorsTest</automated>
  </verify>
  <done>TechnicalIndicatorsTest.java exists with 25+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 2: Create DefaultStrategyTest with entry/exit rule validation</name>
  <files>strategy/src/test/java/com/swingtrade/strategy/impl/DefaultStrategyTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - generateStrategy() creates Ta4j strategy with correct indicators
    - Entry rule: EMA crossover + RSI conditions + Volume spike
    - Exit rule: EMA cross down OR RSI overbought OR Stop loss OR Take profit
    - shouldExecuteTrade() returns correct signal based on current price
  </behavior>
  <action>
Create DefaultStrategyTest.java with:
- @Test methods for:
  - generateStrategy() with null BarSeries throws IllegalArgumentException
  - Strategy creation for uptrend (EMA fast > slow, RSI 50-65)
  - Strategy creation for downtrend
  - Entry rule evaluation at specific index
  - Exit rule: stop loss trigger
  - Exit rule: target hit trigger
  - Exit rule: RSI overbought (70+)
  - shouldExecuteTrade() with valid/invalid inputs

Use ta4j BaseBarSeries, BaseStrategy, TradingRecord mocks.
Verify Ta4j Rule objects are correctly configured.
  </action>
  <verify>
    <automated>mvn test -pl strategy -Dtest=DefaultStrategyTest</automated>
  </verify>
  <done>DefaultStrategyTest.java exists with 18+ tests, all passing</done>
</task>

<task type="auto">
  <name>Task 3: Create DefaultBacktestEngineTest with performance metrics</name>
  <files>strategy/src/test/java/com/swingtrade/strategy/impl/DefaultBacktestEngineTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - runBacktest() delegates to runBacktestWithExecution with default commission
    - runBacktestWithExecution() returns accurate trade count
    - Win rate calculated correctly (profit count / total trades)
    - Sharpe ratio calculated from trade returns
    - Max drawdown tracks peak-to-trough equity decline
    - Profit factor = total profits / total losses
  </behavior>
  <action>
Create DefaultBacktestEngineTest.java with:
- @Test methods for:
  - runBacktest() with null strategy/barSeries throws exception
  - runBacktest() delegates to runBacktestWithExecution
  - runBacktestWithExecution() with empty series returns zero metrics
  - runBacktestWithExecution() with 3 profitable trades:
    - tradeCount = 3
    - winRate = 100%
    - profitFactor > 1
  - runBacktestWithExecution() with mixed P&L:
    - winRate calculated correctly
    - profitFactor < 1
  - runBacktestWithExecution() with no trades:
    - all metrics = 0
  - Commission rate affects totalPnL
  - Sharpe ratio > 0 for positive returns
  - Max drawdown tracks peak equity decline

Use ta4j BaseBarSeries, BaseStrategy, BaseTradingRecord.
Verify BacktestResult fields match expected values.
  </action>
  <verify>
    <automated>mvn test -pl strategy -Dtest=DefaultBacktestEngineTest</automated>
  </verify>
  <done>DefaultBacktestEngineTest.java exists with 15+ tests, all passing</done>
</task>

</tasks>

<verification>
Overall checks:
1. Run `mvn test -pl strategy` to verify all 3 test classes pass
2. Check coverage: `mvn jacoco:report -pl strategy` shows 85%+ line coverage
3. Verify Ta4j dependencies are available in test scope
4. Ensure tests use known input/output for indicator validation
</verification>

<success_criteria>
- All 3 strategy test classes created
- 58+ total tests across all classes
- 85%+ code coverage on strategy module
- All tests pass with `mvn test -pl strategy`
- Technical indicators tested with known values
- Backtest metrics validated against expected calculations
</success_criteria>

<output>
After completion, create `.planning/phases/05-testing-foundation/05-02-strategy-unit-tests-SUMMARY.md`
</output>
