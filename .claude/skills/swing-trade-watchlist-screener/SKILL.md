---
name: swing-trade-watchlist-screener
description: Automated stock screening based on liquidity, volume, and market cap criteria for the swing-trade system. Use this skill when expanding the watchlist, adding new stocks to the trading universe, optimizing the current watchlist, or before quarterly rebalancing. This skill is essential for discovering new trading opportunities and should be triggered proactively when the current watchlist underperforms or when seeking to diversify beyond current holdings.
---

# SwingTrade Watchlist Screener Skill

## Overview

This skill automates the screening and evaluation of stocks for inclusion in the swing-trade watchlist. It applies liquidity, volume, and market cap criteria to identify suitable candidates for swing trading.

## When to Use This Skill

Trigger this skill when:
- Expanding the watchlist beyond current holdings
- Quarterly watchlist rebalancing
- When current watchlist underperforms
- Before adding new sectors to the universe
- After market regime changes
- When seeking diversification opportunities

## Screening Criteria

### Liquidity Criteria

```java
public class LiquidityCriteria {
    // Minimum average daily volume
    private static final double MIN_AVG_VOLUME = 500000; // ₹500K

    // Minimum number of trades per day
    private static final int MIN_TRADES_PER_DAY = 1000;

    // Maximum bid-ask spread
    private static final double MAX_BID_ASPREAD_PCT = 0.1;

    public boolean passesLiquidityCriteria(Stock stock) {
        return stock.avgDailyVolume >= MIN_AVG_VOLUME
            && stock.tradesPerDay >= MIN_TRADES_PER_DAY
            && stock.bidAskSpread <= MAX_BID_ASPREAD_PCT;
    }
}
```

### Market Cap Criteria

```java
public class MarketCapCriteria {
    // Minimum market cap (large-cap only)
    private static final double MIN_MARKET_CAP = 10_000_000_000; // ₹10K Cr

    // Maximum market cap (exclude mega-caps for volatility)
    private static final double MAX_MARKET_CAP = 500_000_000_000; // ₹500K Cr

    public boolean passesMarketCapCriteria(Stock stock) {
        return stock.marketCap >= MIN_MARKET_CAP
            && stock.marketCap <= MAX_MARKET_CAP;
    }
}
```

### Price Volatility Criteria

```java
public class VolatilityCriteria {
    // Minimum ATR for swing trading opportunities
    private static final double MIN_ATR_PCT = 1.5;

    // Maximum ATR (too volatile)
    private static final double MAX_ATR_PCT = 5.0;

    // Beta range (not too correlated to market)
    private static final double MIN_BETA = 0.7;
    private static final double MAX_BETA = 1.5;

    public boolean passesVolatilityCriteria(Stock stock) {
        return stock.atrPercent >= MIN_ATR_PCT
            && stock.atrPercent <= MAX_ATR_PCT
            && stock.beta >= MIN_BETA
            && stock.beta <= MAX_BETA;
    }
}
```

### Sector Diversification Criteria

```java
public class SectorDiversification {
    // Maximum positions per sector
    private static final int MAX_POSITIONS_PER_SECTOR = 3;

    // Minimum sector representation
    private static final int MIN_SECTORS = 5;

    public ValidationResult validateSectorDiversification(List<Stock> watchlist) {
        Map<String, Long> sectorCounts = watchlist.stream()
            .collect(Collectors.groupingBy(Stock::getSector, Collectors.counting()));

        boolean passes = sectorCounts.values().stream()
            .allMatch(count -> count <= MAX_POSITIONS_PER_SECTOR);

        return ValidationResult.builder()
            .passes(passes && sectorCounts.size() >= MIN_SECTORS)
            .sectorBreakdown(sectorCounts)
            .build();
    }
}
```

## Screening Workflow

```
1. Fetch universe of eligible stocks
   ↓
2. Apply liquidity filters
   ↓
3. Apply market cap filters
   ↓
4. Apply volatility filters
   ↓
5. Check sector diversification
   ↓
6. Score remaining candidates
   ↓
7. Generate ranked watchlist
   ↓
8. Output recommendations
```

## Stock Scoring System

```java
public class StockScore {
    private double liquidityScore;    // 0-30 points
    private double volatilityScore;   // 0-25 points
    private double momentumScore;     // 0-25 points
    private double sectorScore;       // 0-20 points

    public double calculateScore(Stock stock) {
        liquidityScore = calculateLiquidityScore(stock);
        volatilityScore = calculateVolatilityScore(stock);
        momentumScore = calculateMomentumScore(stock);
        sectorScore = calculateSectorScore(stock);

        return liquidityScore + volatilityScore + momentumScore + sectorScore;
    }
}
```

### Scoring Breakdown

| Factor | Weight | Criteria |
|--------|--------|----------|
| Liquidity | 30% | Volume, trades, spread |
| Volatility | 25% | ATR, beta, price range |
| Momentum | 25% | Price trend, relative strength |
| Sector | 20% | Sector performance, diversification |

## Watchlist Report Format

```
# Watchlist Screening Report - [Timestamp]

## Screening Parameters
- Liquidity Filter: [criteria]
- Market Cap Filter: [criteria]
- Volatility Filter: [criteria]
- Sector Diversification: [criteria]

## Results Summary
- Stocks Screened: [X]
- Passed All Filters: [X]
- Top Candidates: [X]

## Top 10 Candidates

| Rank | Symbol | Sector | Score | Liquidity | Volatility | Momentum |
|------|--------|--------|-------|-----------|------------|----------|
| 1 | HDFCBANK | Banking | 85 | 90 | 80 | 85 |
| 2 | INFY | IT | 82 | 85 | 78 | 82 |

## Sector Distribution
| Sector | Count | Top Stock |
|--------|-------|-----------|
| Banking | 3 | HDFCBANK |
| IT | 2 | INFY |

## Watchlist Changes
### Additions
- [X] - Score: [X]
- [Y] - Score: [X]

### Removals (if applicable)
- [Z] - Reason: [low liquidity / poor momentum]

## Recommendations
1. Add top 5 candidates to watchlist
2. Monitor candidates 6-10 for next review
3. Remove bottom performers from current watchlist
```

## Example Usage

```bash
# Screen with default criteria
/skill: swing-trade-watchlist-screener

# Screen with custom criteria
/skill: swing-trade-watchlist-screener --min-volume 1000000 --min-market-cap 20000000000

# Add top candidates to watchlist
/skill: swing-trade-watchlist-screener --add-top-10

# Generate full report
/skill: swing-trade-watchlist-screener --report --detailed

# Screen specific sector
/skill: swing-trade-watchlist-screener --sector Banking --top-5
```

## Integration with Data Ingestion

The screener can:
1. **Auto-add stocks** to the data ingestion pipeline
2. **Trigger initial data fetch** for new candidates
3. **Set up monitoring** for price alerts
4. **Create watchlist entries** in the database

## Dependencies

- NSE/BSE market data API
- Stock universe database
- Liquidity metrics (volume, trades, spread)
- Sector classification data

## Performance Considerations

- Screening should complete within 2 minutes
- Use cached market data where possible
- Parallelize screening across stock universe
- Consider incremental screening for efficiency
