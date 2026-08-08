# feat(api): add portfolio analytics endpoint

**Labels:** `enhancement` `tier-1-backend` `api` `analytics`
**Estimated effort:** 2-3 days

## Problem

The `TradingController` has a `/performance` endpoint but it only returns basic P&L. There is no comprehensive portfolio analytics endpoint that computes industry-standard metrics like Sharpe ratio, max drawdown, profit factor, and win rate.

## Proposed Solution

Create a `PortfolioAnalyticsService` that computes all portfolio metrics from the `trades` table and exposes them via a REST endpoint.

## API Endpoint

```
GET /api/portfolio/metrics?range=30d
    Returns: PortfolioMetricsResponse with all computed metrics
```

Query params:
- `range` (optional): `7d`, `30d`, `90d`, `180d`, `365d`, `all` — default `30d`

## Response DTO

```java
public class PortfolioMetricsResponse {
    private BigDecimal totalValue;
    private BigDecimal totalPnl;
    private BigDecimal totalPnlPercent;
    private BigDecimal winRate;           // percentage 0-100
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal averageWin;
    private BigDecimal averageLoss;
    private BigDecimal profitFactor;      // grossProfit / grossLoss
    private BigDecimal maxDrawdown;       // peak-to-trough decline %
    private BigDecimal sharpeRatio;       // annualized
    private BigDecimal avgTradeDuration;  // in days
    private List<MonthlyReturn> monthlyReturns;
}

public class MonthlyReturn {
    private String month;    // "2026-01"
    private BigDecimal pnl;
    private BigDecimal returnPercent;
    private int trades;
}
```

## Files to Create/Modify

### New files
- `api/src/main/java/com/swingtrade/api/dto/PortfolioMetricsResponse.java` - Response DTO
- `api/src/main/java/com/swingtrade/api/dto/MonthlyReturn.java` - Monthly return sub-DTO
- `api/src/main/java/com/swingtrade/api/service/PortfolioAnalyticsService.java` - Metrics computation
- `api/src/main/java/com/swingtrade/api/controller/PortfolioAnalyticsController.java` - REST controller

### Modified files
- `api/src/main/java/com/swingtrade/api/controller/TradingController.java` - Add import/autowire

## Computation Logic

| Metric | Formula |
|--------|---------|
| `totalPnl` | Sum of all trade (exit_price - entry_price) * quantity |
| `winRate` | winningTrades / totalTrades * 100 |
| `profitFactor` | Sum of winning PnL / Sum of absolute losing PnL |
| `maxDrawdown` | Max peak-to-trough decline in cumulative PnL |
| `sharpeRatio` | (mean_daily_return - risk_free_rate) / std_daily_return * sqrt(252) |
| `averageWin` | Mean of all positive PnL trades |
| `averageLoss` | Mean of all negative PnL trades |

## Caching

- Cache results in Redis with key `portfolio:metrics:{range}`
- TTL: 5 minutes (metrics don't change intra-day)
- Invalidate on position close

## Acceptance Criteria

- [ ] `PortfolioAnalyticsService` computes all metrics correctly
- [ ] Monthly breakdown returns last 12 months
- [ ] Redis caching implemented with proper TTL
- [ ] `range` param filters trades by timestamp
- [ ] Unit tests with known trade data (use `DomainObjectFactory`)
- [ ] Integration test with real database
- [ ] Code coverage >= 80%

## Notes

- Use `BigDecimal` for all monetary calculations
- Risk-free rate: assume 7% (Indian 10Y G-Sec annualized)
- Daily returns computed from daily portfolio value snapshots
- Handle edge case: zero trades (return zeros, not nulls)
