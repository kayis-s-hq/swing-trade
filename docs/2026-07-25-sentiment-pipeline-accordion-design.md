# Sentiment Pipeline Accordion Design

**Date:** 2026-07-25
**Status:** Approved
**Scope:** Frontend — `dashboard/src/views/SentimentView.vue` and `dashboard/src/components/AnalysisProgress.vue`

## Goal

Redesign the Sentiment page's pipeline progress display so each stage is expandable with structured summary cards and a formatted details view. Merge the old separate LLM analysis block into the LLM sentiment pipeline stage. Keep the old LLM block collapsed at the bottom for historical data.

## Current State

- `AnalysisProgress.vue` renders a flat list of pipeline stages with icon + name + short message
- Stages are not interactive — no expansion, no detail view
- The LLM analysis result (`SentimentResult`) renders as a separate block below the composite analysis in SentimentView.vue (lines 128-153)
- No toggle between summary and raw data per stage

## Target State

### 1. AnalysisProgress — Accordion Rows

Each stage row becomes an accordion trigger. Clicking opens a detail panel below the row.

**Row structure:**
```
[icon] Stage Name          [expand chevron ▼]
       Short message
```

**Detail panel (on expand):**
- Two tabs: **Summary** | **Details**
- **Summary tab:** structured cards with human-readable layout, color-coded
- **Details tab:** formatted key-value display (not raw JSON), scrollable, with copy button

**State:**
- `expandedStage: number | null` — which stage is open
- `detailView: 'summary' | 'details'` — tab selection (per stage)

### 2. Per-Stage Summary Cards

| Stage | Summary Cards |
|-------|---------------|
| pipeline-start | Symbol, timestamp |
| checking data | Candle count, date range |
| backfilling OHLCV | Before → After (e.g., "739 → 739 candles") |
| fetching news | Article count, source names |
| LLM sentiment | Score badge (POSITIVE/NEUTRAL/NEGATIVE), confidence %, summary text, red flags, catalysts |
| technical analysis | Signal badge (BUY/SELL/HOLD), score, confidence %, indicator list |
| composite score | Composite score, signal badge, confidence %, reasoning text |
| backtest | Trades count, win rate %, total return %, profit factor, max drawdown |
| complete | Total duration badge |

### 3. LLM Analysis Merge

- **LLM sentiment stage (Stage 5)** now carries full `SentimentResult` data: score, confidence, summary, red flags, catalysts
- The old separate LLM block (SentimentView lines 128-153) is **kept but collapsed** — moved below BacktestPanel with a "View Historical Sentiment" header
- When no pipeline has run, the old block is the primary display

### 4. SentimentView Layout Changes

**New Overview tab order:**
1. Symbol selector + Analyze button
2. AnalysisProgress (now accordion)
3. ScoreCard
4. SourceBreakdown
5. NewsSentimentPanel
6. TechnicalIndicators
7. FundamentalsPanel
8. BacktestPanel
9. Historical Sentiment (old LLM block, collapsed by default)

**Changes:**
- Remove old LLM block from its current position (between composite results and ContextPanel)
- Move it to the bottom of the Overview tab, below BacktestPanel
- Add a "View Historical Sentiment" collapsible header

### 5. Styling

- Detail panels use `card-panel` class with `mt-2` spacing
- Summary cards use same visual language as ScoreCard (rounded pills, color-coded badges)
- Chevron rotates 180° on expand
- Smooth height transition via CSS
- Mobile: identical behavior, no collapse-on-tap

### 6. Data Flow

- `AnalysisProgress` events carry `stageNumber`, `stageName`, `status`, `message`
- `FullAnalysisResult` carries full `CompositeAnalysis` object
- Frontend maps stage numbers to structured data from:
  - `message` field for simple stats (parse "Found 739 candles")
  - `CompositeAnalysis` fields for the LLM stage
  - Backend can later add a `details` field to `AnalysisProgress` for richer per-stage data

## Files Changed

1. `dashboard/src/components/AnalysisProgress.vue` — accordion behavior, detail panel, summary/detail tabs
2. `dashboard/src/views/SentimentView.vue` — move old LLM block, remove inline LLM block

## Decisions

- **Manual click only** — no auto-expand on stage completion
- **Formatted details** — human-readable key-value display, not raw JSON, with copy button
- **All stages expandable** — including pipeline-start and complete
- **Old LLM block kept but collapsed** — moved to bottom of Overview tab