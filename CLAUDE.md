# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a swing trading system built with Java 21 and Spring Boot 3.x. It's designed for automated trading of NSE/BSE Indian equities with a multi-factor technical approach enhanced by LLM sentiment analysis.

## Architecture

The system is organized into 6 core modules:
- `core`: Domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult)
- `data`: Data ingestion, storage, and scheduling with PostgreSQL + TimescaleDB
- `strategy`: Technical analysis with TA4J integration and signal generation
- `llm`: LLM client with sentiment analysis pipeline using LangChain4j
- `broker`: Paper trading engine with order management
- `api`: REST endpoints for system interaction

## Key Technologies

- Java 21 with Spring Boot 3.x
- PostgreSQL with TimescaleDB for time-series data
- TA4J for technical analysis
- LangChain4j for LLM integration
- Docker with docker-compose for infrastructure
- Maven multi-module build system

## Development Commands

### Build
```bash
mvn clean install
```

### Run
```bash
# Start database services
docker-compose up -d

# Run the API module
java -jar api/target/api-1.0.0.jar
```

### Testing
```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=DataIngestionServiceTest

# Run a specific test method
mvn test -Dtest=DataIngestionServiceTest#testIngestData
```

## Infrastructure

The system requires PostgreSQL with TimescaleDB extension and Redis for caching. These are managed via docker-compose.yml:
```bash
docker-compose up -d
```

## Database Schema

The database schema is managed with Flyway migrations in `data/src/main/resources/db/migration/V1__swing_trade_schema.sql`. This includes:
- TimescaleDB hypertable for ohlcv_candles
- Tables for stocks, signals, positions, trades, sentiment_results
- Proper indexing for performance

## Documentation Rules

All documentation files must be written in Markdown format and placed in the `docs/` folder.

## Planning Directory

The GSD planning folder is located at `.planning/` (root of the swing-trade repository, NOT in this worktree directory). Always reference planning artifacts (SUMMARY.md, PLAN.md, UAT.md, etc.) from `/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/`. This includes:
- System architecture documentation
- API documentation
- User guides
- Technical specifications
- Development guidelines

Each Markdown file should follow these conventions:
- Use proper heading hierarchy (#, ##, ###)
- Include code blocks with appropriate language syntax highlighting
- Use consistent terminology throughout the documentation
- Link to related documents and code sections where relevant

## Session Memory

At the start of every session, read all `.md` files in `.claude/memory/` for accumulated project context (decisions, trade-offs, session summaries). Start with `MEMORY.md` as the index, then read files listed there.

When saving session memories (via session-wrap), write to `.claude/memory/` in this project directory — NOT to `~/.claude/projects/…/memory/`. Use descriptive filenames:
- Memory files: `{type}_{slug}.md` (e.g., `project_phase9_notification_migration.md`)
- Session summaries: `session-YYYY-MM-DD-{slug}.md` (e.g., `session-2026-03-22-phase9-plan.md`)

### Loading relevant context

Use the `read-project-memory` skill to load only the memory files relevant to your current task, avoiding context bloat:

```bash
# Load memory for a specific topic
read-project-memory

# Or phrase it naturally:
# "what do we know about Phase 9?"
# "did we decide how to handle notifications?"
# "check memory for the data pipeline"
```

The skill reads `.claude/memory/MEMORY.md`, filters for matching files, and reports key context compactly. This keeps sessions focused without reading unnecessary files.

## Custom Skills

This project includes 16 domain-specific skills in `.claude/skills/`. See `.claude/SKILLS.md` for the complete index with trigger conditions.

**Common triggers to invoke skills proactively:**
- **Data quality issues** → `swing-trade-data-quality-audit` (signal drops, ingestion errors, anomalies)
- **Signal analysis** → `swing-trade-signal-suppression-analyzer` (when signals drop unexpectedly)
- **Before auto-trade** → `swing-trade-risk-control-validator` (validate risk parameters)
- **Strategy changes** → `swing-trade-signal-backtest-runner` (backtest new indicators or rules)
- **Performance reports** → `swing-trade-performance-report-generator` (EOD/weekly reviews)
- **Prompt tuning** → `swing-trade-llm-prompt-optimizer` (if sentiment accuracy drops)
- **Build issues** → `swing-trade-maven-multi-module-build` (Maven build or dependency errors)
- **System health** → `swing-trade-health-check-monitor` (pre-trade status checks)
- **Infrastructure** → `swing-trade-docker-compose-deployer` (Docker/deployment changes)
- **Debugging** → `swing-trade-systematic-debugging` (bugs, test failures)

Invoke skills using the `Skill` tool: `Skill("swing-trade-{skill-name}")`

## Worktree & Planning Workflow

This project uses git worktrees for parallel phase development. Each worktree gets its own copy of `.planning/`, but the **root `.planning/` is the source of truth**.

### Planning Document Locations

When working in a worktree (e.g., `.claude/worktrees/phase-05/`):
- **Worktree `.planning/`** (editable): `/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.claude/worktrees/phase-05/.planning/`
- **Root `.planning/` (source of truth)**: `/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/`

### Workflow

1. **Start worktree:** You have a git worktree branch (e.g., `worktree-phase-05`) with its own `.planning/` copy
2. **Edit planning docs:** Update `.planning/` files in the worktree freely (STATE.md, phase plans, verification artifacts)
3. **Sync to root:** Periodically run `Skill("superpowers:gsd-worktree-workflow --sync-to-root")` to copy changes back to root `.planning/`
4. **Verify phase:** Run `gsd:verify` to validate phase goal achievement
5. **Post-verify merge:** Run `Skill("superpowers:gsd-worktree-workflow --post-verify")` for merge guidance
6. **Merge to main:** Merge worktree branch back to main with updated planning docs
7. **Clean up:** Remove worktree via `gsd:remove-workspace` or `git worktree remove .claude/worktrees/phase-X`

### Key Rules

- **Root `.planning/` on main is authoritative** — don't edit it directly from main, wait for worktree merge
- **Worktree `.planning/` is your working copy** — update freely, sync to root before merging
- **Always sync before merging** — run `--sync-to-root` before `git merge` to avoid conflicts
- **Verify before merge** — `gsd:verify` must complete before merging worktree to main
- **Planning conflicts may occur** — resolve manually if root and worktree diverged during parallel work
- **Commit everything before merge** — worktree must have no uncommitted changes

### Worktree Management Skill

Use `Skill("superpowers:gsd-worktree-workflow")` to:
- Detect current branch and show planning locations
- Sync `.planning/` changes from worktree to root (`--sync-to-root`)
- Get merge guidance after `gsd:verify` passes (`--post-verify`)
- Track worktree verification status and sync history (`--show-state`)

See the skill documentation for detailed usage and examples.