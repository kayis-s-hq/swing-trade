# Repository Guidelines

Contributor guide for the **Swing Trade System** — an automated swing-trading platform for NSE/BSE Indian equities (1–4 week holds on Nifty 500 stocks using technical analysis, LLM sentiment, and paper trading). Follow the shared agent guide in [`AGENTS.md`](../AGENTS.md) first.

## Project Structure & Module Organization

```
swing-trade/
├── backend/      # Multi-module Spring Boot (Gradle Kotlin) app
│   ├── core/     # Domain models: Stock, OhlcvCandle, Signal, Position, Trade
│   ├── data/     # Ingestion/storage (Upstox/Fyers/Yahoo clients, JPA, Flyway)
│   ├── strategy/ # TA via TA4j, signal generation, backtesting
│   ├── llm/      # LLM client (vLLM/OpenAI), sentiment, news
│   ├── broker/   # Paper trading engine, order/position management
│   ├── gpuhub/   # GPUHub elastic deployment API client
│   └── api/      # REST endpoints, scheduled jobs
├── dashboard/    # Vue 3 + TypeScript frontend (Vite, Pinia, vue-router)
├── infra/        # Docker, env files, monitoring, nginx
└── docs/         # Project documentation (plans live in docs/plans/)
```

Modules are declared in `backend/settings.gradle.kts`; dependencies flow `api → strategy → data → core`.

## Build, Test, and Development Commands

- `./bin/verify-changes` — selects and runs checks for changed paths (backend vs. dashboard).
- Backend: `cd backend && ./gradlew test --no-daemon` runs the suite; `./gradlew :api:bootRun --spring.profiles.active=local` runs the API.
- Dashboard: `cd dashboard && yarn test:run` runs Vitest; `yarn build` runs format-check, typecheck, lint, and Vite build.

## Coding Style & Naming Conventions

- Backend: Java on the JVM, 4-space indentation, `camelCase` code and `SCREAMING_CASE` constants. Match surrounding style; no added license/copyright headers.
- Dashboard: Prettier (single quotes, no semicolons) and ESLint via `@typescript-eslint`. Run `yarn format:write`; enforce with `yarn format:check` and `yarn typecheck`.
- Docs: Markdown is linted via `.markdownlint.yaml`.

## Testing Guidelines

- Backend uses Gradle's test task (JUnit); dashboard uses Vitest for unit tests and Playwright for E2E.
- Name tests by behavior (`*Test`, `*Tests`) and cover the unit under change first, then broaden.

## Commit & Pull Request Guidelines

- Use Conventional Commits: `type(scope): short imperative subject`, e.g. `feat(api): evaluate configured strategies live`. Scopes mirror modules (`api`, `data`, `strategy`, `llm`, `broker`, `core`, `dashboard`).
- Keep subjects imperative (~50 chars max); reference issues in the body or PR description.
- Keep PRs focused on one area; describe the change, link the issue, and note verification performed.

## Agent-Specific Instructions

- Read `.codex/RULES.md` for Codex-specific architecture, verification, and safety guidance; use matching skills in `.agents/skills/`.
- Preserve unrelated worktree changes and edit via `apply_patch`. Do not overwrite `AGENTS.md`, `README.md`, or `CLAUDE.md`.
- Never flip the global Docker context in scripts (guard in `bin/verify-changes`); use command-scoped `docker --context`.
