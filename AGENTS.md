# Swing Trade agent guide

Keep this file short and shared by Codex and Claude Code. Preserve unrelated worktree changes and use `apply_patch` for source and documentation edits.

For Codex-specific architecture, verification, safety, and skill routing, read [`.codex/RULES.md`](.codex/RULES.md). Use the focused skills in `.agents/skills/` when their descriptions match the task; don’t repeat their workflows here. Run `./bin/verify-changes` to select checks from changed paths.

Project documentation belongs in `docs/`. `README.md` is short onboarding, `CLAUDE.md` is Claude-specific routing, and active plans belong in `docs/plans/` (archive completed historical plans). Update `docs/status.md` only after performing a verification.
