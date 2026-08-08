# Sentiment Analysis Redesign — Composite Analysis Pipeline

## Problem

Current sentiment page only shows LLM/keyword-based sentiment from news headlines. It lacks:
- Technical signal context (TA4j indicators)
- Price-based fundamentals (volatility, momentum, volume trends)
- Backtest performance (how well the strategy would have done on this stock)
- A unified composite score that weighs all sources together

The result is a one-dimensional analysis that doesn't reflect the full picture.

## Solution

Build a **composite analysis pipeline** that fetches 3 scored sources in parallel, computes a weighted composite score, and presents backtest results as a standalone validator.

```
                    ┌─────────────────┐
                    │  Manual Trigger  │  POST /api/analysis/analyze?symbol=X
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │  NEWS    │  │ TECHNICAL│  │FUNDAMENT-│
        │ SENTIMENT│  │ SIGNAL   │  │  ALS     │
        └────┬─────┘  └────┬─────┘  └────┬─────┘
           (-100..+100)   (-100..+100)   (-100..+100)
              │              │              │
              └──────────────┼──────────────┘
                             ▼
                    ┌─────────────────┐
                    │  COMPOSITE      │
                    │  SCORE (-100..  │
                    │  +100)          │
                    │                 │
                    │  40% News       │
                    │  40% Technical  │
                    │  20% Fundamentals│
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    ▼                 ▼
            ┌─────────────┐   ┌─────────────┐
            │  BUY > +20  │   │  SELL < -20 │
            │  HOLD else  │   │  HOLD else  │
            └─────────────┘   └─────────────┘

        ┌─────────────────────────────────┐
        │  BACKTEST PERFORMANCE (standalone)│
        │  Win Rate · Profit Factor       │
        │  Max Drawdown · Total Return    │
        └─────────────────────────────────┘
```

## Scoring Thresholds

| Composite | Signal | Color |
|-----------|--------|-------|
| > +20     | BUY    | Green |
| < -20     | SELL   | Red   |
| -20 to +20| HOLD   | Amber |

## Backend Design

### New Files

#### 1. `backend/api/src/main/java/com/swingtrade/api/dto/CompositeAnalysis.java`

```java
package com.swingtrade.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CompositeAnalysis(
    String symbol,
    LocalDate date,

    // Composite
    int compositeScore,        // -100 to +100
    String compositeSignal,    // BUY / SELL / HOLD
    BigDecimal compositeConfidence, // 0.0 to 1.0
    List<SourceScore> sources,

    // News sentiment
    NewsScore news,

    // Technical signal
    TechnicalScore technical,

    // Fundamentals
    FundamentalScore fundamentals,

    // Backtest (standalone, not weighted)
    BacktestScore backtest,

    // Summary reasoning
    String reasoning
) {
    public record SourceScore(String name, int score, double weight, String description) {}
    public record NewsScore(int score, String summary, List<String> catalysts, List<String> redFlags, int articleCount) {}
    public record TechnicalScore(int score, String signal, double confidence, List<String> indicators) {}
    public record FundamentalScore(int score, List<String> factors) {}
    public record BacktestScore(int totalTrades, double winRate, double profitFactor,
                                double maxDrawdown, double totalReturn, double expectancy,
                                boolean hasEnoughData) {}
}
```

#### 2. `backend/api/src/main/java/com/swingtrade/api/service/TechnicalAnalysisService.java`

Computes technical score from TA4j indicators (same indicators as `PriceActionSignalEngine`):

- **Score derivation:**
  - Price > EMA20 > EMA50 → +25
  - RSI in 50-65 range → +25
  - Volume > 1.5x VolumeMA20 → +25
  - Price within 3% of 52-week high → +25
  - Each failed rule subtracts 25
  - Range: -100 (all fail) to +100 (all pass)
- **Signal:** BUY if score > 0, HOLD if score == 0, SELL if score < 0
- **Confidence:** abs(score) / 100

Dependencies: `OhlcvCandleRepository` (reuse same TA4j indicator classes)

#### 3. `backend/api/src/main/java/com/swingtrade/api/service/FundamentalScorer.java`

Calculates price-based fundamental factors (no external financial data needed):

- **Volatility score** (ATR/Price ratio): Low vol = stable = positive
- **Momentum score** (30-day price change): Positive momentum = positive
- **Volume trend** (recent avg volume vs historical avg): Increasing volume = positive
- **Price position** (close vs SMA50): Above SMA50 = positive
- Each factor contributes -25 to +25
- Total: -100 to +100

Dependencies: `OhlcvCandleRepository`

#### 4. `backend/api/src/main/java/com/swingtrade/api/service/BacktestScorer.java`

Wraps `BacktestEngine` for single-symbol backtest:

- Runs `BacktestEngine.runBacktest(symbol, exchange, BacktestConfig.defaults())`
- Extracts: win rate, profit factor, max drawdown, total return, expectancy
- `profitFactor = abs(winning trades' total pnl) / abs(losing trades' total pnl)`
- Returns `BacktestScore` with `hasEnoughData = totalTrades > 0`
- Handles `IllegalStateException` (insufficient candles) gracefully

Dependencies: `BacktestEngine`, `WatchlistService` (for exchange)

#### 5. `backend/api/src/main/java/com/swingtrade/api/service/CompositeAnalysisService.java`

Orchestrates the pipeline:

```java
@Service
public class CompositeAnalysisService {

    private final SentimentService sentimentService;
    private final NewsIngestionService newsService;
    private final TechnicalAnalysisService technicalService;
    private final FundamentalScorer fundamentalScorer;
    private final BacktestScorer backtestScorer;

    public CompositeAnalysis analyze(String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);

        // Parallel fetch
        NewsScore news = fetchNewsScore(sym);
        TechnicalScore technical = technicalService.compute(sym);
        FundamentalScore fundamentals = fundamentalScorer.compute(sym);
        BacktestScore backtest = backtestScorer.compute(sym);

        // Weighted composite
        int composite = round(
            news.score * 0.40 +
            technical.score * 0.40 +
            fundamentals.score * 0.20
        );

        // Signal determination
        String signal = composite > 20 ? "BUY" : composite < -20 ? "SELL" : "HOLD";
        BigDecimal confidence = BigDecimal.valueOf(Math.abs(composite) / 100.0);

        // Reasoning
        String reasoning = buildReasoning(news, technical, fundamentals, backtest, composite);

        return new CompositeAnalysis(sym, LocalDate.now(), composite, signal, confidence,
            List.of(), news, technical, fundamentals, backtest, reasoning);
    }
}
```

#### 6. `backend/api/src/main/java/com/swingtrade/api/controller/AnalysisController.java`

```java
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final CompositeAnalysisService analysisService;

    @PostMapping("/analysis/analyze")
    public ResponseEntity<ApiResponse<CompositeAnalysis>> analyze(
            @RequestParam String symbol) {
        CompositeAnalysis result = analysisService.analyze(symbol);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
```

### Dependencies

No new Maven dependencies. Reuses:
- TA4j (already in `strategy` module)
- BacktestEngine (already in `strategy` module)
- SentimentService + NewsIngestionService (already in `llm` module)

### Module Dependencies

```
api → llm (SentimentService, NewsIngestionService)
api → data (OhlcvCandleRepository, WatchlistService)
api → strategy (BacktestEngine)
```

All already satisfied by existing `api/pom.xml`.

## Frontend Design

### New Files

#### 1. `dashboard/src/api/client.ts` additions

```typescript
export async function getCompositeAnalysis(symbol: string): Promise<ApiResponse<CompositeAnalysis>> {
  const raw = await rawFetch(`/analysis/analyze?symbol=${symbol}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as CompositeAnalysis }
}
```

#### 2. `dashboard/src/api/types.ts` additions

```typescript
export interface CompositeAnalysis {
  symbol: string
  date: string
  compositeScore: number
  compositeSignal: 'BUY' | 'SELL' | 'HOLD'
  compositeConfidence: number
  sources: Array<{ name: string; score: number; weight: number; description: string }>
  news: {
    score: number
    summary: string
    catalysts: string[]
    redFlags: string[]
    articleCount: number
  }
  technical: {
    score: number
    signal: string
    confidence: number
    indicators: string[]
  }
  fundamentals: {
    score: number
    factors: string[]
  }
  backtest: {
    totalTrades: number
    winRate: number
    profitFactor: number
    maxDrawdown: number
    totalReturn: number
    expectancy: number
    hasEnoughData: boolean
  }
  reasoning: string
}
```

#### 3. `dashboard/src/components/ScoreCard.vue`

Large composite score display:
- Big number: composite score (-100 to +100)
- Signal badge: BUY / SELL / HOLD with color
- Confidence meter: 0-100%
- Weight breakdown: News 40%, Technical 40%, Fundamentals 20%

#### 4. `dashboard/src/components/SourceBreakdown.vue`

Horizontal bar chart for each source:
- News Sentiment: bar + score + weight
- Technical Signal: bar + score + weight
- Fundamentals: bar + score + weight
- Color-coded: green for positive, red for negative, amber for neutral

#### 5. `dashboard/src/components/TechnicalIndicators.vue`

Shows technical signal detail:
- Signal badge (BUY/SELL/HOLD)
- Confidence %
- Indicator grid: EMA20, EMA50, RSI, Volume, 52W High
- Each indicator: pass/fail icon + value
- Reasoning text

#### 6. `dashboard/src/components/FundamentalsPanel.vue`

Price-based fundamental factors:
- Score badge
- Factor list: Volatility, Momentum, Volume Trend, Price Position
- Each factor: score + short description

#### 7. `dashboard/src/components/BacktestPanel.vue`

Backtest performance (standalone section):
- Header: "Backtest Performance"
- Stats grid: Total Trades, Win Rate, Profit Factor, Max Drawdown, Total Return, Expectancy
- If `hasEnoughData = false`: "Insufficient history for backtest"
- Color coding: green for good metrics, red for bad

#### 8. Redesigned `dashboard/src/views/SentimentView.vue`

3 tabs: **Overview** / **Details** / **History**

**Overview tab:**
- Symbol selector + Analyze button
- ScoreCard (large composite score)
- SourceBreakdown (weighted scores)
- Reasoning text block
- TechnicalIndicators (compact)
- FundamentalsPanel (compact)
- BacktestPanel (standalone)

**Details tab:**
- Same as Overview but expanded
- Full news summary with catalysts/red flags
- Full indicator details
- Full backtest trade list (collapsible)

**History tab:**
- Existing sentiment history (unchanged)

### Component Hierarchy

```
SentimentView.vue
├── ScoreCard.vue              (composite score)
├── SourceBreakdown.vue        (weighted sources)
├── TechnicalIndicators.vue    (TA4j indicator details)
├── FundamentalsPanel.vue      (price-based fundamentals)
├── BacktestPanel.vue          (backtest performance)
├── SentimentBadge.vue         (existing — reused)
├── SentimentTimeline.vue      (existing — reused)
└── ContextPanel.vue           (removed — replaced by new components)
```

## Implementation Order

### Phase 1 — Backend DTOs + Technical Analysis (2-3 hours)
1. `CompositeAnalysis.java` (DTO record)
2. `TechnicalAnalysisService.java`
3. `FundamentalScorer.java`
4. Unit tests for scoring logic

### Phase 2 — Backtest + Composite Pipeline (2-3 hours)
5. `BacktestScorer.java`
6. `CompositeAnalysisService.java`
7. `AnalysisController.java`
8. Integration test

### Phase 3 — Frontend Types + API (1 hour)
9. TypeScript types in `client.ts` + `types.ts`
10. `getCompositeAnalysis()` API function

### Phase 4 — Frontend Components (3-4 hours)
11. `ScoreCard.vue`
12. `SourceBreakdown.vue`
13. `TechnicalIndicators.vue`
14. `FundamentalsPanel.vue`
15. `BacktestPanel.vue`

### Phase 5 — View Redesign + Polish (2-3 hours)
16. Redesign `SentimentView.vue` (3 tabs)
17. Remove `ContextPanel.vue` (no longer needed)
18. Style pass + responsive layout
19. Manual verification in browser

## Key Decisions

1. **Backtest not weighted** — Backtest measures historical strategy performance, not current sentiment. It acts as a reality check, not a score contributor.

2. **Price-based fundamentals only** — No external financial data (P/E, debt, etc.) available. Uses volatility, momentum, volume trends, price position from existing OHLCV data.

3. **40/40/20 weights** — News and Technical are equally important (40% each). Fundamentals is the tiebreaker (20%). These can be made configurable later via `application.properties`.

4. **Thresholds at ±20** — Not ±50, because with 3 sources each -100 to +100, the composite range is -100 to +100. A ±20 threshold means even slight agreement between sources triggers a BUY/SELL.

5. **Parallel execution** — All 4 sources fetched via `CompletableFuture` in parallel. Total latency = max(source latencies), not sum.

6. **Graceful degradation** — If any source fails (e.g., insufficient candles for backtest), the composite still computes from available sources with adjusted weights.

## Verification

### Backend
```bash
cd backend && mvn clean install
cd backend/api && mvn spring-boot:run -Dspring-boot.run.profiles=local,fyers
# Test:
curl -X POST 'http://localhost:8080/api/analysis/analyze?symbol=RELIANCE'
```

### Frontend
```bash
cd dashboard && yarn dev
# Visit http://localhost:3003/sentiment
# Verify:
# - Overview tab shows composite score + all panels
# - Details tab shows expanded view
# - History tab still works
# - Symbol selector + Analyze button trigger full pipeline
```