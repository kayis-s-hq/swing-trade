# Dev Stack Skill & CLI Design

## Goal

Create a Claude Code skill and a thin CLI wrapper for the SwingTrade dev stack, so Claude knows how to manage the dev environment and users get enhanced UX over the raw `dev-stack.sh` script.

## Current State

- `dev-stack.sh` — Bash script with `start|stop|infra|status|logs|*` commands
- Manages Docker infra on `piworm.local` via `pi-node` context
- Runs Spring Boot locally with `local,fyers` profiles
- No Claude Code skill exists for this project
- No CLI wrapper

## Design

### Part 1: CLI Wrapper (`bin/swingdev`)

Thin wrapper around `dev-stack.sh` with:

**Commands:**
- `start [--profile name] [--dashboard]` — validates `.env`, starts infra, auto-waits with progress until healthy, optionally launches Vue dev server
- `stop` — stops infra + local Spring Boot + dashboard
- `status` — formatted table: infra health, local app health, API health
- `health` — checks DB, Redis, API health from `/actuator/health` endpoint
- `env-check` — validates `.env` has all required variables
- `reset` — stop + clean volumes + start (full reset)
- `logs [--tail N] [--follow]` — wraps existing, adds tail/follow

**UX additions:**
- `printf` colors (green=ok, yellow=warning, red=fail)
- Auto-wait with progress bar (poll health endpoint until healthy)
- Arg validation + `--help`
- Zero dependencies

**File location:** `bin/swingdev`

### Part 2: Claude Code Skill (`.claude/skills/dev-stack/SKILL.md`)

Documents:
- When to use dev stack vs standalone commands
- Quick reference table of all CLI commands
- Common workflows (start dev, check infra, full reset, switch profile)
- Troubleshooting (pi-node unreachable, infra unhealthy, env missing)
- How to pick the right command for each situation

**File location:** `.claude/skills/dev-stack/SKILL.md`

### Part 3: Project Integration

- Add `bin/swingdev` to `.gitignore` exclusions (it's tracked)
- Add skill reference to project `CLAUDE.md`
- Make `bin/swingdev` executable

## Decisions

- **Bash wrapper, not Node** — keeps zero-dependency, follows existing pattern
- **Thin wrapper** — delegates to `dev-stack.sh`, adds health checks and UX on top
- **Skill format** — follows `device-screenshot` SKILL.md pattern
