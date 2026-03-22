# /tldr — Save Session to Vault

## Purpose

Summarize this conversation and save key information to the right folder.

## What It Does

1. Summarizes current session: decisions, things to remember, next actions
2. Saves summary to `daily/` (or relevant folder based on context)
3. Updates `MEMORY.md` with session log entry

## Usage

Run `/tldr` at the end of any session to:
- Capture decisions made
- Save things to remember
- Record next actions
- Maintain session continuity

## Example Output

```
## Session Summary: 2026-03-22

### Decisions
- Enabled LLM filter for signal validation
- Set backtest period to 90 days

### Things to Remember
- RELIANCE entry at 2,850 with volume spike
- Strategy iteration #5 needs 6-month backtest

### Next Actions
1. Run backtest on iteration #5
2. Log RELIANCE exit when hit
3. Weekly sentiment digest due Friday

Saved to: daily/2026-03-22.md
```
