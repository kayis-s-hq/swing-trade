# /trade — Log a New Trade

## Purpose

Log a new trade with full reasoning — capture everything the database doesn't store.

## What It Does

1. Prompts for trade details: symbol, entry/exit price, position size
2. Captures reasoning: signal type, chart context, news, LLM sentiment
3. Creates structured note in `trades/` folder
4. Links to strategy iteration if applicable

## Usage

Run `/trade` when:
- A signal fires and you enter a position
- You exit a position (realize P&L)
- You want to document why a signal was taken or avoided

## Required Fields

- **Symbol**: NSE/BSE stock symbol (e.g., RELIANCE, TCS)
- **Type**: entry or exit
- **Price**: Entry or exit price
- **Quantity**: Number of shares
- **Reasoning**: Why this trade? What did the chart show? What did the LLM say?

## Example Output

```
## Trade #123: RELIANCE Entry

**Date**: 2026-03-22
**Type**: Entry
**Price**: 2,850
**Quantity**: 100
**Position Size**: 285,000 INR

### Signal Reasoning
- **Technical**: 50-day MA cross above 200-day MA, RSI at 58
- **Volume**: 1.5x average volume on breakout
- **LLM Sentiment**: Positive (0.72) — earnings beat expectations
- **News**: Q4 results announced this morning, revenue up 12%

### Strategy Iteration
- Using parameters from iteration #5 (LLM filter enabled)

### Exit Plan
- Stop loss: 2,720 (-4.5%)
- Target: 3,000 (+5.3%)
- Time horizon: 2-4 weeks
```
