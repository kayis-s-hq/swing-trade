---
description: Generate a test-first development plan for backend changes with fixtures, Mockito tests, and H2 integration tests
---

# TDD Plan Generator Command

Generate a test-first development plan for backend changes. Captures real API request/response as fixtures, writes unit tests with Mockito, integration tests with H2, and the minimal code to make them pass.

## When to Use

- Backend bug fix, precision issue, or new feature in a Spring Boot Maven module
- Existing tests use Mockito (mock repos/services) or SpringBootTest + H2
- Need to capture real API request/response as JSON fixtures
- Plan must go under `docs/plan/` with exact file paths and code

## Core Principle

Write tests first (RED), watch them fail, write minimal code (GREEN), verify all pass. The plan documents the full cycle: what to test, how to test it, what fixtures to capture, which files change.

## Plan Structure

Every plan MUST have these sections:

### 0. Phase Status (REQUIRED)

```markdown
## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: NumPrecisionTest | [ ] PENDING | — | — |
| 2: Missing Features | [ ] PENDING | — | — |
```

### 1. Feature Map
Table mapping every behavior/feature to test coverage:
- Feature name
- Tested? (Yes/No with count)
- Test type (Unit/Integration)

### 2. Phase Breakdown
Each phase is a step in the RED-GREEN-REFACTOR cycle:

**Phase N: [Phase Name] (TDD — will fail)**
- What to test, file path, test method names, what it asserts, why it will fail currently

**Phase N: Switch to [Fix]**
- File paths, exact lines to change, what changes from/to

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

## TDD Discipline

**NO CODE BEFORE TESTS PASS.** The sequence is always:
1. Comment out the code that needs fixing (do NOT delete)
2. Write test that fails (RED)
3. Uncomment code
4. Apply minimal fix (GREEN)
5. Verify all existing tests still pass (no regression)
6. Remove the comment wrapper once tests pass

## Red Flags - STOP and Start Over

- Code before test
- "I already manually tested it"
- "Tests after achieve the same purpose"
- "This is different because..."
- Skipping fixture capture because "it's complicated"
- Deleting code instead of commenting it out

**All of these mean: Comment out the code. Start over with TDD.**

## Arguments

$ARGUMENTS
