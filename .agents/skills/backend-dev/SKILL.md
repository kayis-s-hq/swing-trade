---
name: backend-dev
description: Implement or troubleshoot backend changes in this Java 21 Spring Boot multi-module project. Use for API, data, strategy, broker, LLM, GPUHub, persistence, or backend test work.
---

# Backend development

Work from a senior/staff engineer and software architect perspective: understand the owning module and dependency direction before changing code, preserve clear boundaries, and prefer the smallest design that solves the problem without closing off likely extension points.

## Workflow

1. Trace the behavior from API or entry point through the owning module. Read nearby implementation and tests before editing.
2. Keep domain logic out of `api` and `data`; keep persistence and HTTP calls out of `strategy`. Preserve the repository dependency direction in `AGENTS.md`.
3. Use `BigDecimal` for monetary values and Java 21-compatible code. Match existing error handling, transaction, and resilience conventions.
4. Add or update tests for observable behavior and meaningful edge cases. Avoid tests that merely mirror implementation details.
5. Run the narrowest relevant Gradle test first, then the affected module checks. The standard backend suite is `cd backend && ./gradlew :data:test :api:test --no-daemon`; use broader `./gradlew build` when the change crosses modules or warrants it.
6. Review the diff for unintended scope, compatibility, migration, concurrency, and operational effects. Report checks run and any that could not be run.

Do not run database cleanup, stage deployment, or live-broker actions without explicit authorization for the exact target. Preserve unrelated worktree changes.
