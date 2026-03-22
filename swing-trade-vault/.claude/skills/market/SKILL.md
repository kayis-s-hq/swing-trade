# /market — Capture Market Research

## Purpose

Capture market research or LLM sentiment digest — weekly watchlist analysis and Indian market learnings.

## What It Does

1. Prompts for research type: weekly sentiment digest or market learning
2. Captures LLM sentiment analysis for watchlist stocks
3. Records Indian market insights and observations
4. Creates structured note in `market-research/` folder

## Usage

Run `/market` when:
- Running weekly LLM sentiment analysis on watchlist
- You've learned something new about the Indian market
- You want to document market context for future trade decisions

## Required Fields

- **Type**: sentiment-digest or market-learning
- **Date**: Analysis date
- **Watchlist**: List of stocks analyzed (for sentiment digest)
- **Sentiment Scores**: LLM sentiment for each stock
- **Key Insights**: What did you learn? What patterns emerged?

## Example Output — Sentiment Digest

```
## Weekly Sentiment Digest: 2026-03-22

**Watchlist**: 12 stocks

| Symbol   | Sentiment | Score | Key Driver                    |
|----------|-----------|-------|-------------------------------|
| RELIANCE | Positive  | 0.72  | Q4 earnings beat              |
| TCS      | Neutral   | 0.51  | No major news                 |
| HDFCBANK | Positive  | 0.68  | RBI policy favorable          |
| INFY     | Negative  | 0.35  | US tech slowdown concerns     |

### Top 3 Positives
1. RELIANCE — earnings momentum
2. HDFCBANK — policy tailwinds
3. ICICIBANK — retail credit growth

### Top 3 Negatives
1. INFY — global tech headwinds
2. WIPRO — margin pressure
3. TATAMOTORS — EV transition concerns

## Example Output — Market Learning

```
## Market Learning: NSE Sector Rotation

**Date**: 2026-03-22

### Observation
Banking sector showing relative strength while IT underperforms. This pattern emerged after RBI's recent policy announcement.

### Hypothesis
Rate cut expectations benefit banks (lower funding costs) while IT suffers from global growth concerns.

### Action
- Add HDFCBANK, ICICIBANK to watchlist for potential entries
- Reduce IT exposure in backtest parameters
- Monitor if pattern persists through earnings season
```
