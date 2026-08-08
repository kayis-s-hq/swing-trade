# feat(strategy): add strategy comparison and backtest report

**Labels:** `enhancement` `tier-3-infra` `strategy` `backtesting`
**Estimated effort:** 3-4 days

## Problem

The `BacktestEngine` exists but there is no REST endpoint to run backtests or compare strategies. Users cannot validate strategy changes before deploying them live.

## Proposed Solution

Add backtest endpoints that allow running backtests with configurable parameters and comparing results across strategies.

## API Endpoints

```
POST /api/backtest/run
    Run a backtest with given parameters

POST /api/backtest/compare
    Compare two strategies on the same data

GET  /api/backtest/history
    List past backtest runs

GET  /api/backtest/history/{runId}
    Get specific backtest result
```

## Request DTOs

```java
public class BacktestRequest {
    private String symbol;              // Stock symbol to backtest
    private LocalDate dateFrom;         // Start date
    private LocalDate dateTo;           // End date
    private BigDecimal initialCapital;  // Default: 100000
    private StrategyParams params;      // Strategy configuration
    private String strategyName;        // "default" or custom
}

public class StrategyParams {
    private int emaFastPeriod;          // Default: 12
    private int emaSlowPeriod;          // Default: 26
    private int rsiPeriod;              // Default: 14
    private double rsiOversold;         // Default: 30
    private double rsiOverbought;       // Default: 70
    private double atrMultiplierSl;     // Default: 2.0
    private double volumeMultiplier;    // Default: 1.5
    private double positionSizePct;     // Default: 10.0
}

public class BacktestCompareRequest {
    private String symbol;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal initialCapital;
    private String strategyA;           // Strategy A name + params
    private StrategyParams paramsA;
    private String strategyB;           // Strategy B name + params
    private StrategyParams paramsB;
}
```

## Response DTOs

```java
public class BacktestResult {
    private String runId;
    private String strategyName;
    private String symbol;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal initialCapital;

    // Performance
    private BigDecimal finalCapital;
    private BigDecimal totalReturn;
    private BigDecimal totalReturnPercent;
    private BigDecimal winRate;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal averageWin;
    private BigDecimal averageLoss;
    private BigDecimal profitFactor;
    private BigDecimal maxDrawdown;
    private BigDecimal maxDrawdownPercent;
    private BigDecimal sharpeRatio;
    private BigDecimal avgTradeDuration;

    // Trade list
    private List<BacktestTrade> trades;

    // Equity curve
    private List<EquityPoint> equityCurve;
}

public class BacktestTrade {
    private int tradeNumber;
    private String symbol;
    private LocalDateTime entryDate;
    private BigDecimal entryPrice;
    private BigDecimal quantity;
    private LocalDateTime exitDate;
    private BigDecimal exitPrice;
    private BigDecimal pnl;
    private BigDecimal pnlPercent;
    private String exitReason;        // STOP_LOSS, TARGET_HIT, EMA_CROSS, etc.
    private int durationBars;
}

public class BacktestComparison {
    private BacktestResult resultA;
    private BacktestResult resultB;
    private BacktestComparisonSummary winner;
}

public class BacktestComparisonSummary {
    private String winnerStrategy;
    private Map<String, BigDecimal> metricsDelta;  // A - B per metric
}
```

## Files to Create

- `api/src/main/java/com/swingtrade/api/controller/BacktestController.java`
- `api/src/main/java/com/swingtrade/api/dto/BacktestRequest.java`
- `api/src/main/java/com/swingtrade/api/dto/BacktestResult.java`
- `api/src/main/java/com/swingtrade/api/dto/BacktestTrade.java`
- `api/src/main/java/com/swingtrade/api/dto/BacktestCompareRequest.java`
- `api/src/main/java/com/swingtrade/api/dto/BacktestComparison.java`
- `api/src/main/java/com/swingtrade/api/dto/StrategyParams.java`
- `strategy/src/main/java/com/swingtrade/strategy/BacktestService.java` - Service layer
- `data/src/main/resources/db/migration/V10__create_backtest_results_table.sql`

## BacktestService API

```java
@Service
public class BacktestService {

    public BacktestResult run(BacktestRequest request) {
        // 1. Fetch OHLCV data for symbol/date range
        // 2. Build BarSeries from data
        // 3. Create strategy with request params
        // 4. Run backtest engine
        // 5. Compute performance metrics
        // 6. Save result to database
        // 7. Return BacktestResult
    }

    public BacktestComparison compare(BacktestCompareRequest request) {
        BacktestResult resultA = run(convertToRequestA(request));
        BacktestResult resultB = run(convertToRequestB(request));
        return buildComparison(resultA, resultB);
    }
}
```

## Acceptance Criteria

- [ ] POST /api/backtest/run executes backtest and returns results
- [ ] POST /api/backtest/compare runs two strategies and shows delta
- [ ] BacktestResult includes all performance metrics
- [ ] Trade list with entry/exit details and exit reasons
- [ ] Equity curve data returned for charting
- [ ] Strategy params are validated (positive values, reasonable ranges)
- [ ] Results persisted to database for history
- [ ] GET /api/backtest/history lists past runs
- [ ] Unit tests for backtest engine integration
- [ ] Integration test with database
- [ ] Code coverage >= 80%

## Notes

- Reuse existing `DefaultBacktestEngine` and `BarSeries` from strategy module
- Exit reasons should be tracked: STOP_LOSS, TARGET_HIT, EMA_CROSS_DOWN, RSI_OVERBOUGHT
- The comparison should highlight which strategy wins on each metric
- Consider adding a Monte Carlo simulation for robustness testing (future)
- Backtest results can be heavy — paginate the history list
