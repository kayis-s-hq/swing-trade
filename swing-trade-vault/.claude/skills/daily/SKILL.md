# /daily — Start Your Day with Vault Context

## Purpose

Start your day with full vault context, check inbox for unprocessed files, and surface top priorities.

## What It Does

1. Reads today's daily note (or creates one if it doesn't exist)
2. Checks `inbox/` for unprocessed files
3. Surfaces top 3 priorities from recent activity
4. Asks: "What are we working on today?"

## Usage

Run `/daily` in the morning to:
- Review yesterday's trades and strategy updates
- Process anything in your inbox
- Set daily priorities based on trading system status

## Example Output

```
## Today: 2026-03-22

### Recent Activity
- Trade #123: RELIANCE entry logged
- Strategy iteration #5: LLM filter enabled
- Weekly sentiment digest: 12 stocks analyzed

### Inbox (2 items)
- README.md — needs sorting
- notes.txt — needs processing

### Top 3 Priorities
1. Review RELIANCE trade exit criteria
2. Run backtest on strategy iteration #5
3. Prepare weekly sentiment digest

What are we working on today?
```
