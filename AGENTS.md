# Swing Trade agent guide

This is the shared repository guidance for coding agents, including Codex and Claude Code. Keep it current; do not duplicate project architecture or command lists in agent-specific files.

## Repository map

- `backend/` — Java 21, Spring Boot, multi-module Gradle project.
  - `core` — domain models and ports
  - `data` — persistence and market-data clients
  - `strategy` — technical indicators, signals, and backtests
  - `llm` — news and sentiment integrations
  - `broker` — paper-trading engine and risk controls
  - `gpuhub` — GPUHub client
  - `api` — REST API and orchestration
- `dashboard/` — Vue 3, TypeScript, Vite, Pinia, Vitest, and Playwright.
- `infra/` — Docker, environment templates, monitoring, and nginx.
- `docs/` — product, architecture, API, operations, and historical planning documentation.
- `dev-stack.sh` and `bin/swingdev` — local development-stack entry points.

The dependency direction is `api → strategy, llm, broker, gpuhub, data, core`; `broker → strategy, data, core`; `strategy → data, llm, core`; `llm → data, core`; `data → core`; `gpuhub` and `core` are standalone. Do not introduce reverse dependencies.

## Working rules

- Preserve unrelated user changes in a dirty worktree.
- Use `apply_patch` for source and documentation edits.
- Never commit credentials, tokens, `.env` files, generated logs, build output, or runtime data.
- Use `infra/env/.env.example` as the template. The local `.env` is sourced by the stack scripts and is not auto-loaded by Spring Boot.
- Treat database cleanup, stage deployment, and live-broker actions as destructive. Confirm the exact target before acting.
- Use `BigDecimal` for monetary values and Java 21 for Gradle builds.
- Keep domain logic out of `data` and `api`; keep persistence and HTTP calls out of `strategy`.

## Verification commands

```bash
# Backend
cd backend
./gradlew :data:test :api:test --no-daemon
./gradlew build

# Dashboard
cd dashboard
yarn typecheck
yarn test:run
yarn build

# Local stack
./dev-stack.sh status
curl -sS http://localhost:8080/actuator/health
```

For a focused change, run the narrowest relevant test first, then the module suite. Integration tests may require Docker and a test database; do not confuse them with the local dev database.

## Local stack

```bash
./dev-stack.sh start       # infrastructure + API + dashboard
./dev-stack.sh status
./dev-stack.sh logs --tail=100
./dev-stack.sh stop
```

The development database is PostgreSQL on pi-node at `192.168.0.100:5435`, database `swingtrade_db`. Stage is separate: API `:8081`, database `swingtrade_stage`, and its own Docker stack. Use `./dev-stack.sh stage*` only when explicitly working on stage.

For detailed operational commands, read `.claude/skills/dev-stack/SKILL.md` or run `./dev-stack.sh --help`.

## Documentation policy

- `README.md` is the short onboarding document.
- `AGENTS.md` is shared agent guidance.
- `CLAUDE.md` contains only Claude Code-specific routing and workflow notes.
- `docs/` is the canonical home for project documentation.
- `docs/plans/` contains active or recently completed implementation plans; move historical plans to `docs/plans/archive/`.
- Do not create a second copy of a plan under `.claude/plans/`.
- Update `docs/status.md` only when a verification has actually been performed.
