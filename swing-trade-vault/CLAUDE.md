# CLAUDE.md — Staff Security Engineer's Swing Trading Vault

## Who I Am

I'm a Staff Security Software Engineer at Qualcomm in Cork, Ireland. On the side, I'm building an automated swing trading system for Indian equities (NSE/BSE) with a friend who is an experienced swing trader. The system uses a multi-factor technical approach enhanced by LLM sentiment analysis.

## My Vault Structure

```
swing-trade-vault/
├── inbox/              Drop zone — everything new lands here first
├── daily/              Daily brain dumps and quick captures
├── trades/             Live & paper trades with full entry/exit reasoning
├── strategy/           Backtest results, parameter iterations, LLM filter analysis
├── market-research/    Indian market learnings, weekly LLM sentiment digests
├── career/             Work notes at Qualcomm
├── projects/           Side projects with status and next actions
└── archive/            Completed work — never deleted, just moved
```

## How I Work

- **Trade reasoning is critical**: When a signal fires, I need to know why — what the chart looked like, what the news context was, what the LLM sentiment said
- **Track strategy evolution**: Monitor how strategy parameters perform over time and whether my LLM filter is actually improving outcomes
- **Weekly sentiment digests**: Run LLM sentiment analysis on my watchlist weekly and capture the results
- **Indian market learning**: Maintain a running log of what I'm learning about the Indian equity market

## Context Rules

- When I mention a trade → check `trades/` first for entry/exit reasoning
- When discussing strategy → look in `strategy/` for backtest results and parameter history
- When asking about market context → check `market-research/` for recent sentiment digests
- When something lands in `inbox/` → ask if I want it sorted now
- When planning a new backtest → review previous `strategy/` iterations to avoid repeating failed parameters
