---
description: TDD brainstorming — discover test strategy, scope phases, resolve ambiguities, then check for pending TDD plans
---

# TDD Brainstorm

Discover test strategy, scope RED-GREEN phases, resolve ambiguities, then check for pending TDD plans.

## When to Use

- Starting a new backend feature/bugfix in a Spring Boot Gradle module
- Need to decide test strategy before `/tdd-plan` generates the full plan
- Want to know what pending TDD plans are waiting for execution

## When NOT to Use

- A TDD plan already exists in `docs/plans/` — use `/tdd-plan` or `/tdd-impl`
- Frontend-only changes
- Config-only or one-line fixes

## ABSOLUTE PRINCIPLES

These rules have NO exceptions.

### 1. Auto-Detect First

Scan the codebase before asking anything. Detect:
- Existing test patterns (MockitoExtension, AssertJ, @Nested, @DisplayName)
- Module structure and dependencies (from `backend/settings.gradle.kts`)
- Existing fixtures in `src/test/resources/fixtures/`
- Coverage thresholds (from root `build.gradle.kts`)
- Test naming conventions in the target module
- Existing test file locations relative to source

Only ask what cannot be auto-detected.

### 2. Batch Questions

Ask ALL unknowns in a single `AskUserQuestion` call. Never ask one at a time. Use multi-select options. Max 5 questions.

### 3. Ambiguity Requires Asking

If ANY requirement is unclear during execution, use `AskUserQuestion` before proceeding. Guessing is a hard violation.

## Step 1: Auto-Detect

Scan the target module. Report what you found:

```
Detected patterns:
- Tests use @ExtendWith(MockitoExtension.class) + @Mock + @Nested
- AssertJ assertThat (not assertEquals)
- Fixture dir: src/test/resources/fixtures/
- Coverage: 80% line threshold
- 12 existing test files in module
```

If nothing is found, note it and proceed.

## Step 2: Batch Questions

Ask only what's genuinely unknown. Max 5 questions in a single `AskUserQuestion`.

### Question Template

| # | Question | Options |
|---|----------|---------|
| 1 | What type of change? | New feature / Bug fix / Precision fix / Refactoring |
| 2 | Target module | Auto-detected list of modules, or user specifies |
| 3 | Test depth | Surface (happy + obvious edges) / Deep (all exit paths, precision) / Exhaustive (what code should do) |
| 4 | Fixture strategy | Capture real API responses / Use existing fixtures / No fixtures needed |
| 5 | Phase granularity | Coarse (2-3 phases, ship fast) / Fine (many small phases, maximum proof) |

Only include questions where the answer is unknown. Skip questions you already know.

## Step 3: Output TDD Brief

Produce a 1-page TDD brief:

```markdown
## TDD Brief

**Entry:** [first RED phase target]
**Scope:** [what's in, what's out]

### Phase Map
| Phase | RED Target | Mock/Real Boundary |
|-------|-----------|-------------------|
| 1 | [behavior to test] | [mocks vs real] |
| 2 | [behavior to test] | [mocks vs real] |

### Fixture List
| Fixture | Source | Used In |
|---------|--------|---------|
| [name] | [endpoint/data] | [phase N] |

### Mock Map
| Dependency | Mock or Real | Rationale |
|-----------|-------------|-----------|
| [repo/service] | Mock/Real | [why] |
```

## Step 4: Tail End — Pending Plans Check

After the TDD brief, check for pending TDD plans:

```bash
# Find all plans with Phase Status tables
grep -rl "## Phase Status" docs/plans/ --exclude-dir=archive

# Extract phase status for each
for f in docs/plans/*.md; do
  grep -A 10 "## Phase Status" "$f" | head -15
done
```

Report:

```markdown
## Pending TDD Plans

| Plan | Phases | Status |
|------|--------|--------|
| plan-name.md | 4 phases | [x] 2 done / [ ] 2 pending |
| other-plan.md | 7 phases | [ ] 7 pending |

No pending plans — all clear.
```

If there are pending plans, ask:

> "You have [N] pending TDD plan(s). Execute one before starting new work?"

Let the user decide: execute pending, skip, or start new brainstorm.

## Step 5: Handoff

After the brief is approved:

> "Brief ready. Two paths:
> 1. `/tdd-plan` — generate full plan with exact file paths, test names, assertions
> 2. `/tdd-impl` — execute an existing plan
>
> Which?"

## Arguments

$ARGUMENTS