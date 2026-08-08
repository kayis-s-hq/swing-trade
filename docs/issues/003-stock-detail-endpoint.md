# feat(api): add stock detail and chart data endpoints

**Labels:** `enhancement` `tier-1-backend` `api`
**Estimated effort:** 2-3 days

## Problem

There is no single endpoint to get comprehensive information about a stock. Users must call multiple endpoints (signals, positions, performance) to build a stock detail view. Also no chart data endpoint for the frontend candlestick chart.

## Proposed Solution

Create a `StockDetailController` with two endpoints: one for stock overview and one for OHLCV chart data.

## API Endpoints

```
GET /api/stocks/{symbol}
    Returns: StockDetailResponse with overview data

GET /api/stocks/{symbol}/chart?period=30d&interval=daily
    Returns: List<OhlcvCandleResponse> for charting
```

Query params for `/chart`:
- `period`: `7d`, `30d`, `90d`, `180d`, `365d`, `1y`, `all` — default `30d`
- `interval`: `daily`, `weekly`, `monthly` — default `daily`

## Response DTOs

```java
public class StockDetailResponse {
    private StockInfo stock;
    private LatestCandle latestCandle;
    private List<SignalResponse> recentSignals; // last 10
    private PositionResponse openPosition;
    private PerformanceSummary performance;
}

public class StockInfo {
    private String symbol;
    private String name;
    private String sector;
    private String industry;
    private BigDecimal peRatio;
    private Long marketCap;
}

public class LatestCandle {
    private LocalDate date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
}

public class PerformanceSummary {
    private BigDecimal totalReturn;
    private int signalCount;
    private BigDecimal avgConfidence;
}
```

## Files to Create/Modify

### New files
- `api/src/main/java/com/swingtrade/api/controller/StockDetailController.java` - REST controller
- `api/src/main/java/com/swingtrade/api/service/StockDetailService.java` - Aggregation service
- `api/src/main/java/com/swingtrade/api/dto/StockDetailResponse.java` - Response DTO
- `api/src/main/java/com/swingtrade/api/dto/StockInfo.java`
- `api/src/main/java/com/swingtrade/api/dto/LatestCandle.java`
- `api/src/main/java/com/swingtrade/api/dto/PerformanceSummary.java`

### Modified files
- Existing repositories: `StockRepository`, `SignalRepository`, `PositionRepository`, `OhlcvCandleRepository`
  - May need to add `findBySymbolOrderByDateDesc()` methods

## Implementation Details

### Stock detail aggregation
1. Fetch `StockEntity` by symbol (return 404 if not found)
2. Fetch latest candle from `OhlcvCandleRepository` (ORDER BY date DESC LIMIT 1)
3. Fetch last 10 signals from `SignalRepository` (ORDER BY signal_timestamp DESC)
4. Check for open position via `PositionRepository` (status = 'OPEN')
5. Compute performance summary from trades

### Chart data
1. Calculate date range from `period` param
2. Query `OhlcvCandleRepository` for candles in range
3. If interval = `weekly`, aggregate daily candles to weekly (OHLC + sum volume)
4. If interval = `monthly`, aggregate to monthly
5. Return sorted list

## Acceptance Criteria

- [ ] GET /api/stocks/{symbol} returns 404 for unknown symbols
- [ ] StockDetailResponse includes all fields (stock info, latest candle, signals, position)
- [ ] GET /api/stocks/{symbol}/chart returns candles for the requested period
- [ ] Weekly/monthly aggregation is correct (open = first, close = last, high = max, low = min)
- [ ] Candle data sorted ascending by date (for chart libraries)
- [ ] Unit tests for aggregation logic
- [ ] Integration test with database
- [ ] Code coverage >= 80%

## Notes

- Chart data should be returned in ascending date order (chart libraries expect this)
- Consider adding volume as a secondary chart series
- The `StockInfo` should reuse the existing `Stock` domain model where possible
