---
name: tdd-impl
description: Use when executing a TDD plan from docs/plan/ — implement backend changes test-first with Mockito unit tests and SpringBootTest integration tests
---

# TDD Implementation

Execute a TDD plan from `docs/plan/` step by step. Strict RED-GREEN-REFACTOR cycle.

## When to Use

Symptoms:
- A TDD plan exists in `docs/plan/` for the current task
- Plan has phases with test names, file paths, and assertions
- Implementing backend changes in a Spring Boot Maven project

## When NOT to Use

- No plan exists (use tdd-plan skill first)
- Frontend-only changes
- One-line fixes with no test gap

## Core Principle

Write tests first (RED), watch them fail, write minimal code (GREEN), verify all pass. Never skip phases. Never write production code before its test fails.

## Execution Rules

### Phase Execution

Execute phases IN ORDER. Do NOT skip ahead.

1. **RED** — Write the test. Run it. Verify it FAILS.
2. **GREEN** — Write minimal production code. Run all tests. Verify they PASS.
3. **REFACTOR** — Clean up while keeping all tests green.

After each phase completes, verify with `mvn test`. Do NOT proceed to next phase until current phase passes.

### Status Tracking

After EACH phase completes (pass or fail), update the plan file in `docs/plan/`:

- Mark the phase status: `[ ] Phase N: Name (pending)` → `[x] Phase N: Name (PASS)` or `[x] Phase N: Name (FAIL)`
- Add a timestamp and result summary
- If FAIL, note what failed and why
- Do NOT proceed until the current phase is marked

**Format:** Edit the plan file directly. Add a "Phase Status" section at the top if it doesn't exist:

```markdown
## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: NumPrecisionTest | [x] PASS | All 4 tests pass | 2026-08-07 14:32 IST |
| 2: Missing Features | [ ] PENDING | — | — |
```

### Subagent Usage

Max **2 subagents** for independent work. Use only when:
- Two phases can run in parallel (e.g., writing two independent test files)
- Fixture capture can run while you write tests

**Never** use subagents for:
- The same phase
- Code that depends on the same file
- Test execution (run tests yourself)

**How to use:**
```
Agent(description="write NumPrecisionTest", prompt="...")
Agent(description="write BacktestEngineTest", prompt="...")
```
Both run in parallel. Wait for both to complete before running verification.

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

**Format:** Use `AskUserQuestion` with `multiSelect: false`. First option MUST be your recommended choice. Wait for answer before proceeding.

**Do NOT guess.** If you are uncertain about ANY requirement, ask. Guessing leads to wrong tests and wasted time.

### Comment-Out Discipline

When fixing code:
1. Comment out the line to fix (do NOT delete)
2. Write test that fails
3. Uncomment the line
4. Apply minimal fix
5. Verify tests pass
6. Remove comment wrapper

**Never delete code. Always comment out first.**

## Red Flags - STOP

- Skipping a phase because "tests should pass"
- Writing production code before test fails
- Guessing assertions instead of asking
- Running all tests instead of just the relevant test
- Proceeding to next phase without verification
- Skipping status update in plan file after phase completes
- Using more than 2 subagents

**All of these mean: Stop. Go back to the correct phase.**

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Writing test and production code in same step | Write test first, verify fail, THEN write code |
| Running `mvn test` instead of `mvn test -Dtest=Name` | Run only the relevant test class |
| Skipping fixture capture | Capture real API responses before integration tests |
| Using mocks in integration tests | Use real implementations in integration tests |
| Asserting on behavior not in plan | Stick to plan assertions only |

## Execution Checklist

- [ ] Read the full plan before starting
- [ ] Check for ambiguities — ask questions if any exist
- [ ] Phase 1: Write test (RED) → verify fail
- [ ] Phase 2: Write code (GREEN) → verify pass
- [ ] Phase 3: Refactor → verify pass
- [ ] Update plan status table after each phase
- [ ] Repeat for each phase
- [ ] Final: `mvn test` on entire module
- [ ] Commit after each complete phase
