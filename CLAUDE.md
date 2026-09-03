# Claude Code repository instructions

Read [`AGENTS.md`](AGENTS.md) first. It is the shared guide for all coding agents and is the source of truth for repository structure, commands, safety rules, and documentation policy.

## Claude Code routing

Use the repository's specialized agents when the task matches their domain:

| Task | Agent |
|---|---|
| Backend Java, Gradle, persistence, APIs | `backend-dev` |
| Vue, TypeScript, Pinia, dashboard | `frontend-dev` |
| Test-first implementation | `test-writer` |
| Architecture and module boundaries | `arch-auditor` |
| Flyway migrations | `migration-reviewer` |
| Deployment validation | `deploy-validator` |
| Health and smoke checks | `health-check` |
| Diff, bug, security, or simplification review | `code-reviewer` |

For a cross-cutting change, route each independent area to the relevant agent. Shell exploration, documentation-only edits, Git operations, and questions about these instructions can be handled directly.

## Claude skills and commands

- `dev-stack` is the canonical skill for local and stage stack operations.
- `context` provides version-specific library documentation when the Context MCP server is available.
- `crit`, `crit-cli`, and `crit-story` are opt-in review tools; use them only when explicitly requested.
- `tdd-*` commands are opt-in workflows for test-first backend work.

Plans belong in `docs/plans/`, not `.claude/plans/`. Keep Claude-specific state such as resume checkpoints, local settings, and locks out of shared project documentation.
