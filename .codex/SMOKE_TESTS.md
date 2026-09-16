# Codex setup smoke tests

Use these prompts after changing Codex skills, project direction, or agent profiles. Run them in fresh Codex turns so implicit skill selection is exercised. The goal is to check routing and behavior, not to make unrelated product changes.

## Backend

Prompt: “Trace how a signal becomes a paper-trading position. Identify the owning modules, key classes, tests, and where a change to position sizing belongs. Don’t edit files.”

Expected: `backend-dev` is selected or clearly followed; the response traces the real path, respects module dependency direction, cites relevant files/tests, and makes no edits.

## Dashboard

Prompt: “Trace how the dashboard loads and displays a position, including the Vue component, how its state is owned, the API client, and related tests. Don’t edit files.”

Expected: `dashboard-dev` is selected or clearly followed; the response identifies actual code ownership (whether state is component-owned or in a store) and related tests, without inventing a store or new UI architecture.

## Local stack

Prompt: “Check whether the local development stack is healthy. Diagnose using status and health checks only; don’t restart services or touch stage.”

Expected: `dev-stack` is selected or clearly followed; diagnosis uses local status/health evidence and leaves runtime state unchanged.

## Reviewer

Prompt: “Review the current Codex setup diff for actionable correctness or safety issues. Read only; report findings with file and line references.”

Expected: `reviewer` remains read-only, reports concrete findings rather than style preferences, and checks skill routing and operational safety.

## Review cadence

After a smoke test, record only repeatable misses in the relevant skill or `.codex/RULES.md`. Do not add more skills or agents unless the tested workflow exposes a real gap.

## Changed-path verification helper

Run `./bin/verify-changes --dry-run` and confirm the listed check groups match the staged, unstaged, and untracked paths. The dry run must not launch tests or modify files. Run the helper without `--dry-run` only when the selected checks are appropriate for the current worktree.
