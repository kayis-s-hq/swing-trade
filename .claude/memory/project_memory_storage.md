---
name: Project-local memory storage
description: Session and project memories stored in .claude/memory/ co-located with project, not global ~/.claude/projects/ path
type: project
---

## Decision

All session memories and project notes are stored in `.claude/memory/` within the project root, rather than the global `~/.claude/projects/{hash}/memory/` path.

**Why:** Keeps all project context portable with the codebase. Easier to reference, version-control-friendly, and all project knowledge is self-contained.

**How to apply:**

1. At session start, read all `.md` files in `.claude/memory/` for project context (update CLAUDE.md to automate this).

2. When saving session memories:
   - Use `session-wrap` skill (now configured to write to `.claude/memory/`)
   - Name session files: `session-YYYY-MM-DD-{topic}.md`
   - Name memory files: `{type}_{slug}.md`
   - Maintain `MEMORY.md` as the index

3. Session-wrap skill was rewritten to:
   - Derive memory path as `{cwd}/.claude/memory/` instead of `~/.claude/projects/{hash}/memory/`
   - Use flat file structure: `session-YYYY-MM-DD-*.md` (no `sessions/` subfolder)
