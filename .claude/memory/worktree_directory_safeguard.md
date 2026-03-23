---
name: worktree_directory_safeguard
description: Lesson learned about always cd-ing into worktree directory before working
type: feedback
---

**Rule:** Always `cd` into the worktree directory after creating it, never work from the root repository while on a worktree branch.

**Why:** During Phase 04, I created the worktree and checked out the `worktree-phase-04` branch but never changed directory into `.claude/worktrees/phase-04/`. This caused all changes to be made to the main branch instead of the isolated worktree, leading to merge conflicts and extra manual resolution.

**How to apply:**
1. After `git worktree add .claude/worktrees/phase-XX -b worktree-phase-XX`, immediately run `cd .claude/worktrees/phase-XX`
2. All work should be done from within the worktree directory
3. When done, return to root: `cd /path/to/repo`, then `git checkout main`, then `git merge worktree-phase-XX`
4. The `superpowers:gsd-worktree-workflow` skill now includes directory verification and will warn if you're on a worktree branch but not in the worktree directory

**Impact:** This prevents confusion about which branch changes are going to, avoids merge conflicts, and ensures the isolated worktree workflow works as intended.
