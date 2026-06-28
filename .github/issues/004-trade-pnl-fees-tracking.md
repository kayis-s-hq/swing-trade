# feat(broker): add trade P&L and fees tracking

**Labels:** `enhancement` `tier-1-backend` `broker`
**Estimated effort:** 1-2 days

## Problem

The `trades` table has a `fees` column that defaults to 0, but it is never populated. The `PaperTradeEngine` and `KiteConnectClient` do not calculate or track trading costs (brokerage, STT, stamp duty, exchange charges, GST). This makes P&L calculations inaccurate.

## Proposed Solution

Create a `FeeCalculator` service that computes all trading costs based on broker mode (paper vs live) and populate the `fees` column on every trade.

## Fee Structure (Indian NSE/BSE)

### Paper Trading Mode (estimates)
| Fee Type | Rate |
|----------|------|
| Brokerage | 0.01% or Rs 20 per order (whichever is higher) |
| STT (equity buy) | 0.1% |
| STT (equity sell) | 0.1% |
| Exchange transaction charge | 0.00325% |
| GST | 18% on (brokerage + exchange charge + SEBI fee) |
| SEBI turnover fee | Rs 0.0001% of turnover |
| Stamp duty (buy) | 0.015% (equity) |

### Live Trading (Zerodha Kite)
| Fee Type | Rate |
|----------|------|
| Brokerage | Rs 20 per order or 0.05% (whichever is lower) |
| STT | 0.1% (sell side only for equity) |
| Other charges | Same as above |

## Files to Create/Modify

### New files
- `broker/src/main/java/com/swingtrade/broker/util/FeeCalculator.java` - Fee computation utility
- `broker/src/main/java/com/swingtrade/broker/dto/FeeBreakdown.java` - Fee detail DTO

### Modified files
- `broker/src/main/java/com/swingtrade/broker/engine/PaperTradeEngine.java` - Call `FeeCalculator` when creating trades
- `broker/src/main/java/com/swingtrade/broker/engine/PaperTradingEngine.java` - Same
- `broker/src/main/java/com/swingtrade/broker/kite/KiteConnectClient.java` - Call `FeeCalculator` for live orders
- `data/src/main/java/com/swingtrade/data/entity/Trade.java` - Ensure `fees` field is properly mapped

## FeeCalculator API

```java
public class FeeCalculator {
    public static FeeBreakdown calculate(BigDecimal tradeValue, OrderType type, BrokerMode mode) {
        // Returns breakdown of all fees
    }
}

public class FeeBreakdown {
    private BigDecimal brokerage;
    private BigDecimal stt;
    private BigDecimal exchangeCharge;
    private BigDecimal gst;
    private BigDecimal sebiFee;
    private BigDecimal stampDuty;
    private BigDecimal total;
}
```

## Acceptance Criteria

- [ ] `FeeCalculator` computes all fee components correctly
- [ ] Paper trading uses estimated rates
- [ ] Live trading uses Zerodha Kite rates
- [ ] Every trade created in `PaperTradeEngine` has `fees` populated
- [ ] Every trade created in `KiteConnectClient` has `fees` populated
- [ ] `FeeBreakdown` is stored in trade metadata for audit
- [ ] Unit tests for fee calculation edge cases (small trades, large trades)
- [ ] Code coverage >= 80%

## Notes

- Fees should be deducted from P&L calculations
- Consider adding a `realizedPnl` and `unrealizedPnl` distinction
- For short positions, STT applies on the sell side (entry)
- Round fees to 2 decimal places
