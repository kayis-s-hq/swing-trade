---
name: tdd-plan
description: Use when implementing any backend feature, bug fix, or precision fix that requires test-first development with Mockito unit tests and SpringBootTest integration tests with H2 in-memory DB
---

# TDD Plan Generator

Generate a test-first development plan for backend changes. Captures real API request/response as fixtures, writes unit tests with Mockito, integration tests with H2, and the minimal code to make them pass.

## When to Use

Symptoms that signal this skill applies:
- Backend bug fix, precision issue, or new feature in a Spring Boot Maven module
- Existing tests use Mockito (mock repos/services) or SpringBootTest + H2
- Need to capture real API request/response as JSON fixtures
- Plan must go under `docs/plan/` with exact file paths and code

## When NOT to Use

- Frontend-only changes (use frontend testing skills)
- Pure infrastructure/config changes
- One-line fixes with no test gap
- Database migration-only work

## Core Principle

Write tests first (RED), watch them fail, write minimal code (GREEN), verify all pass. The plan documents the full cycle: what to test, how to test it, what fixtures to capture, which files change.

## Plan Structure

Every plan MUST have these sections:

### 0. Phase Status (REQUIRED — updated by tdd-impl skill)

```markdown
## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: NumPrecisionTest | [ ] PENDING | — | — |
| 2: Missing Features | [ ] PENDING | — | — |
```

Mark `[x] PASS` or `[x] FAIL` after each phase verification. Do NOT proceed until current phase is marked.

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
mvn test -pl module -Dtest=TestName    # fails (RED)
# → apply fix
mvn test -pl module                    # all pass (GREEN)
```

## Test Design Rules

### Unit Tests (Mockito)
- `@ExtendWith(MockitoExtension.class)` on test class
- `@Mock` for dependencies (repos, services, stores)
- `@BeforeEach` to wire mocked dependencies into the class under test
- Assert on both behavior (method calls) and state (return values, fields)
- Use `assertThat` from AssertJ, not `assertEquals`
- Group related tests in `@Nested` classes with `@DisplayName`

### Integration Tests (SpringBootTest + H2)
- `@SpringBootTest` with `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` if using real schema
- Or `@SpringBootTest` with H2 auto-config if schema is compatible
- Load real OHLCV/data from CSV fixtures into H2
- Wire real implementations (not mocks)
- Assert on full pipeline output (JSON reports, CSV files)

### Fixtures
- Run backend locally, hit endpoints with real data
- Save responses to `src/test/resources/fixtures/`
- Name: `fixture-name.json` (single symbol) / `fixture-multi.json` (multi-symbol)
- Integration tests load fixtures and assert exact values match

## TDD Discipline

**NO CODE BEFORE TESTS PASS.** The sequence is always:
1. Comment out the code that needs fixing (do NOT delete)
2. Write test that fails (RED)
3. Uncomment code
4. Apply minimal fix (GREEN)
5. Verify all existing tests still pass (no regression)
6. Remove the comment wrapper once tests pass

**Violating the letter of the rules is violating the spirit of the rules.**

## Red Flags - STOP and Start Over

- Code before test
- "I already manually tested it"
- "Tests after achieve the same purpose"
- "This is different because..."
- Skipping fixture capture because "it's complicated"
- Deleting code instead of commenting it out

**All of these mean: Comment out the code. Start over with TDD.**

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
