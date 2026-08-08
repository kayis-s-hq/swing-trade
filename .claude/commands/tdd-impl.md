---
description: Execute a TDD plan from docs/plans/ step by step with strict RED-GREEN-REFACTOR cycle
---

# TDD Implementation Executor

Execute a TDD plan from `docs/plans/` step by step. Strict RED-GREEN-REFACTOR cycle.

## When to Use

- A TDD plan exists in `docs/plans/` for the current task
- Plan has phases with test names, file paths, and assertions
- Implementing backend changes in a Spring Boot Gradle project

## When NOT to Use

- No plan exists — use `/tdd-plan` first
- Frontend-only changes
- One-line fixes with no test gap

## ABSOLUTE PRINCIPLES

These rules have NO exceptions. Violating any rule means STOP, revert all changes, and restart from the correct phase.

### 1. Tests Before Code

Production code MUST NOT exist before its test fails. Write test → verify RED → write minimal code → verify GREEN. Manual verification does not count. Only `./gradlew :module:test --tests=TestName` passing counts.

### 2. Phases Execute In Order

Phase N MUST complete (GREEN + all tests pass) before touching Phase N+1. No parallelism across phases. No skipping. No "I know what comes next."

### 3. Ambiguity Requires Asking

If ANY requirement is unclear during execution, use `AskUserQuestion` before proceeding. Guessing is a hard violation.

### 4. Code Is Never Deleted

When fixing code: comment out → write test → verify RED → uncomment → apply minimal fix → verify GREEN → remove comment. Deletion is forbidden at every step.

### 5. Phase Status Is Updated After Every Phase

The plan file in `docs/plans/` MUST be updated with `[x] PASS` or `[x] FAIL` and a timestamp after each phase. No update means the phase did not complete. Do not proceed.

### 6. Integration Tests Use Real Implementations

Integration tests use `@SpringBootTest` with real service/repository implementations — never mocks. Unit tests use Mockito. The boundary is strict.

### 7. Subagent Cap Is 2 — ABSOLUTE

Maximum **2 subagents total, per phase, per session**. No exceptions. No "just one more." No spawning then killing. Count every Agent call toward the limit.

**Only when BOTH conditions are met:**
1. Two tasks are truly independent (different files, no shared state)
2. Both tasks are in the same phase (RED, GREEN, or REFACTOR)

**Never** use subagents for:
- The same file (edit conflicts)
- Code that depends on the same dependency
- Test execution — tests ALWAYS run in the main context
- Fixture capture that shares the same backend instance
- Any work that would cause file write conflicts

**Enforcement:** Before every Agent call, count: how many subagents have you spawned this phase? If 2 or more, DO NOT spawn another. Run the work yourself.

### Ambiguity Resolution

**STRICT: Before starting ANY phase, check for ambiguities.**

If any of these are unclear, use `AskUserQuestion` tool:

| Ambiguity | Question |
|-----------|----------|
| Test method names not specified | "Which test method names should I use?" |
| Assertions unclear | "What exact values should I assert?" |
| Fixture data missing | "What real data should I capture?" |
| Config parameters unclear | "Which BacktestConfig values should I use?" |
| File paths ambiguous | "Which exact file and line should I modify?" |
| Dependencies unclear | "Which mocks should I use for this dependency?" |
| Test scope unclear | "Should I test edge cases or just the main behavior?" |

**Do NOT guess.** If you are uncertain about ANY requirement, ask.

### Comment-Out Discipline

When fixing code:
1. Comment out the line to fix (do NOT delete)
2. Write test that fails
3. Uncomment the line
4. Apply minimal fix
5. Verify tests pass
6. Remove comment wrapper

**Never delete code. Always comment out first.**

## Red Flags — HARD STOP

Any of these conditions means STOP immediately. Revert all changes. Return to the correct phase. Restart.

| Violation | What to do |
|-----------|------------|
| Wrote production code before test failed | Comment out the code. Write the test. Verify RED. |
| Skipping a phase because "tests should pass" | Execute the phase. Verify RED. Then GREEN. |
| Guessing assertions or test names | Use AskUserQuestion. Do not proceed without answer. |
| Running `./gradlew test` instead of `./gradlew :module:test --tests=Name` | Run the specific test. Isolate the failure. |
| Deleting code instead of commenting out | Restore from git. Follow comment-out discipline. |
| Using mocks in integration tests | Replace with real implementations. |
| Skipping fixture capture | Run backend locally. Hit endpoints. Save responses. |
| Proceeding to next phase without verification | Run `./gradlew :module:test`. Verify pass. |
| Skipping phase status update | Update the plan file. Do not proceed. |
| Using more than 2 subagents | Kill extras. Continue with max 2. |

## Execution Checklist

- [ ] Read the full plan before starting
- [ ] Check for ambiguities — ask questions if any exist
- [ ] Phase 1: Write test (RED) → verify fail
- [ ] Phase 2: Write code (GREEN) → verify pass
- [ ] Phase 3: Refactor → verify pass
- [ ] Update plan status table after each phase
- [ ] Repeat for each phase
- [ ] Final: `./gradlew test` on entire module
- [ ] Commit after each complete phase

## Arguments

$ARGUMENTS
