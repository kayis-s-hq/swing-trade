---
description: Generate a test-first development plan for backend changes with fixtures, Mockito tests, and H2 integration tests
---

# TDD Plan Generator

Generate a test-first development plan. Captures real API request/response as fixtures, writes unit tests with Mockito, integration tests with H2, and the minimal code to make them pass.

## When to Use

- Backend bug fix, precision issue, or new feature in a Spring Boot Gradle module
- Existing tests use Mockito (mock repos/services) or SpringBootTest + H2
- Need to capture real API request/response as JSON fixtures
- Plan goes under `docs/plans/` with exact file paths and code

## When NOT to Use

- Frontend-only changes
- Pure infrastructure/config changes
- One-line fixes with no test gap
- Database migration-only work
- Plan already exists in `docs/plans/` — use `/tdd-impl` to execute it

## ABSOLUTE PRINCIPLES

These rules have NO exceptions.

### 1. Tests Before Code

Production code MUST NOT exist before its test fails. This is the only rule. Everything else derives from it.

### 2. Plans Are Actionable Artifacts

Every plan must be executable by another agent without guessing. Exact file paths. Exact method names. Exact assertions. If a requirement is unclear, ask before writing the plan.

### 3. Fixtures Are Real Data

No synthetic fixtures. Run the backend locally. Hit the endpoints. Save real responses to `src/test/resources/fixtures/`.

## Two Outputs

### Plan Document (`docs/plans/<feature>.md`)

Every plan MUST have these sections:

### 0. Phase Status (REQUIRED)

```markdown
## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: NumPrecisionTest | [ ] PENDING | — | — |
| 2: Missing Features | [ ] PENDING | — | — |
```

**Note:** Stale/completed plans MUST be moved to `docs/plans/archive/`.

### 1. Feature Map
Table mapping every behavior/feature to test coverage:
- Feature name
- Tested? (Yes/No with count)
- Test type (Unit/Integration)

### 2. Phase Breakdown
Each phase is a step in the RED-GREEN-REFACTOR cycle:

**Phase N: [Phase Name] (TDD — will fail)**
- What to test
- File path
- Test method names
- What it asserts
- Why it will fail currently

**Phase N: Switch to [Fix]**
- File paths
- Exact lines to change
- What changes from/to

### 3. Files Summary
| Action | File | Type |
|--------|------|------|
| Create | path/to/NewTest.java | Unit |
| Expand | path/to/ExistingTest.java (3 new tests) | Unit |
| Modify | path/to/Source.java (2 lines) | Source |
| Create | path/to/fixture.json | Fixture |

### 4. Verification
Commands to run:
```bash
./gradlew :module:test --tests=TestName    # fails (RED)
# → apply fix
./gradlew :module:test                     # all pass (GREEN)
```

## Test Design Rules

### Unit Tests (Mockito)

- `@ExtendWith(MockitoExtension.class)` on test class — REQUIRED
- `@Mock` for all dependencies (repos, services, stores) — REQUIRED
- `@BeforeEach` to wire mocked dependencies into the class under test — REQUIRED
- Assert on both behavior (method calls) and state (return values, fields) — REQUIRED
- Use `assertThat` from AssertJ, NOT `assertEquals` — REQUIRED
- Group related tests in `@Nested` classes with `@DisplayName` — REQUIRED

### Integration Tests (SpringBootTest + H2)

- `@SpringBootTest` with `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` if using real schema — REQUIRED
- Or `@SpringBootTest` with H2 auto-config if schema is compatible — REQUIRED
- Load real OHLCV/data from CSV fixtures into H2 — REQUIRED
- Wire real implementations (NOT mocks) — REQUIRED
- Assert on full pipeline output (JSON reports, CSV files) — REQUIRED

### Fixtures

- Run backend locally, hit endpoints with real data — REQUIRED
- Save responses to `src/test/resources/fixtures/` — REQUIRED
- Name: `fixture-name.json` (single symbol) / `fixture-multi.json` (multi-symbol)
- Integration tests load fixtures and assert exact values match — REQUIRED

## Ambiguity Resolution

Before writing the plan, if ANY of these are unclear, use `AskUserQuestion`:

| Ambiguity | Question |
|-----------|----------|
| Test method names not specified | "Which test method names should I use?" |
| Assertions unclear | "What exact values should I assert?" |
| Fixture data missing | "What real data should I capture?" |
| Config parameters unclear | "Which BacktestConfig values should I use?" |
| File paths ambiguous | "Which exact file and line should I modify?" |
| Dependencies unclear | "Which mocks should I use for this dependency?" |
| Test scope unclear | "Should I test edge cases or just the main behavior?" |

**Do NOT guess.** If uncertain about ANY requirement, ask before writing the plan.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Only testing happy path | Test all exit reasons, edge cases, null inputs |
| Asserting only on trades list | Assert on aggregate metrics (Sharpe, drawdown, expectancy) |
| Missing config parameter tests | Test slippage, brokerage, maxHoldingDays explicitly |
| No precision comparison | Compare DecimalNum vs DoubleNum behavior |
| Fixture file too large | Split into single/multi fixtures, remove noise |
| Integration test uses mocks | Use real implementations, not mocks |

## Real-World Impact

A thorough TDD plan for a backtest precision fix caught:
- 5 untested features (slippage, brokerage, forced close, metrics, multi-symbol)
- Precision loss from DoubleNum → DecimalNum conversion
- 23 call sites using `doubleValue()` instead of `toBigDecimal()`
- Total: 64 findings across architecture audit, 15 high priority

## Arguments

$ARGUMENTS
