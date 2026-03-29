# SwingTrade Skills Summary

This document provides an overview of all custom skills created for the swing-trade system.

## Skills Created

### Category 1: Data Quality & Pipeline (3 skills)

| Skill Name | Purpose | Trigger |
|------------|---------|---------|
| `swing-trade-data-quality-audit` | Run automated data quality checks on TimescaleDB | Data ingestion issues, signal failures, before trading |
| `swing-trade-data-pipeline-diagnostic` | Diagnose data pipeline failures with detailed reporting | Pipeline errors, signal drops, quality issues |
| `swing-trade-timescaledb-migration` | Create TimescaleDB hypertable migrations | New time-series tables, schema changes |

### Category 2: Trading & Strategy (4 skills)

| Skill Name | Purpose | Trigger |
|------------|---------|---------|
| `swing-trade-signal-backtest-runner` | Execute automated backtests on trading signals | Strategy changes, new indicators, auto-trade testing |
| `swing-trade-watchlist-screener` | Automated stock screening based on criteria | Watchlist expansion, quarterly rebalancing |
| `swing-trade-auto-trade-executor` | Auto-execute high-confidence signals with risk validation | Auto-trade implementation, signal execution |
| `swing-trade-position-rebalancer` | Automated position rebalancing with partial exits | Position management, target hits, trailing stops |

### Category 3: Risk & Performance (3 skills)

| Skill Name | Purpose | Trigger |
|------------|---------|---------|
| `swing-trade-risk-control-validator` | Validate risk control parameters and circuit breakers | Risk parameter changes, auto-trade deployment |
| `swing-trade-performance-report-generator` | Generate daily/weekly performance reports | EOD, weekly review, portfolio decisions |
| `swing-trade-signal-suppression-analyzer` | Analyze why signals are suppressed by sentiment | Signal count drops, sentiment tuning |

### Category 4: LLM & Debugging (2 skills)

| Skill Name | Purpose | Trigger |
|------------|---------|---------|
| `swing-trade-llm-prompt-optimizer` | Test and optimize LLM prompts for sentiment | Sentiment accuracy drops, prompt changes |
| `swing-trade-systematic-debugging` | Systematic debugging with persistent state | Bugs, test failures, unexpected behavior |

### Category 5: Infrastructure & Operations (5 skills)

| Skill Name | Purpose | Trigger |
|------------|---------|---------|
| `swing-trade-health-check-monitor` | Monitor system health via Actuator endpoints | Pre-trade, continuous monitoring, incidents |
| `swing-trade-docker-compose-deployer` | Deploy with Docker Compose including health checks | Infrastructure changes, new environments |
| `swing-trade-maven-multi-module-build` | Build and test specific modules with dependency resolution | Module changes, targeted tests, deployment |
| `swing-trade-e2e-smoke-test` | Run lightweight E2E smoke tests on the running API | Phase completion, post-merge validation, gsd:verify-work integration |
| `swing-trade-telegram-signal-migrator` | Migrate from Telegram to Signal/Signl4 webhook | Notification changes, Telegram issues |

## Installation

All skills are located in:
```
/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.claude/skills/
```

Each skill has its own directory with a `SKILL.md` file containing the full documentation.

## Usage

Skills can be invoked using the `Skill` tool in Claude Code:

```bash
# Example: Run data quality audit
Skill("swing-trade-data-quality-audit")

# Example: Generate performance report
Skill("swing-trade-performance-report-generator")

# Example: Validate risk controls
Skill("swing-trade-risk-control-validator")
```

## Integration with Existing Skills

These custom skills complement the existing superpowers skills:
- `superpowers:test-driven-development` - Testing workflow
- `superpowers:systematic-debugging` - Debugging (replaced by custom version)
- `superpowers:writing-plans` - Planning
- `gsd:*` - Project management

## Future Enhancements

Potential skills to add:
- `swing-trade-incident-response` - Incident response runbooks
- `swing-trade-backup-recovery` - Database backup and recovery
- `swing-trade-api-documentation` - Auto-generate API docs
- `swing-trade-architecture-diagram` - Update architecture diagrams
